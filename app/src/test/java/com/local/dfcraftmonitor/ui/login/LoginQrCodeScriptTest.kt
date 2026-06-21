package com.local.dfcraftmonitor.ui.login

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginQrCodeScriptTest {

    @Test
    fun loginScreenExposesQqAndWechatSwitchWithoutShrinkingWebView() {
        val screen = loginScreenSource()

        assertTrue(
            "login screen must keep a selected login method in Compose state",
            screen.contains("LoginMethod.QQ") && screen.contains("selectedLoginMethod"),
        )
        assertTrue(
            "login screen must expose both QQ and WeChat login choices",
            screen.contains("QQ登录") && screen.contains("微信登录"),
        )
        assertTrue(
            "login screen must pass the selected login method down to LoginWebView",
            screen.contains("loginMethod = selectedLoginMethod"),
        )
        assertTrue(
            "QQ and WeChat must use separate login URLs so the legacy QQ QR layout is not affected by the WebMP page",
            screen.contains("loginUrlFor(selectedLoginMethod)") &&
                screen.contains("https://pvp.qq.com/cp/a20161115tyf/page1.shtml") &&
                screen.contains("BuildConfig.DF_WEB_LOGIN_URL"),
        )
        assertTrue(
            "login method switching should reuse the existing status band instead of wrapping the QR WebView in a smaller fixed container",
            screen.contains("LoginMethodSelector(") && screen.contains("Modifier.fillMaxSize()"),
        )
    }

    @Test
    fun autoLoginSelectsConfiguredQqOrWechatButton() {
        val source = loginWebViewSource()
        val script = source
            .substringAfter("private const val JS_AUTO_LOGIN_TEMPLATE = \"\"\"")
            .substringBefore("\"\"\"")

        assertTrue(
            "LoginWebView must accept a login method so the app can switch between QQ and WeChat",
            source.contains("loginMethod: LoginMethod"),
        )
        assertTrue(
            "auto-login script must receive the selected method from Kotlin",
            script.contains("__dfPreferredLoginMethod"),
        )
        assertTrue(
            "auto-login script must know the QQ login API",
            script.contains("LoginManager.login();"),
        )
        assertTrue(
            "auto-login script must know the WeChat login API",
            script.contains("LoginManager.loginByWx"),
        )
        assertTrue(
            "auto-login script must prefer the official Delta Force WebMP Milo login APIs",
            script.contains("Milo.loginByWxDelegate") &&
                script.contains("Milo.mobileLoginByQQConnect"),
        )
        assertTrue(
            "WeChat login must use the official Delta Force WebMP scan-login appId/domain instead of mobile OAuth outside WeChat",
            script.contains("wxfa0c35392d06b82f") && script.contains("iu.qq.com"),
        )
        assertFalse(
            "embedded WebView must not use mobile WeChat OAuth because it shows 'please open in WeChat'",
            script.contains("Milo.mobileLoginByWX"),
        )
        assertTrue(
            "auto-login script must branch on the configured method rather than always opening QQ",
            script.contains("preferred === 'wechat'"),
        )
    }

    @Test
    fun autoLoginDoesNotDependOnDeadDologinClickHandler() {
        val script = loginWebViewSource()
            .substringAfter("private const val JS_AUTO_LOGIN_TEMPLATE = \"\"\"")
            .substringBefore("\"\"\"")

        assertFalse(
            "auto-login must not rely on #dologin because main.js can abort before binding its click handler",
            script.contains("document.getElementById('dologin')") || script.contains("dologin.click()"),
        )
    }

    @Test
    fun autoLoginUsesDirectLoginManagerApiForSelectedMethod() {
        val script = loginWebViewSource()
            .substringAfter("private const val JS_AUTO_LOGIN_TEMPLATE = \"\"\"")
            .substringBefore("\"\"\"")

        assertTrue(
            "WeChat auto-login must call LoginManager.loginByWx directly because the page's #dologin binding can fail when milo.ready aborts",
            script.contains("LoginManager.loginByWx"),
        )
        assertTrue(
            "QQ auto-login must call LoginManager.login directly instead of depending on a dead #dologin click handler",
            script.contains("LoginManager.login();"),
        )
        assertTrue(
            "selected login API must branch on the injected preferred method",
            script.contains("preferred === 'wechat'"),
        )
    }

    @Test
    fun defaultWechatLoginUrlUsesOfficialDeltaForceWebMiniProgram() {
        val buildGradle = buildGradleSource()

        assertTrue(
            "default WeChat login URL must be the current Delta Force WebMP page so WeChat receives the matching official login appId",
            buildGradle.contains("https://df.qq.com/cp/a20241230webmp/index.html"),
        )
    }

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
            .substringAfter("private const val JS_AUTO_LOGIN_TEMPLATE = \"\"\"")
            .substringBefore("\"\"\"")

        val captureIndex = script.indexOf("installPtloginFrameCapture();")
        val qqLoginIndex = script.indexOf("LoginManager.login();")
        val wxLoginIndex = script.indexOf("LoginManager.loginByWx")

        assertTrue(
            "auto-login must install a ptlogin iframe capture hook",
            captureIndex >= 0,
        )
        assertTrue(
            "iframe capture must be installed before direct QQ or WeChat login APIs create login frames",
            captureIndex < qqLoginIndex && captureIndex < wxLoginIndex,
        )
        assertTrue(
            "auto-login iframe capture should rescan existing iframes because the popup may already be present",
            script.contains("scanExistingPtloginFrames"),
        )
    }

    @Test
    fun wechatAutoLoginClicksWechatChoiceInOfficialCombinedPopup() {
        val script = loginWebViewSource()
            .substringAfter("private const val JS_AUTO_LOGIN_TEMPLATE = \"\"\"")
            .substringBefore("\"\"\"")

        val autoClickIndex = script.indexOf("installWechatChoiceAutoClicker();")
        val combinedLoginIndex = script.indexOf("Milo.loginByQQConnectAndWX")

        assertTrue(
            "WeChat auto-login must install a popup auto-clicker before opening the official QQ/WeChat combined dialog",
            autoClickIndex >= 0 && combinedLoginIndex >= 0 && autoClickIndex < combinedLoginIndex,
        )
        assertTrue(
            "auto-clicker must only run for the selected WeChat login method",
            script.contains("window.__dfPreferredLoginMethod !== 'wechat'"),
        )
        assertTrue(
            "auto-clicker must search for and click the WeChat choice in the official combined popup",
            script.contains("clickWechatLoginChoice") &&
                script.contains("微信") &&
                script.contains(".click()"),
        )
    }

    @Test
    fun wechatAutoLoginDoesNotOpenCombinedQqFallbackAfterVisibleWechatChoiceClicked() {
        val script = loginWebViewSource()
            .substringAfter("private const val JS_AUTO_LOGIN_TEMPLATE = \"\"\"")
            .substringBefore("\"\"\"")

        val visibleChoiceIndex = script.indexOf("__dfClickWechatLoginChoice")
        val skipFallbackIndex = script.indexOf("wechat choice already clicked; skipping official combined fallback")
        val combinedFallbackIndex = script.indexOf("Milo.loginByQQConnectAndWX")

        assertTrue(
            "WeChat auto-login must expose a direct visible-choice click path that tryMiloLogin can query synchronously",
            visibleChoiceIndex >= 0,
        )
        assertTrue(
            "When the visible WeChat choice has already been clicked, tryMiloLogin must stop instead of opening the QQ/WeChat combined fallback",
            skipFallbackIndex > visibleChoiceIndex && skipFallbackIndex < combinedFallbackIndex,
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
        assertTrue(
            "parent page scaler must be limited to the legacy QQ/PVP page and never run on WebMP WeChat pages",
            source.contains("isLegacyLoginUrl(url)") &&
                !source.contains("if (isLoginHost(url)) {\n                view.evaluateJavascript(autoLoginScript"),
        )
    }

    private fun loginWebViewSource(): String {
        val candidates = listOf(
            File("src/main/java/com/local/dfcraftmonitor/ui/login/LoginWebView.kt"),
            File("app/src/main/java/com/local/dfcraftmonitor/ui/login/LoginWebView.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }

    private fun loginScreenSource(): String {
        val candidates = listOf(
            File("src/main/java/com/local/dfcraftmonitor/ui/login/LoginScreen.kt"),
            File("app/src/main/java/com/local/dfcraftmonitor/ui/login/LoginScreen.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }

    private fun buildGradleSource(): String {
        val candidates = listOf(
            File("build.gradle"),
            File("app/build.gradle"),
        )
        return candidates.first { it.isFile && it.readText().contains("DF_WEB_LOGIN_URL") }.readText()
    }
}
