package com.local.dfcraftmonitor.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeActionsSourceTest {

    @Test
    fun loginScreenProvidesRefreshActionForWebView() {
        val loginScreen = source("ui/login/LoginScreen.kt")
        val loginWebView = source("ui/login/LoginWebView.kt")

        assertTrue(loginScreen.contains("Icons.Default.Refresh"))
        assertTrue(loginScreen.contains("refreshSignal"))
        assertTrue(loginWebView.contains("refreshSignal:"))
        assertTrue(loginWebView.contains("webView.loadUrl(initialUrl)"))
    }

    @Test
    fun homeScreenOverflowMenuShowsAccountRoleAndLogout() {
        val homeScreen = source("ui/home/HomeScreen.kt")

        assertTrue(homeScreen.contains("Icons.Default.MoreVert"))
        assertTrue(homeScreen.contains("DropdownMenu"))
        assertTrue(homeScreen.contains("当前账号"))
        assertTrue(homeScreen.contains("三角洲角色"))
        assertTrue(homeScreen.contains("退出重新登录"))
    }

    private fun source(relative: String): String {
        val path = "src/main/java/com/local/dfcraftmonitor/$relative"
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.first { it.isFile }.readText()
    }
}
