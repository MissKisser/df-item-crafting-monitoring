package com.local.dfcraftmonitor.ui.login

import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.local.dfcraftmonitor.data.remote.AmsConstants

/**
 * 内嵌登录 WebView（M3-5）。
 *
 * 保留 spike 反复验证过的关键配置（这些不能丢）：
 * 1. **setSupportMultipleWindows(true) + WebChromeClient.onCreateWindow**：
 *    pvp.qq.com 登录按钮用 window.open() 弹 ptlogin 登录窗，缺这俩点击就无反应。
 * 2. **桌面 Chrome UA**：避免被 QQ/AMS 风控判成 WebView。
 * 3. **setAcceptThirdPartyCookies(true)**：QQ/微信登录链路依赖第三方 Cookie。
 * 4. **setDomStorageEnabled(true)**：ptlogin 登录页用 localStorage。
 *
 * 登录成功后通过 [onLoginSuccess] 回调（自动判定，不需用户手点）。
 */
@Composable
fun LoginWebView(
    initialUrl: String,
    onLoginSuccess: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
                configureWebView { cookies ->
                    if (harvester.isLoginComplete(cookies)) {
                        onLoginSuccess(cookies)
                    }
                }
                loadUrl(initialUrl)
            }
        },
    )
}

/**
 * 配置 WebView 的关键参数。保留 spike 反复验证过的 onCreateWindow 内核。
 */
private fun WebView.configureWebView(
    onPageFinished: (Map<String, String>) -> Unit,
) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        // window.open 弹窗支持：必须同时 setSupportMultipleWindows(true)
        // 和提供 WebChromeClient.onCreateWindow，否则点击无反应。
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true
        loadWithOverviewMode = true
        useWideViewPort = true
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = AmsConstants.DESKTOP_UA
    }

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            CookieManager.getInstance().flush()
            val harvester = CookieHarvester()
            onPageFinished(harvester.harvest())
        }
    }

    // 处理 window.open：ptlogin 登录窗通过 window.open() 打开。
    // 标准做法：创建临时 transport WebView 满足 window.open 协议，
    // 在它的 WebViewClient.shouldOverrideUrlLoading 里把首个 URL 转给主 webView。
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val transport = WebView(view.context).apply {
                settings.javaScriptEnabled = true
            }
            transport.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(tv: WebView, url: String): Boolean {
                    view.loadUrl(url)
                    return true
                }
            }
            val transportWrapper = resultMsg.obj as WebView.WebViewTransport
            transportWrapper.webView = transport
            resultMsg.sendToTarget()
            return true
        }
    }
}
