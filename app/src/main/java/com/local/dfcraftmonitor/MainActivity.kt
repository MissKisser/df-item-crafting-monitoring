package com.local.dfcraftmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
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
import com.local.dfcraftmonitor.ui.settings.SettingsScreen
import com.local.dfcraftmonitor.ui.theme.DfCraftingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionHolder: SessionHolder
    @Inject lateinit var globalRefreshController: GlobalRefreshController

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
        setContent {
            // 全局深色：禁用 Monet 动态取色，固定使用品牌深色调色板
            DfCraftingTheme(
                darkTheme = true,
                dynamicColor = false,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DfNavGraph(sessionHolder, globalRefreshController)
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
}

@androidx.compose.runtime.Composable
private fun DfNavGraph(
    sessionHolder: SessionHolder,
    globalRefreshController: GlobalRefreshController,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val refreshState by globalRefreshController.state.collectAsStateWithLifecycle()

    // 仅在 HOME 路由显示 Settings 入口（spec "设置入口上移到顶栏"）。
    // 其他路由（Settings/Privacy/Login）由 GlobalTopBar 的 navigationIcon 提供返回按钮。
    val onSettingsClick: (() -> Unit)? = if (currentRoute == Routes.HOME) {
        { navController.navigate(Routes.SETTINGS) }
    } else null
    val onNavigationClick: (() -> Unit)? = if (currentRoute != Routes.HOME) {
        { navController.popBackStack() }
    } else null

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
                    LoginScreen(
                        onLoggedIn = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                        isAddingAccount = false,
                    )
                }
                composable(
                    route = Routes.LOGIN_ADD,
                ) {
                    LoginScreen(
                        onLoggedIn = {
                            navController.popBackStack(Routes.SETTINGS, false)
                        },
                        isAddingAccount = true,
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToSettings = {
                            navController.navigate(Routes.SETTINGS)
                        },
                        onLogout = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
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
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
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
    Routes.LOGIN, Routes.LOGIN_ADD -> "账号绑定"
    else -> "三角洲助手"
}
