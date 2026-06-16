package com.local.dfcraftmonitor.ui.login

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginQrCodeScriptTest {

    @Test
    fun qrCodeEnlargerKeepsWatchingForLateLoadedQrImages() {
        val script = loginWebViewSource()
            .substringAfter("private const val JS_ENLARGE_QRCODE = \"\"\"")
            .substringBefore("\"\"\"")

        assertTrue(
            "QR enlarger must observe DOM changes because ptlogin creates or refreshes QR images asynchronously",
            script.contains("MutationObserver"),
        )
        assertFalse(
            "QR enlarger must not mark itself complete before a QR image has actually been found",
            script.contains(
                "if (window.__dfQrEnlarged) return;\n" +
                    "    window.__dfQrEnlarged = true;",
            ),
        )
        assertTrue(
            "QR enlarger should keep a bounded retry loop for delayed image decode/load",
            script.contains("setInterval"),
        )
    }

    @Test
    fun popupTransportTransfersPtloginOnFirstPageStart() {
        val transportClient = loginWebViewSource()
            .substringAfter("val transport = WebView(view.context).apply")
            .substringBefore("val transportWrapper = resultMsg.obj")

        assertTrue(
            "window.open transport must transfer the initial ptlogin URL even when shouldOverrideUrlLoading is skipped",
            transportClient.contains("override fun onPageStarted"),
        )
        assertTrue(
            "transport transfer should route ptlogin back to the visible main WebView",
            transportClient.contains("transferPtloginToMain(url)"),
        )
    }

    @Test
    fun autoLoginInstallsPtloginIframeCaptureBeforeTriggeringLogin() {
        val script = loginWebViewSource()
            .substringAfter("private const val JS_AUTO_LOGIN = \"\"\"")
            .substringBefore("\"\"\"")

        val captureIndex = script.indexOf("installPtloginFrameCapture();")
        val initLoginIndex = script.indexOf("initLogin();")

        assertTrue(
            "auto-login must install a ptlogin iframe capture hook",
            captureIndex >= 0,
        )
        assertTrue(
            "iframe capture must be installed before initLogin creates the QQ login iframe",
            captureIndex < initLoginIndex,
        )
        assertTrue(
            "auto-login iframe capture should rescan existing iframes because the popup may already be present",
            script.contains("scanExistingPtloginFrames"),
        )
    }

    @Test
    fun parentPageScalerEnlargesVisiblePtloginIframe() {
        val source = loginWebViewSource()
        val script = source
            .substringAfter("private const val JS_SCALE_LOGIN_DIALOG = \"\"\"")
            .substringBefore("\"\"\"")

        assertTrue(
            "parent page scaler must target the visible ptlogin iframe",
            script.contains("iframe") && script.contains("ptlogin2.qq.com"),
        )
        assertTrue(
            "parent page scaler must enlarge the iframe rendered contents",
            script.contains("transform") && script.contains("scale(3"),
        )
        assertTrue(
            "Kotlin must repeatedly inject the parent page scaler while the login popup is appearing",
            source.contains("JS_SCALE_LOGIN_DIALOG"),
        )
    }

    private fun loginWebViewSource(): String {
        val candidates = listOf(
            File("src/main/java/com/local/dfcraftmonitor/ui/login/LoginWebView.kt"),
            File("app/src/main/java/com/local/dfcraftmonitor/ui/login/LoginWebView.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }
}
