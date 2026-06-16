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
    fun homeScreenUsesV2MiniProgramTabsWithoutStrategyOrOverflowMenu() {
        val homeScreen = source("ui/home/HomeScreen.kt")

        assertTrue(homeScreen.contains("NavigationBarItem"))
        assertTrue(homeScreen.contains("\"首页\""))
        assertTrue(homeScreen.contains("\"工具\""))
        assertTrue(homeScreen.contains("\"我的\""))
        assertTrue(homeScreen.contains("当前账号"))
        assertTrue(homeScreen.contains("三角洲角色"))
        assertTrue(homeScreen.contains("退出重新登录"))
        assertTrue(!homeScreen.contains("\"攻略\""))
        assertTrue(!homeScreen.contains("Icons.Default.MoreVert"))
        assertTrue(!homeScreen.contains("DropdownMenu"))
        assertTrue(!homeScreen.contains("https://"))
        assertTrue(!homeScreen.contains("comm.ams.game.qq.com"))
    }

    @Test
    fun homeExperienceDoesNotExposeDeveloperTerminologyOrRawCredentialLabels() {
        val homeScreen = source("ui/home/HomeScreen.kt")
        val homeViewModel = source("ui/home/HomeViewModel.kt")
        val forbiddenHomeTerms = listOf(
            "本地后端",
            "小程序接口",
            "/api",
            "dfm/",
            "接口",
            "数据来源",
            "写接口",
            "AMS",
        )

        forbiddenHomeTerms.forEach { term ->
            assertTrue("HomeScreen should not expose '$term'", !homeScreen.contains(term))
        }
        assertTrue(!homeViewModel.contains("OpenID"))
        assertTrue(!homeViewModel.contains("AppID"))
    }

    @Test
    fun playerFacingSettingsAndPrivacyCopyAvoidDeveloperTerminology() {
        val settingsScreen = source("ui/settings/SettingsScreen.kt")
        val privacyScreen = source("ui/privacy/PrivacyScreen.kt")
        val forbiddenTerms = listOf(
            "AMS",
            "接口",
            "Cookie",
            "token",
            "OpenID",
            "AppID",
            "域名",
            "WorkManager",
            "/api",
            "dfm/",
        )

        forbiddenTerms.forEach { term ->
            assertTrue("SettingsScreen should not expose '$term'", !settingsScreen.contains(term))
            assertTrue("PrivacyScreen should not expose '$term'", !privacyScreen.contains(term))
        }
    }

    private fun source(relative: String): String {
        val path = "src/main/java/com/local/dfcraftmonitor/$relative"
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.first { it.isFile }.readText()
    }
}
