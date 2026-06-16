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
import com.local.dfcraftmonitor.ui.theme.DfCraftingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 单 Activity 入口。NavHost 串起 Login ↔ Home。
 *
 * 起点判断：已登录（SessionHolder 有 credential）→ Home，否则 → Login。
 * M3 不持久化会话（App 重启回 Login），M4 改为读 DataStore。
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
}

@androidx.compose.runtime.Composable
private fun DfNavGraph(sessionHolder: SessionHolder) {
    val navController = rememberNavController()
    val startRoute = if (sessionHolder.isLoggedIn()) Routes.HOME else Routes.LOGIN

    NavHost(navController = navController, startDestination = startRoute) {
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
            HomeScreen()
        }
    }
}
