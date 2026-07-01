package com.local.dfcraftmonitor.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeActionsSourceTest {

    @Test
    fun loginScreenReloadsWebViewOnLoginMethodSwitch() {
        val loginScreen = source("ui/login/LoginScreen.kt")
        val loginWebView = source("ui/login/LoginWebView.kt")

        // Bug 1 修复：删除了 LoginScreen 内的"刷新登录页面" IconButton + LoginWebView 的
        // refreshSignal 形参/状态/更新分支。LoginScreen 现在只渲染 WebView，
        // 刷新走 GlobalTopBar 右上角按钮。WebView 的 update 块仍保留：QQ ↔ 微信切换时
        // 重载 URL；初次进入仍由 factory 块 loadUrl。
        assertTrue(
            "LoginScreen should NOT contain a local Refresh icon anymore",
            !loginScreen.contains("Icons.Outlined.Refresh"),
        )
        assertTrue(
            "LoginScreen should NOT reference refreshSignal anymore",
            !loginScreen.contains("refreshSignal"),
        )
        assertTrue(
            "LoginWebView should NOT declare refreshSignal parameter anymore",
            !loginWebView.contains("refreshSignal:"),
        )
        // 验证 WebView 加载行为：factory 块首次 loadUrl + update 块在 loginMethod 切换时 loadUrl。
        assertTrue(loginWebView.contains("webView.loadUrl(initialUrl)"))
        assertTrue(
            "LoginWebView update should trigger on loginMethod change",
            loginWebView.contains("lastLoginMethod != loginMethod"),
        )
    }

    @Test
    fun homeScreenUsesV2MiniProgramTabsWithoutStrategyOrOverflowMenu() {
        val homeScreen = source("ui/home/HomeScreen.kt")
        val globalTopBar = source("ui/common/GlobalTopBar.kt")

        assertTrue(homeScreen.contains("NavigationBarItem"))
        assertTrue(homeScreen.contains("\"首页\""))
        assertTrue(homeScreen.contains("\"战绩\""))
        assertTrue(homeScreen.contains("\"我的\""))
        // 任务3/5：移除了"三角洲角色"行与"退出重新登录"按钮（退出登录收入设置页）
        assertTrue(!homeScreen.contains("三角洲角色"))
        assertTrue(!homeScreen.contains("退出重新登录"))
        // 任务5：设置入口上移到全局 TopBar（spec "全局刷新"）。
        // 仅在 HOME 路由显示，由 GlobalTopBar 渲染，HomeScreen 不再画。
        assertTrue(globalTopBar.contains("Icons.Outlined.Settings"))
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
        // 字段字符串分布在 HomeTab / BattleTab / MineTab 三个子 Composable 里
        // （HomeScreen 只负责 Tab 容器，不再持有字段字面量）。
        // 扫描 ui/home 目录下除 ViewModel 外的所有用户可见 Composable 文件
        // —— 不含 ViewModel 是因为里面的"当前账号"等是开发者注释，不属于用户文案。
        val homeDir = File("src/main/java/com/local/dfcraftmonitor/ui/home")
        val homeFiles = homeDir.listFiles { f -> f.extension == "kt" && !f.name.endsWith("ViewModel.kt") }
        checkNotNull(homeFiles) { "ui/home 目录不存在或不可读" }
        val homeSources = homeFiles.joinToString("\n") { it.readText() }

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
            assertTrue("home/*.kt should contain '$term'", homeSources.contains(term))
        }
        assertTrue(homeSources.contains("manufacturingRecommendations"))
        // 制作推荐与三角洲小程序对齐：展示"每小时利润"（profitPerHour）而非旧的"净利润"
        assertTrue(homeSources.contains("每小时利润"))
        assertTrue(homeSources.contains("profitPerHour"))
        assertTrue(!homeSources.contains("toolObjects.take(3)"))
        assertTrue(!homeSources.contains("maps.map { it.name } + matches.map"))
        assertTrue(!homeSources.contains("maps.map { it.name } + records.map"))
        assertTrue(!homeSources.contains("当前账号"))
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
        // Settings 页面：完全禁止开发术语（用户能直接看到设置项）
        val settingsForbidden = listOf(
            "AMS",
            "本地接口",
            "小程序接口",
            "/api",
            "dfm/",
            "token",
            "OpenID",
            "AppID",
            "域名",
            "WorkManager",
        )
        settingsForbidden.forEach { term ->
            assertTrue("SettingsScreen should not expose '$term'", !settingsScreen.contains(term))
        }
        // Privacy 页面：允许"Cookie"（用户必须知道本机存了什么）。
        // 但 AMS / 接口类型 / token 仍要禁。
        val privacyForbidden = listOf(
            "AMS",
            "本地接口",
            "小程序接口",
            "/api",
            "dfm/",
            "token",
            "OpenID",
            "AppID",
            "域名",
            "WorkManager",
        )
        privacyForbidden.forEach { term ->
            assertTrue("PrivacyScreen should not expose '$term'", !privacyScreen.contains(term))
        }
    }

    private fun source(relative: String): String {
        val path = "src/main/java/com/local/dfcraftmonitor/$relative"
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.first { it.isFile }.readText()
    }
}
