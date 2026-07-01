package com.local.dfcraftmonitor

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.dfcraftmonitor.data.monitor.GlobalRefreshController
import com.local.dfcraftmonitor.ui.common.GlobalTopBar
import com.local.dfcraftmonitor.ui.home.HomeScreen
import com.local.dfcraftmonitor.ui.login.LoginScreen
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.ui.privacy.PrivacyScreen
import com.local.dfcraftmonitor.ui.settings.DaySecretMapPickerScreen
import com.local.dfcraftmonitor.ui.settings.SettingsScreen
import com.local.dfcraftmonitor.ui.theme.DfCraftingTheme
import com.local.dfcraftmonitor.widget.LocalWidgetConfigureBridge
import com.local.dfcraftmonitor.widget.WidgetConfigureBridge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionHolder: SessionHolder
    @Inject lateinit var globalRefreshController: GlobalRefreshController

    /**
     * 等待被 widget configure 流程消费的 widgetId。
     *
     * 非空时表示当前 Activity 实例是从桌面拖完 widget 后被 Launcher 用
     * `APPWIDGET_CONFIGURE` intent 拉起的；此时我们要把 Navigation 引导到
     * `Routes.DAY_SECRET_PICKER`，并通过 [LocalWidgetConfigureBridge] 把 widgetId
     * 暴露给 picker 屏幕，让它保存后能 `setResult(OK) + finish()`。
     *
     * 用 mutableStateOf 是为了让 [DfNavGraph] 的 LaunchedEffect 能直接 observe 触发
     * 导航；onNewIntent 收到新 intent 时 setValue 即可重入。
     */
    private var pendingWidgetId by mutableStateOf<Int?>(null)

    /**
     * 解析 widget configure intent。Launcher 拖完 widget 后会用
     * `Intent { action=APPWIDGET_CONFIGURE, extras={EXTRA_APPWIDGET_ID=<id>...} }`
     * 启动本 Activity；其它入口（launcher 图标、deep link 等）action 通常是 ACTION_MAIN
     * 或 null —— 这些路径都不应触发 widget 配置上下文。
     */
    private fun consumeWidgetConfigureIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) return
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        pendingWidgetId = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) null else id
    }

    /**
     * 处理 launchMode=singleTop/singleTask 时 Launcher 把新 intent 转给已存活的 Activity
     * 实例的场景（部分 launcher 实现）。需要重新解析以触发导航。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWidgetConfigureIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边布局，让 TopAppBar/NavigationBar 的 windowInsets 接管状态栏/导航栏
        // 强制深色：状态栏图标浅色（夜间作战终端），不受系统浅色/深色模式切换影响
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // 冷启动即触发一次同步：已登录用户进入 App 立刻看到最新数据，
        // 同时启动 WorkManager 周期任务（解决"App 启动后周期同步没起来"的问题）。
        if (sessionHolder.get() != null) {
            globalRefreshController.refreshAsync()
        }
        consumeWidgetConfigureIntent(intent)
        setContent {
            // 全局深色：禁用 Monet 动态取色，固定使用品牌深色调色板
            DfCraftingTheme(
                darkTheme = true,
                dynamicColor = false,
            ) {
                // widget configure 上下文：把 widgetId 包成 Bridge 注入。
                // Picker 屏幕读到非空 Bridge 时保存走 bridge.onComplete()；
                // 未启动自桌面拖放时 Bridge 为 null，行为退化为普通导航。
                val widgetId = pendingWidgetId
                val bridge = remember(widgetId) {
                    if (widgetId == null) null else WidgetConfigureBridge(
                        widgetId = widgetId,
                        onComplete = {
                            val result = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            }
                            setResult(RESULT_OK, result)
                            pendingWidgetId = null
                            finish()
                        },
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            pendingWidgetId = null
                            finish()
                        },
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CompositionLocalProvider(LocalWidgetConfigureBridge provides bridge) {
                        DfNavGraph(
                            globalRefreshController = globalRefreshController,
                            widgetConfigureBridge = bridge,
                        )
                    }
                }
            }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val LOGIN_ADD = "login?addMode=true"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DAY_SECRET_PICKER = "day_secret_picker"
}

@androidx.compose.runtime.Composable
private fun DfNavGraph(
    globalRefreshController: GlobalRefreshController,
    widgetConfigureBridge: WidgetConfigureBridge?,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val refreshState by globalRefreshController.state.collectAsStateWithLifecycle()

    // Widget configure 上下文：Activity 是被 Launcher 拖完 widget 拉起的，
    // 必须把导航引导到 picker 屏幕，并在用户取消（返回键 / 直接退出）时
    // 由 Bridge 通知 Launcher RESULT_CANCELED —— 否则 Launcher 会一直等 OK。
    if (widgetConfigureBridge != null) {
        LaunchedEffect(widgetConfigureBridge.widgetId) {
            navController.navigate(Routes.DAY_SECRET_PICKER) {
                // 从 HOME 跳过去，不留返回栈（用户取消就直接 finish Activity，
                // 不能再 popBack 回 HOME）。
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
        // 拦截系统返回：取消时 RESULT_CANCELED + finish。
        // 注意：BackHandler 必须放在 NavHost 之前注册才能抢在 NavController 之前消费。
        BackHandler(enabled = currentRoute == Routes.DAY_SECRET_PICKER) {
            widgetConfigureBridge.onCancel()
        }
    }

    // 仅在 HOME 路由显示 Settings 入口（spec "设置入口上移到顶栏"）。
    // 其他路由（Settings/Privacy/Login）由 GlobalTopBar 的 navigationIcon 提供返回按钮。
    val onSettingsClick: (() -> Unit)? = if (currentRoute == Routes.HOME) {
        { navController.navigate(Routes.SETTINGS) }
    } else null
    // Widget configure 上下文中：顶栏返回按钮也是"取消配置"语义，而不是 popBackStack
    // （回不去 HOME —— NavHost 启动跳到 picker 时已经把 HOME 留在栈底，再 pop 会
    // 让用户看到短暂空白）。统一走 bridge.onCancel()。
    val onNavigationClick: (() -> Unit)? = run {
        if (widgetConfigureBridge != null && currentRoute == Routes.DAY_SECRET_PICKER) {
            { widgetConfigureBridge.onCancel() }
        } else if (currentRoute != Routes.HOME) {
            { navController.popBackStack() }
        } else {
            null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlobalTopBar(
                title = titleFor(currentRoute),
                refreshState = refreshState,
                onRefresh = { globalRefreshController.refreshAsync() },
                onSettingsClick = onSettingsClick,
                navigationIcon = onNavigationClick?.let { Icons.AutoMirrored.Filled.ArrowBack },
                onNavigationClick = onNavigationClick,
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.LOGIN) {
                    // 跳转由上方 LaunchedEffect（会话状态驱动）接管，LoginScreen 不再
                    // 回调 onLoggedIn。
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        isAddingAccount = false,
                    )
                }
                composable(
                    route = Routes.LOGIN_ADD,
                ) {
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        isAddingAccount = true,
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToSettings = {
                            navController.navigate(Routes.SETTINGS)
                        },
                        onLogout = {
                            // Bug 2 修复：从 LockedPanel 进入登录页时，保留 HOME 在
                            // 栈底，让 BackHandler 触发的 popBackStack 能回到 HOME
                            // （而不是把整个 Activity 弹掉）。
                            navController.navigate(Routes.LOGIN) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onNavigateToPrivacy = {
                            navController.navigate(Routes.PRIVACY)
                        },
                        onAddAccount = {
                            navController.navigate(Routes.LOGIN_ADD)
                        },
                        onLogout = {
                            // Bug 2 修复：保留 HOME/SETTINGS 在栈底，让 BackHandler
                            // 触发的 popBackStack 能回到 Settings 的栈位置（而不是
                            // Activity finish 到桌面）。
                            navController.navigate(Routes.LOGIN) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToDaySecretPicker = {
                            navController.navigate(Routes.DAY_SECRET_PICKER)
                        },
                    )
                }
                composable(Routes.DAY_SECRET_PICKER) {
                    DaySecretMapPickerScreen(
                        title = "今日密码 桌面卡",
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(Routes.PRIVACY) {
                    PrivacyScreen()
                }
            }
        }
    }
}

private fun titleFor(route: String): String = when (route) {
    Routes.HOME -> "三角洲助手"
    Routes.SETTINGS -> "设置"
    Routes.PRIVACY -> "隐私声明"
    Routes.DAY_SECRET_PICKER -> "今日密码配置"
    Routes.LOGIN, Routes.LOGIN_ADD -> "账号绑定"
    else -> "三角洲助手"
}
