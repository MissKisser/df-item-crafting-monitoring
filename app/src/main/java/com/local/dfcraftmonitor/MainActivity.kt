package com.local.dfcraftmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.local.dfcraftmonitor.ui.home.HomeScreen
import com.local.dfcraftmonitor.ui.login.LoginScreen
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.ui.privacy.PrivacyScreen
import com.local.dfcraftmonitor.ui.settings.SettingsScreen
import com.local.dfcraftmonitor.ui.theme.DfCraftingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 单 Activity 入口。NavHost 串起 Login ↔ Home。
 *
 * V2 起始终进入主界面：无登录也可以浏览公开/本地后端降级数据，需要账号数据时再进入 Login。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionHolder: SessionHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DfCraftingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DfNavGraph(sessionHolder)
                }
            }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
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
