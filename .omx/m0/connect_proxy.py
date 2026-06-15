import datetime as _dt
import select
import socket
import socketserver
import sys
import threading
from urllib.parse import urlsplit


LOG_PATH = sys.argv[1] if len(sys.argv) > 1 else "proxy-hosts.log"
LISTEN_HOST = sys.argv[2] if len(sys.argv) > 2 else "0.0.0.0"
LISTEN_PORT = int(sys.argv[3]) if len(sys.argv) > 3 else 8888

_lock = threading.Lock()


def _redact_path(path: str) -> str:
    if not path:
        return "/"
    split = urlsplit(path)
    clean = split.path or "/"
    if split.query:
        clean += "?<query-redacted>"
    return clean


def _log(kind: str, value: str) -> None:
    line = f"{_dt.datetime.now().isoformat(timespec='seconds')}\t{kind}\t{value}\n"
    with _lock:
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line)


def _recv_until(sock: socket.socket, marker: bytes, limit: int = 65536) -> bytes:
    data = bytearray()
    while marker not in data and len(data) < limit:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data.extend(chunk)
    return bytes(data)


def _relay(left: socket.socket, right: socket.socket) -> None:
    sockets = [left, right]
    while True:
        readable, _, errored = select.select(sockets, [], sockets, 60)
        if errored or not readable:
            break
        for src in readable:
            dst = right if src is left else left
            data = src.recv(16384)
            if not data:
                return
            dst.sendall(data)


class ProxyHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        self.request.settimeout(15)
        first = _recv_until(self.request, b"\r\n\r\n")
        if not first:
            return
        header_text = first.decode("iso-8859-1", errors="replace")
        lines = header_text.split("\r\n")
        parts = lines[0].split()
        if len(parts) < 3:
            return

        method, target, _version = parts[0].upper(), parts[1], parts[2]
        if method == "CONNECT":
            host, port_s = target.rsplit(":", 1) if ":" in target else (target, "443")
            port = int(port_s)
            _log("CONNECT", f"{host}:{port}")
            upstream = socket.create_connection((host, port), timeout=15)
            try:
                self.request.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
                _relay(self.request, upstream)
            finally:
                upstream.close()
            return

        host = ""
        for line in lines[1:]:
            if line.lower().startswith("host:"):
                host = line.split(":", 1)[1].strip()
                break
        parsed = urlsplit(target)
        upstream_host = parsed.hostname or host.split(":")[0]
        upstream_port = parsed.port or (443 if parsed.scheme == "https" else 80)
        path = _redact_path(target if not parsed.scheme else parsed.path + (("?" + parsed.query) if parsed.query else ""))
        _log("HTTP", f"{method} {upstream_host}:{upstream_port}{path}")

        upstream = socket.create_connection((upstream_host, upstream_port), timeout=15)
        try:
            upstream.sendall(first)
            _relay(self.request, upstream)
        finally:
            upstream.close()


class ThreadingTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    _log("START", f"{LISTEN_HOST}:{LISTEN_PORT}")
    with ThreadingTCPServer((LISTEN_HOST, LISTEN_PORT), ProxyHandler) as server:
        server.serve_forever()
