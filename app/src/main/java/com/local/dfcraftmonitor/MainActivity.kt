package com.local.dfcraftmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边布局，让 TopAppBar/NavigationBar 的 windowInsets 接管状态栏/导航栏
        // 强制深色：状态栏图标浅色（夜间作战终端），不受系统浅色/深色模式切换影响
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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
                    DfNavGraph(sessionHolder)
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
private fun DfNavGraph(sessionHolder: SessionHolder) {
    val navController = rememberNavController()

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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
