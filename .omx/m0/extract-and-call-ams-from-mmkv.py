import json
import pathlib
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request


APPID = "wx1c36464bbea2507a"
ENDPOINT = (
    "https://comm.ams.game.qq.com/ide/"
    "?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2"
)
MMKV_PATH = pathlib.Path(".omx/m0/private/AppBrandMMKVStorage4093052208.bin")
REMOTE_MMKV_PATH = "/data/data/com.tencent.mm/files/mmkv/AppBrandMMKVStorage4093052208"
OUT_PATH = pathlib.Path(".omx/m0/ams-crafting-status-authenticated.redacted.json")
SUMMARY_PATH = pathlib.Path(".omx/m0/ams-crafting-status-authenticated-summary.md")


def extract_strings(data: bytes) -> list[str]:
    return [
        match.group().decode("utf-8", "ignore")
        for match in re.finditer(rb"[\x20-\x7e]{3,}", data)
    ]


def find_cookie_object(strings: list[str]) -> dict[str, str]:
    candidates = []
    key = f"{APPID}__cookie"
    for index, text in enumerate(strings):
        if key not in text:
            continue

        combined = "".join(strings[index : index + 4])
        match = re.search(r"Object#\d+#(\{.*?\})(?:[\w$'\")]|$)", combined)
        if not match:
            continue

        raw_json = match.group(1)
        try:
            cookie = json.loads(raw_json)
        except json.JSONDecodeError:
            continue
        if isinstance(cookie, dict):
            candidates.append(cookie)

    if not candidates:
        raise RuntimeError(f"Could not find {key} Object value in MMKV strings.")

    # Prefer the candidate with the most AMS credential fields.
    def score(cookie: dict[str, str]) -> int:
        wanted = {
            "acctype",
            "openid",
            "appid",
            "access_token",
            "ieg_ams_session_token",
            "ieg_ams_token",
            "ieg_ams_token_time",
            "ieg_ams_token_v2",
            "unionid",
        }
        return len(wanted.intersection(cookie.keys()))

    return sorted(candidates, key=score, reverse=True)[0]


def build_cookie_header(cookie: dict[str, str]) -> str:
    order = [
        "openid",
        "acctype",
        "appid",
        "access_token",
        "ieg_ams_session_token",
        "ieg_ams_token",
        "ieg_ams_token_time",
        "ieg_ams_token_v2",
        "unionid",
        "verifysession",
    ]
    parts = []
    for key in order:
        value = cookie.get(key)
        if value is None or value == "":
            continue
        parts.append(f"{key}={value}")
    if not parts:
        raise RuntimeError("Extracted cookie object had no usable values.")
    return "; ".join(parts)


def load_mmkv_bytes() -> bytes:
    if MMKV_PATH.exists() and MMKV_PATH.stat().st_size > 0:
        return MMKV_PATH.read_bytes()

    command = ["adb", "exec-out", "su", "-c", f"cat {REMOTE_MMKV_PATH}"]
    data = subprocess.check_output(command, stderr=subprocess.STDOUT)
    if not data:
        raise RuntimeError(f"Read empty MMKV data from device path: {REMOTE_MMKV_PATH}")
    return data


def redact_text(text: str, secrets: list[str]) -> str:
    redacted = text
    for secret in sorted(set(secrets), key=len, reverse=True):
        if secret:
            redacted = redacted.replace(secret, "[REDACTED]")
    return redacted


def summarize_response(body: str) -> dict:
    parsed = json.loads(body)
    root = parsed.get("jData", {}).get("data", {})
    if isinstance(root, dict) and "data" in root:
        payload = root.get("data")
    else:
        payload = root
    if not isinstance(payload, dict):
        payload = {}
    place_data = payload.get("placeData") or []
    relate_map = payload.get("relateMap") or {}
    now_time = payload.get("nowTime")
    return {
        "ret": parsed.get("ret"),
        "iRet": parsed.get("iRet"),
        "sMsg": parsed.get("sMsg"),
        "sAmsSerial": parsed.get("sAmsSerial"),
        "businessCode": root.get("code") if isinstance(root, dict) else None,
        "businessMsg": root.get("msg") if isinstance(root, dict) else None,
        "nowTime": now_time,
        "placeDataCount": len(place_data) if isinstance(place_data, list) else 0,
        "placeData": place_data if isinstance(place_data, list) else [],
        "relateMap": relate_map if isinstance(relate_map, dict) else {},
        "relateMapKeys": list(relate_map.keys()) if isinstance(relate_map, dict) else [],
    }


def main() -> int:
    data = load_mmkv_bytes()
    strings = extract_strings(data)
    cookie = find_cookie_object(strings)
    cookie_header = build_cookie_header(cookie)

    secrets = [str(value) for value in cookie.values() if isinstance(value, str)]

    request = urllib.request.Request(
        ENDPOINT,
        data=b"",
        method="POST",
        headers={
            "Cookie": cookie_header,
            "Content-Type": "application/x-www-form-urlencoded",
            "User-Agent": "Mozilla/5.0",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = response.read().decode("utf-8", "replace")
            http_status = response.status
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace")
        http_status = exc.code

    redacted_body = redact_text(body, secrets)
    OUT_PATH.write_text(redacted_body, encoding="utf-8")

    summary = summarize_response(body)
    lines = [
        "# AMS Authenticated Crafting Probe",
        "",
        f"- HTTP status: {http_status}",
        f"- AMS ret: {summary.get('ret')}",
        f"- AMS iRet: {summary.get('iRet')}",
        f"- AMS sMsg: {summary.get('sMsg')}",
        f"- AMS serial: {summary.get('sAmsSerial')}",
        f"- Business code: {summary.get('businessCode')}",
        f"- Business msg: {summary.get('businessMsg')}",
        f"- placeData count: {summary.get('placeDataCount')}",
        "",
        "## Extracted Cookie Fields",
        "",
    ]
    for key, value in sorted(cookie.items()):
        lines.append(f"- `{key}`: present, length={len(str(value))}")

    lines.extend(["", "## Place Data", ""])
    relate_map = summary.get("relateMap", {})
    for item in summary.get("placeData", []):
        if not isinstance(item, dict):
            continue
        object_id = item.get("objectId")
        object_info = {}
        if isinstance(relate_map, dict) and object_id is not None:
            object_info = relate_map.get(str(object_id), {})
        fields = {
            "placeType": item.get("placeType"),
            "placeName": item.get("placeName"),
            "Status": item.get("Status"),
            "objectId": object_id,
            "objectName": object_info.get("objectName") if isinstance(object_info, dict) else None,
            "avgPrice": object_info.get("avgPrice") if isinstance(object_info, dict) else None,
            "pic": object_info.get("pic") if isinstance(object_info, dict) else None,
            "leftTime": item.get("leftTime"),
            "pushTime": item.get("pushTime"),
        }
        lines.append("- " + ", ".join(f"{k}={v}" for k, v in fields.items()))

    SUMMARY_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"HTTP status: {http_status}")
    print(f"AMS ret: {summary.get('ret')}")
    print(f"AMS iRet: {summary.get('iRet')}")
    print(f"AMS sMsg: {summary.get('sMsg')}")
    print(f"placeData count: {summary.get('placeDataCount')}")
    print(f"Cookie fields: {', '.join(sorted(cookie.keys()))}")
    print(f"Redacted response: {OUT_PATH}")
    print(f"Summary: {SUMMARY_PATH}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Probe failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
