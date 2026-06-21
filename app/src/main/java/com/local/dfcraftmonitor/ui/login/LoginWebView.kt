package com.local.dfcraftmonitor.ui.login

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.local.dfcraftmonitor.data.remote.AmsConstants

private const val TAG = "DfLoginWebView"
private const val WEBMP_LOGIN_HOST = "df.qq.com"
private const val LEGACY_LOGIN_HOST = "pvp.qq.com"

enum class LoginMethod(val jsValue: String) {
    QQ("qq"),
    WECHAT("wechat"),
}

/**
 * 内嵌登录 WebView。
 *
 * 登录流程：
 * 1. 官方 WebMP 页面（df.qq.com）通过 Milo 登录 SDK 写入 AMS 四元组
 * 2. 旧 PVP 页面保留 LoginManager 兜底，便于历史环境继续可用
 * 3. Cookie 轮询检测登录完成（不依赖 postMessage）
 *
 * 自动点击 + 登录检测：
 * - 进入页面后注入 JS，自动调用 initLogin() 触发登录
 * - Cookie 轮询检测登录完成（不依赖 postMessage）
 */
@Composable
fun LoginWebView(
    initialUrl: String,
    loginMethod: LoginMethod,
    refreshSignal: Int = 0,
    onLoginSuccess: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLoginMethod = rememberUpdatedState(loginMethod)
    var loginDetected by remember { mutableStateOf(false) }
    var lastRefreshSignal by remember { mutableStateOf(refreshSignal) }
    var lastLoginMethod by remember { mutableStateOf(loginMethod) }
    val harvester = remember { CookieHarvester() }

    DisposableEffect(Unit) {
        onDispose { /* WebView 由 AndroidView 生命周期管理 */ }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                configureWebView(
                    harvester = harvester,
                    loginMethodProvider = { currentLoginMethod.value },
                    onCookiesReady = { cookies ->
                        if (!loginDetected && harvester.isLoginComplete(cookies)) {
                            loginDetected = true
                            onLoginSuccess(cookies)
                        }
                    },
                )
                loadUrl(initialUrl)
            }
        },
        update = { webView ->
            if (lastRefreshSignal != refreshSignal || lastLoginMethod != loginMethod) {
                lastRefreshSignal = refreshSignal
                lastLoginMethod = loginMethod
                loginDetected = false
                webView.loadUrl(initialUrl)
            }
        },
    )
}

/**
 * JS Bridge：从 JS 层接收事件，调度到主线程。
 */
private class DfLoginBridge(
    private val webView: WebView,
) {
    @JavascriptInterface
    fun loadPtlogin(url: String) {
        val normalizedUrl = when {
            url.startsWith("//") -> "https:$url"
            else -> url
        }
        Log.d(TAG, "JS bridge loading ptlogin: $normalizedUrl")
        webView.post { webView.loadUrl(normalizedUrl) }
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d(TAG, "JS: $message")
    }
}

/**
 * 配置 WebView。
 */
private fun WebView.configureWebView(
    harvester: CookieHarvester,
    loginMethodProvider: () -> LoginMethod,
    onCookiesReady: (Map<String, String>) -> Unit,
) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true
        loadWithOverviewMode = true
        useWideViewPort = true
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = AmsConstants.DESKTOP_UA
    }

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    // 注册 JS Bridge
    addJavascriptInterface(DfLoginBridge(this), "DfBridge")

    // Cookie 轮询：每 2 秒检查一次
    val cookiePollHandler = Handler(Looper.getMainLooper())
    var pollRunnable: Runnable? = null
    val qrScaleHandler = Handler(Looper.getMainLooper())
    var scaleRunnable: Runnable? = null

    fun startCookiePolling() {
        pollRunnable?.let { cookiePollHandler.removeCallbacks(it) }
        pollRunnable = object : Runnable {
            override fun run() {
                CookieManager.getInstance().flush()
                val c = harvester.harvest()
                onCookiesReady(c)
                if (!harvester.isLoginComplete(c)) {
                    cookiePollHandler.postDelayed(this, 2000)
                }
            }
        }
        cookiePollHandler.postDelayed(pollRunnable!!, 2000)
    }

    fun startLoginDialogScaling(view: WebView) {
        scaleRunnable?.let { qrScaleHandler.removeCallbacks(it) }
        scaleRunnable = object : Runnable {
            private var attempts = 0
            private var scaled = false

            override fun run() {
                if (scaled) return
                attempts++
                view.evaluateJavascript(JS_SCALE_LOGIN_DIALOG) { result ->
                    if (result == "true") {
                        scaled = true
                        qrScaleHandler.removeCallbacks(this)
                        Log.d(TAG, "visible ptlogin dialog scaled")
                    }
                }
                if (!scaled && attempts < 80) {
                    qrScaleHandler.postDelayed(this, 250)
                }
            }
        }
        qrScaleHandler.post(scaleRunnable!!)
    }

    webViewClient = object : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (url != null && isLegacyLoginUrl(url)) {
                // 页面开始加载时立即注入早期 hook（在 LoginManager 加载前准备）
                view.evaluateJavascript(JS_EARLY_HOOK, null)
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            // 不拦截任何 URL，让 WebView 正常加载
            return false
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            CookieManager.getInstance().flush()

            if (isLegacyLoginUrl(url) || isWebMpLoginUrl(url)) {
                view.evaluateJavascript(autoLoginScript(loginMethodProvider()), null)
            }

            if (isLegacyLoginUrl(url)) {
                startLoginDialogScaling(view)
            }

            if (url.contains("ptlogin2.qq.com")) {
                view.evaluateJavascript(JS_ENLARGE_QRCODE, null)
            }

            val cookies = harvester.harvest()
            onCookiesReady(cookies)

            startCookiePolling()
        }
    }

    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(
                TAG,
                "console: ${consoleMessage.message()} " +
                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
            )
            return super.onConsoleMessage(consoleMessage)
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val transport = WebView(view.context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = AmsConstants.DESKTOP_UA
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                var ptloginTransferred = false
                fun transferPtloginToMain(url: String?): Boolean {
                    if (ptloginTransferred || url == null || !url.contains("ptlogin2.qq.com")) {
                        return false
                    }
                    ptloginTransferred = true
                    view.loadUrl(url)
                    return true
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        tv: WebView,
                        url: String?,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        super.onPageStarted(tv, url, favicon)
                        if (transferPtloginToMain(url)) {
                            tv.stopLoading()
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        tv: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        return transferPtloginToMain(request.url.toString())
                    }

                    override fun onPageFinished(tv: WebView, url: String) {
                        super.onPageFinished(tv, url)
                        CookieManager.getInstance().flush()
                        val cookies = harvester.harvest()
                        onCookiesReady(cookies)
                    }
                }
            }

            (view.parent as? ViewGroup)?.addView(
                transport,
                ViewGroup.LayoutParams(1, 1),
            )

            val transportWrapper = resultMsg.obj as WebView.WebViewTransport
            transportWrapper.webView = transport
            resultMsg.sendToTarget()
            return true
        }
    }
}

private fun isLegacyLoginUrl(url: String): Boolean =
    url.contains(LEGACY_LOGIN_HOST)

private fun isWebMpLoginUrl(url: String): Boolean =
    url.contains(WEBMP_LOGIN_HOST)

// ========================================================================
// JS 脚本
// ========================================================================

/**
 * 早期 hook：在 LoginManager 加载前准备劫持环境。
 * 在 onPageStarted 时立即注入，能更早捕获 LoginManager。
 */
private const val JS_EARLY_HOOK = """
(function() {
    if (window.__dfEarlyHooked) return;
    window.__dfEarlyHooked = true;
    window.__dfCapturedLoginSrc = null;

    // 早期劫持：监控 iframe 创建
    var observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            for (var i = 0; i < mutation.addedNodes.length; i++) {
                var node = mutation.addedNodes[i];
                var iframes = [];
                if (node.tagName === 'IFRAME') {
                    iframes.push(node);
                }
                if (node.querySelectorAll) {
                    var nested = node.querySelectorAll('iframe');
                    for (var j = 0; j < nested.length; j++) iframes.push(nested[j]);
                }
                for (var k = 0; k < iframes.length; k++) {
                    var f = iframes[k];
                    var s = f.src || f.getAttribute('place_src') || '';
                    if (s.indexOf('ptlogin2.qq.com') !== -1 && !window.__dfCapturedLoginSrc) {
                        window.__dfCapturedLoginSrc = s;
                        if (window.DfBridge) window.DfBridge.loadPtlogin(s);
                        setTimeout(function() {
                            var ld = document.getElementById('loginDiv');
                            if (ld) ld.style.display = 'none';
                            f.style.display = 'none';
                        }, 50);
                        return;
                    }
                }
            }
        });
    });

    function startObserve() {
        if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true });
        } else {
            document.addEventListener('DOMContentLoaded', function() {
                observer.observe(document.body, { childList: true, subtree: true });
            });
        }
    }
    startObserve();
})();
"""

/**
 * 自动登录 JS。
 *
 * 多策略保证可靠性：
 * 1. 等待 LoginManager 加载后劫持 openLoginDiv
 * 2. 直接点击 #dologin
 * 3. 直接调用 LoginManager.login()
 */
private fun autoLoginScript(loginMethod: LoginMethod): String =
    JS_AUTO_LOGIN_TEMPLATE.replace("__LOGIN_METHOD__", loginMethod.jsValue)

private const val JS_AUTO_LOGIN_TEMPLATE = """
(function() {
    if (window.__dfAutoLoginStarted) return;
    window.__dfAutoLoginStarted = true;
    window.__dfPreferredLoginMethod = '__LOGIN_METHOD__';

    var MAX_RETRIES = 30;
    var retryCount = 0;

    function bridgeLog(message) {
        try {
            if (window.DfBridge && window.DfBridge.log) {
                window.DfBridge.log(message);
            }
        } catch (e) {}
    }

    function normalizePtloginUrl(raw) {
        if (!raw) return '';
        var url = String(raw);
        if (!url || url === 'about:blank' || url.indexOf('javascript:') === 0) return '';
        if (url.indexOf('//') === 0) return window.location.protocol + url;
        return url;
    }

    function isPtloginUrl(raw) {
        var url = normalizePtloginUrl(raw);
        return url.indexOf('ptlogin2.qq.com') !== -1;
    }

    function hideLoginPopup(frame) {
        var loginDiv = document.getElementById('loginDiv') ||
            document.querySelector('.login_dialog') ||
            document.querySelector('[id*=login][style*=display]');
        if (loginDiv) loginDiv.style.display = 'none';

        var node = frame;
        var depth = 0;
        while (node && node !== document.body && depth < 4) {
            node.style.display = 'none';
            node = node.parentElement;
            depth++;
        }
    }

    function reportPtloginUrl(raw, frame) {
        var url = normalizePtloginUrl(raw);
        if (!isPtloginUrl(url)) return false;
        if (window.__dfCapturedLoginSrc === url) return true;

        window.__dfCapturedLoginSrc = url;
        bridgeLog('captured ptlogin iframe: ' + url);
        if (window.DfBridge && window.DfBridge.loadPtlogin) {
            window.DfBridge.loadPtlogin(url);
        }
        if (frame) {
            setTimeout(function() { hideLoginPopup(frame); }, 50);
        }
        return true;
    }

    function frameUrl(frame) {
        if (!frame) return '';
        var attrs = ['src', 'place_src', 'data-src', 'data-url', 'url'];
        for (var i = 0; i < attrs.length; i++) {
            var value = frame.getAttribute && frame.getAttribute(attrs[i]);
            if (isPtloginUrl(value)) return value;
        }
        if (isPtloginUrl(frame.src)) return frame.src;
        return '';
    }

    function inspectNodeForPtlogin(node) {
        if (!node) return false;
        if (node.tagName === 'IFRAME' || node.tagName === 'FRAME') {
            var direct = frameUrl(node);
            if (direct) return reportPtloginUrl(direct, node);
        }
        if (!node.querySelectorAll) return false;
        var frames = node.querySelectorAll('iframe, frame');
        for (var i = 0; i < frames.length; i++) {
            var url = frameUrl(frames[i]);
            if (url && reportPtloginUrl(url, frames[i])) return true;
        }
        return false;
    }

    function scanExistingPtloginFrames() {
        return inspectNodeForPtlogin(document.documentElement);
    }

    function installPtloginFrameCapture() {
        if (window.__dfPtloginFrameCaptureInstalled) {
            scanExistingPtloginFrames();
            return;
        }
        window.__dfPtloginFrameCaptureInstalled = true;
        bridgeLog('installing ptlogin iframe capture');

        var originalSetAttribute = Element.prototype.setAttribute;
        Element.prototype.setAttribute = function(name, value) {
            var result = originalSetAttribute.apply(this, arguments);
            if ((this.tagName === 'IFRAME' || this.tagName === 'FRAME') &&
                /^(src|place_src|data-src|data-url)$/i.test(name || '')) {
                reportPtloginUrl(value, this);
            }
            return result;
        };

        var observer = new MutationObserver(function(mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var mutation = mutations[i];
                if (mutation.type === 'attributes') {
                    inspectNodeForPtlogin(mutation.target);
                }
                for (var j = 0; j < mutation.addedNodes.length; j++) {
                    inspectNodeForPtlogin(mutation.addedNodes[j]);
                }
            }
        });

        function startObserve() {
            var root = document.documentElement || document.body;
            if (!root) {
                setTimeout(startObserve, 50);
                return;
            }
            observer.observe(root, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['src', 'place_src', 'data-src', 'data-url']
            });
            scanExistingPtloginFrames();
        }
        startObserve();

        var scans = 0;
        var scanTimer = setInterval(function() {
            scans++;
            scanExistingPtloginFrames();
            if (window.__dfCapturedLoginSrc || scans >= 120) {
                clearInterval(scanTimer);
            }
        }, 250);
    }

    function installWechatChoiceAutoClicker() {
        if (window.__dfPreferredLoginMethod !== 'wechat') return false;
        if (window.__dfWechatChoiceAutoClickerInstalled) return true;
        window.__dfWechatChoiceAutoClickerInstalled = true;

        function isVisibleElement(el) {
            if (!el || !el.getBoundingClientRect) return false;
            var rect = el.getBoundingClientRect();
            var style = window.getComputedStyle(el);
            return rect.width > 8 &&
                rect.height > 8 &&
                style.display !== 'none' &&
                style.visibility !== 'hidden' &&
                style.opacity !== '0';
        }

        function compactText(el) {
            var text = (el.innerText || el.textContent || '').replace(/\s+/g, '');
            var aria = (el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('title'))) || '';
            return (text || aria || '').replace(/\s+/g, '');
        }

        function markerText(el) {
            if (!el || !el.getAttribute) return '';
            var className = '';
            try {
                className = typeof el.className === 'string' ? el.className : '';
            } catch (e) {}
            return [
                el.id || '',
                className,
                el.getAttribute('data-type') || '',
                el.getAttribute('data-login') || '',
                el.getAttribute('name') || ''
            ].join(' ').toLowerCase();
        }

        function looksLikeWechatChoice(el) {
            var text = compactText(el);
            if (text.indexOf('微信') !== -1 && text.indexOf('QQ') === -1 && text.length <= 16) {
                return true;
            }
            var marker = markerText(el);
            return (marker.indexOf('wechat') !== -1 || marker.indexOf('wx') !== -1) &&
                marker.indexOf('qq') === -1;
        }

        function clickableTarget(el) {
            var node = el;
            var depth = 0;
            while (node && node !== document.body && depth < 5) {
                var tag = (node.tagName || '').toUpperCase();
                var role = node.getAttribute && node.getAttribute('role');
                var marker = markerText(node);
                if (
                    tag === 'BUTTON' ||
                    tag === 'A' ||
                    role === 'button' ||
                    typeof node.onclick === 'function' ||
                    marker.indexOf('btn') !== -1 ||
                    marker.indexOf('login') !== -1 ||
                    marker.indexOf('wechat') !== -1 ||
                    marker.indexOf('wx') !== -1
                ) {
                    return node;
                }
                node = node.parentElement;
                depth++;
            }
            return el;
        }

        function clickWechatLoginChoice() {
            if (window.__dfPreferredLoginMethod !== 'wechat') return false;
            if (window.__dfWechatChoiceClicked) return true;

            var candidates = document.querySelectorAll(
                'button, a, [role="button"], [class*=wx], [class*=wechat], [id*=wx], [id*=wechat], div, span, li'
            );
            for (var i = 0; i < candidates.length; i++) {
                var candidate = candidates[i];
                if (!isVisibleElement(candidate) || !looksLikeWechatChoice(candidate)) continue;

                var target = clickableTarget(candidate);
                if (!isVisibleElement(target)) target = candidate;
                if (target && !target.disabled) {
                    window.__dfWechatChoiceClicked = true;
                    bridgeLog('auto clicking official wechat login choice');
                    target.click();
                    return true;
                }
            }
            return false;
        }

        window.__dfClickWechatLoginChoice = clickWechatLoginChoice;

        var attempts = 0;
        var timer = setInterval(function() {
            attempts++;
            if (clickWechatLoginChoice() || attempts >= 120) {
                clearInterval(timer);
            }
        }, 250);

        var observer = new MutationObserver(function() {
            if (clickWechatLoginChoice()) {
                observer.disconnect();
                clearInterval(timer);
            }
        });

        function observe() {
            var root = document.documentElement || document.body;
            if (!root) {
                setTimeout(observe, 50);
                return;
            }
            observer.observe(root, { childList: true, subtree: true, attributes: true });
            clickWechatLoginChoice();
        }
        observe();
        return true;
    }

    installPtloginFrameCapture();
    installWechatChoiceAutoClicker();

    function reloadAfterLogin() {
        bridgeLog('login callback, reloading page');
        window.location.reload();
    }

    function officialReturnUrl() {
        return window.location.href.split('#')[0];
    }

    function officialQqParams() {
        return {
            appId: "101491592",
            scope: "get_user_info",
            state: "STATE",
            redirectUri: "https://milo.qq.com/comm-htdocs/login/qc_redirect.html",
            sUrl: officialReturnUrl(),
            callback: reloadAfterLogin,
            showParams: { needReloadPage: true }
        };
    }

    function officialWxParams() {
        return {
            appId: "wxfa0c35392d06b82f",
            gameDomain: "iu.qq.com",
            lang: "zh_CN",
            callback: reloadAfterLogin,
            fail: function(err) {
                bridgeLog('official wechat scan login failed: ' + err);
            }
        };
    }

    function tryMiloLogin() {
        if (typeof Milo === 'undefined') return false;

        var preferred = window.__dfPreferredLoginMethod || 'qq';
        window.iUseQQConnect = 1;

        if (preferred === 'wechat') {
            if (window.__dfWechatChoiceClicked) {
                bridgeLog('wechat choice already clicked; skipping official combined fallback');
                return true;
            }
            if (typeof window.__dfClickWechatLoginChoice === 'function' && window.__dfClickWechatLoginChoice()) {
                bridgeLog('wechat visible choice clicked synchronously; skipping official combined fallback');
                return true;
            }
            if (typeof Milo.loginByWxDelegate === 'function') {
                bridgeLog('starting official wechat scan login via Milo.loginByWxDelegate');
                Milo.loginByWxDelegate(officialWxParams());
                return true;
            }
            if (typeof Milo.loginByQQConnectAndWX === 'function') {
                bridgeLog('falling back to official pc combined login for wechat');
                Milo.loginByQQConnectAndWX({
                    oQQConnectParams: officialQqParams(),
                    oWXParams: officialWxParams()
                });
                return true;
            }
        }

        if (preferred !== 'wechat') {
            if (typeof Milo.mobileLoginByQQConnect === 'function') {
                bridgeLog('starting official qq login via Milo.mobileLoginByQQConnect');
                Milo.mobileLoginByQQConnect(officialQqParams());
                return true;
            }
            if (typeof Milo.loginByQQConnect === 'function') {
                bridgeLog('starting official qq login via Milo.loginByQQConnect');
                Milo.loginByQQConnect(officialQqParams());
                return true;
            }
        }

        if (typeof Milo.loginByQQConnectAndWX === 'function') {
            bridgeLog('falling back to official combined pc login');
            Milo.loginByQQConnectAndWX({
                oQQConnectParams: officialQqParams(),
                oWXParams: officialWxParams()
            });
            return true;
        }

        return false;
    }

    function hookOpenLoginDiv() {
        if (typeof LoginManager === 'undefined') return false;
        if (!LoginManager._coverdiv) return false;
        if (!LoginManager._coverdiv.openLoginDiv) return false;

        var original = LoginManager._coverdiv.openLoginDiv;
        LoginManager._coverdiv.openLoginDiv = function(opts) {
            var loginUrl = opts && (opts.src || opts.url || opts.href);
            if (reportPtloginUrl(loginUrl, null)) {
                return;
            }
            return original.call(this, opts);
        };
        return true;
    }

    function tryAutoLogin() {
        retryCount++;
        if (retryCount > MAX_RETRIES) return;

        if (tryMiloLogin()) return;

        if (typeof need !== 'function') {
            bridgeLog('Milo/need() is not ready, retrying login bootstrap');
            setTimeout(tryAutoLogin, 300);
            return;
        }

        try {
            need("biz.login", function(LoginManager) {
                var preferred = window.__dfPreferredLoginMethod || 'qq';
                window.iUseQQConnect = 1;

                if (LoginManager && typeof LoginManager.init === 'function') {
                    LoginManager.init({ iUseQQConnect: 1, userinfoSpan: "login_qq_span" });
                }

                if (preferred === 'wechat' && LoginManager && typeof LoginManager.loginByWx === 'function') {
                    bridgeLog('starting wechat login via LoginManager.loginByWx');
                    LoginManager.loginByWx({
                        appId: "wx1cd4fbe9335888fe",
                        gameDomain: "iu.qq.com",
                        callback: function() {
                            bridgeLog('wechat login callback');
                            window.location.reload();
                        },
                        fail: function(err) {
                            bridgeLog('wechat login failed: ' + err);
                        }
                    });
                    return;
                }

                if (preferred !== 'wechat' && LoginManager && typeof LoginManager.login === 'function') {
                    bridgeLog('starting qq login via LoginManager.login');
                    LoginManager.login();
                    return;
                }

                if (LoginManager && typeof LoginManager.loginByWXAndQQ === 'function') {
                    bridgeLog('falling back to LoginManager.loginByWXAndQQ');
                    LoginManager.loginByWXAndQQ(
                        {
                            appId: "wx1cd4fbe9335888fe",
                            gameDomain: "iu.qq.com",
                            iUseQQConnect: 1,
                            callback: function() {
                                bridgeLog('combined wechat login callback');
                                window.location.reload();
                            },
                            fail: function(err) {
                                bridgeLog('combined login failed: ' + err);
                            }
                        },
                        function() {
                            bridgeLog('combined qq login callback');
                            window.location.reload();
                        }
                    );
                    return;
                }

                bridgeLog('LoginManager has no supported login API, retrying');
                setTimeout(tryAutoLogin, 300);
            });
        } catch (e) {
            bridgeLog('login bootstrap failed: ' + (e && e.message ? e.message : e));
            setTimeout(tryAutoLogin, 300);
        }

    }

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        setTimeout(tryAutoLogin, 100);
    } else {
        document.addEventListener('DOMContentLoaded', function() {
            setTimeout(tryAutoLogin, 100);
        });
    }
})();
"""

/**
 * 放大嵌在 pvp.qq.com 父页面里的 QQ 登录 iframe。
 *
 * ptlogin 的二维码实际渲染在跨域 iframe 内，父页面不能直接修改 iframe 内部 DOM，
 * 因此这里把整个 iframe 渲染层放大并固定到屏幕前景，让二维码本身在设备上变大。
 */
private const val JS_SCALE_LOGIN_DIALOG = """
(function() {
    function log(message) {
        try {
            if (window.DfBridge && window.DfBridge.log) {
                window.DfBridge.log(message);
            }
        } catch (e) {}
    }

    function isVisible(el) {
        if (!el) return false;
        var rect = el.getBoundingClientRect();
        var style = window.getComputedStyle(el);
        return rect.width > 80 &&
            rect.height > 80 &&
            style.display !== 'none' &&
            style.visibility !== 'hidden' &&
            style.opacity !== '0';
    }

    function frameUrl(frame) {
        if (!frame) return '';
        return frame.src ||
            frame.getAttribute('src') ||
            frame.getAttribute('place_src') ||
            frame.getAttribute('data-src') ||
            '';
    }

    function findPtloginFrame() {
        var frames = document.querySelectorAll('iframe, frame');
        var fallback = null;
        for (var i = 0; i < frames.length; i++) {
            var frame = frames[i];
            var url = frameUrl(frame);
            if (url.indexOf('ptlogin2.qq.com') !== -1 && isVisible(frame)) {
                return frame;
            }
            var rect = frame.getBoundingClientRect();
            if (!fallback && isVisible(frame) && rect.width > 500 && rect.height > 300) {
                fallback = frame;
            }
        }
        return fallback;
    }

    function setImportant(el, name, value) {
        el.style.setProperty(name, value, 'important');
    }

    var frame = findPtloginFrame();
    if (!frame) return false;

    var viewportWidth = window.innerWidth || document.documentElement.clientWidth || 1080;
    var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 1900;
    var scale = 3;
    var left = Math.round((viewportWidth / 2) - (245 * scale));
    var top = Math.max(170, Math.round((viewportHeight / 2) - (185 * scale)));

    setImportant(frame, 'position', 'fixed');
    setImportant(frame, 'left', left + 'px');
    setImportant(frame, 'top', top + 'px');
    setImportant(frame, 'width', '760px');
    setImportant(frame, 'height', '520px');
    setImportant(frame, 'min-width', '760px');
    setImportant(frame, 'min-height', '520px');
    setImportant(frame, 'transform', 'scale(3)');
    setImportant(frame, 'transform-origin', '0 0');
    setImportant(frame, 'z-index', '2147483647');
    setImportant(frame, 'border', '0');
    setImportant(frame, 'background', '#fff');
    setImportant(frame, 'box-shadow', '0 8px 28px rgba(0,0,0,.22)');

    var node = frame.parentElement;
    var depth = 0;
    while (node && node !== document.body && depth < 8) {
        setImportant(node, 'overflow', 'visible');
        setImportant(node, 'z-index', '2147483646');
        node = node.parentElement;
        depth++;
    }

    document.documentElement.style.setProperty('overflow', 'hidden', 'important');
    document.body.style.setProperty('overflow', 'hidden', 'important');
    window.__dfPtloginDialogScaled = true;
    log('scaled visible ptlogin iframe at ' + left + ',' + top);
    return true;
})();
"""

/**
 * 放大 ptlogin2.qq.com 页面的二维码图片。
 * 二维码在桌面页面中尺寸较小，在手机端不便扫码，
 * 通过 JS 将其放大至接近屏幕宽度，同时限制最大尺寸防止溢出。
 */
private const val JS_ENLARGE_QRCODE = """
(function() {
    if (window.__dfQrEnlargeStarted) {
        if (window.__dfEnlargeQrNow) window.__dfEnlargeQrNow();
        return;
    }
    window.__dfQrEnlargeStarted = true;

    function setImportant(el, name, value) {
        el.style.setProperty(name, value, 'important');
    }

    function findQrImage() {
        var selectors = [
            '#qrcode_img',
            '#qrlogin_img',
            '#qr_img',
            '.qrcode-img img',
            '.qrlogin_img img',
            '.qr-img img',
            '#qrlogin img',
            '#qrImg',
            '#qrimg',
            '.qrcode img',
            'img[src*=ptqrshow]',
            'img[src*=qrcode]',
            'img[class*=qr]',
            'img[id*=qr]'
        ];

        for (var i = 0; i < selectors.length; i++) {
            var selected = document.querySelector(selectors[i]);
            if (selected) return selected;
        }

        var imgs = document.querySelectorAll('img');
        var best = null;
        var bestSize = 0;
        for (var j = 0; j < imgs.length; j++) {
            var img = imgs[j];
            var width = img.naturalWidth || img.width || parseInt(img.getAttribute('width'), 10) || 0;
            var height = img.naturalHeight || img.height || parseInt(img.getAttribute('height'), 10) || 0;
            var src = img.currentSrc || img.src || '';
            var looksLikeQr = src.indexOf('ptqrshow') !== -1 ||
                src.indexOf('qrcode') !== -1 ||
                src.indexOf('qr') !== -1;
            var isSquare = width >= 80 && height >= 80 && Math.abs(width - height) <= 12;
            if ((looksLikeQr || isSquare) && Math.min(width, height) > bestSize) {
                best = img;
                bestSize = Math.min(width, height);
            }
        }
        return best;
    }

    function enlarge() {
        var qrImg = findQrImage();
        if (!qrImg) return;

        var vw = window.innerWidth || document.documentElement.clientWidth;
        var vh = window.innerHeight || document.documentElement.clientHeight;
        var targetSize = Math.floor(Math.min(vw * 0.88, vh * 0.7, 420));
        targetSize = Math.max(targetSize, 180);
        targetSize = Math.min(targetSize, Math.floor(vw * 0.96), Math.floor(vh * 0.8));

        setImportant(qrImg, 'width', targetSize + 'px');
        setImportant(qrImg, 'height', targetSize + 'px');
        setImportant(qrImg, 'max-width', '96vw');
        setImportant(qrImg, 'max-height', '80vh');
        setImportant(qrImg, 'display', 'block');
        setImportant(qrImg, 'margin', '0 auto');
        setImportant(qrImg, 'image-rendering', 'pixelated');

        var node = qrImg.parentElement;
        var depth = 0;
        while (node && node !== document.body && depth < 5) {
            setImportant(node, 'width', targetSize + 'px');
            setImportant(node, 'max-width', '96vw');
            setImportant(node, 'height', 'auto');
            setImportant(node, 'margin-left', 'auto');
            setImportant(node, 'margin-right', 'auto');
            setImportant(node, 'overflow', 'visible');
            node = node.parentElement;
            depth++;
        }

        setImportant(document.body, 'overflow', 'hidden');
        setImportant(document.body, 'margin', '0');
        setImportant(document.body, 'padding', '0');
        setImportant(document.body, 'min-height', '100vh');
        setImportant(document.documentElement, 'overflow', 'hidden');
        window.__dfQrEnlarged = true;
    }

    window.__dfEnlargeQrNow = enlarge;

    var pending = false;
    function scheduleEnlarge() {
        if (pending) return;
        pending = true;
        setTimeout(function() {
            pending = false;
            enlarge();
        }, 50);
    }

    enlarge();
    var attempts = 0;
    var retryTimer = setInterval(function() {
        attempts++;
        enlarge();
        if (window.__dfQrEnlarged && attempts >= 12) {
            clearInterval(retryTimer);
        }
        if (attempts >= 120) {
            clearInterval(retryTimer);
        }
    }, 250);

    var observer = new MutationObserver(scheduleEnlarge);
    observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['src', 'class', 'width', 'height']
    });
})();
"""
