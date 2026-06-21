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
        assertTrue(homeScreen.contains("\"战绩\""))
        assertTrue(homeScreen.contains("\"我的\""))
        assertTrue(homeScreen.contains("昵称"))
        assertTrue(homeScreen.contains("当前区"))
        // 任务3/5：移除了"三角洲角色"行与"退出重新登录"按钮（退出登录收入设置页）
        assertTrue(!homeScreen.contains("三角洲角色"))
        assertTrue(!homeScreen.contains("退出重新登录"))
        // 任务5：设置入口上移到顶栏
        assertTrue(homeScreen.contains("Icons.Default.Settings"))
        assertTrue(!homeScreen.contains("\"攻略\""))
        assertTrue(!homeScreen.contains("\"工具\""))
        assertTrue(!homeScreen.contains("\"周报\""))
        assertTrue(!homeScreen.contains("\"福利\""))
        assertTrue(!homeScreen.contains("\"最新行情\""))
        assertTrue(!homeScreen.contains("Icons.Default.MoreVert"))
        // 赛季下拉筛选使用 DropdownMenuItem，但不应有溢出菜单 MoreVert
        assertTrue(!homeScreen.contains("https://"))
        assertTrue(!homeScreen.contains("comm.ams.game.qq.com"))
    }

    @Test
    fun homeBattleAndMineSurfacesExposeRequestedMiniProgramSections() {
        val homeScreen = source("ui/home/HomeScreen.kt")

        listOf(
            "昨日收益",
            "带出收藏品",
            "制造推荐",
            "最近对局",
            "净收益",
            "战绩详情",
            "头像框",
            "昵称",
            "当前区",
            "当前段位",
            "最高段位",
            "烽火地带带出总价值",
            "撤离率",
            "击败干员",
            "赚损比",
            "大红藏馆",
            "出红记录",
        ).forEach { term ->
            assertTrue("HomeScreen should contain '$term'", homeScreen.contains(term))
        }
        assertTrue(homeScreen.contains("manufacturingRecommendations"))
        // 制作推荐与三角洲小程序对齐：展示"每小时利润"（profitPerHour）而非旧的"净利润"
        assertTrue(homeScreen.contains("每小时利润"))
        assertTrue(homeScreen.contains("profitPerHour"))
        assertTrue(!homeScreen.contains("toolObjects.take(3)"))
        assertTrue(!homeScreen.contains("maps.map { it.name } + matches.map"))
        assertTrue(!homeScreen.contains("maps.map { it.name } + records.map"))
        assertTrue(!homeScreen.contains("当前账号"))
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
    fun loginCookieHarvesterIncludesPvpDomainForLiveAmsCredential() {
        val cookieHarvester = source("ui/login/CookieHarvester.kt")

        assertTrue(cookieHarvester.contains("https://pvp.qq.com/"))
        assertTrue(cookieHarvester.contains("cookies[\"pvp.qq.com\"]"))
    }

    @Test
    fun loginCookieHarvesterIncludesOfficialWebMiniProgramDomains() {
        val cookieHarvester = source("ui/login/CookieHarvester.kt")

        assertTrue(cookieHarvester.contains("https://df.qq.com/"))
        assertTrue(cookieHarvester.contains("https://iu.qq.com/"))
        assertTrue(cookieHarvester.contains("https://milo.qq.com/"))
        assertTrue(cookieHarvester.contains("cookies[\"df.qq.com\"]"))
        assertTrue(cookieHarvester.contains("cookies[\"iu.qq.com\"]"))
    }

    @Test
    fun amsRequestsUseOfficialWebMiniProgramReferer() {
        val interceptor = source("data/remote/AmsHeadersInterceptor.kt")

        assertTrue(interceptor.contains("\"https://df.qq.com/cp/a20241230webmp/index.html\""))
        assertTrue(interceptor.contains("\"https://df.qq.com\""))
    }

    @Test
    fun harvestedCredentialIsPersistedInAppPrivateStorageAndClearedOnLogout() {
        val sessionHolder = source("ui/login/SessionHolder.kt")
        val accountStore = source("data/account/AccountStore.kt")

        assertTrue(sessionHolder.contains("AccountStore"))
        assertTrue(sessionHolder.contains("accountStore"))
        assertTrue(accountStore.contains("getSharedPreferences"))
        assertTrue(accountStore.contains("MODE_PRIVATE"))
        assertTrue(!accountStore.contains("LocalBackendCatalog"))
    }

    @Test
    fun priceDialogDoesNotUseHardcodedTrendPoints() {
        val homeScreen = source("ui/home/HomeScreen.kt")

        assertTrue(!homeScreen.contains("\"6小时前\""))
        assertTrue(!homeScreen.contains("\"3小时前\""))
        assertTrue(!homeScreen.contains("\"41.2万\""))
        assertTrue(!homeScreen.contains("\"43.8万\""))
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
