package com.local.dfcraftmonitor.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.BuildConfig

/**
 * 登录页。顶部状态条 + 内嵌 WebView（M3-5 的 LoginWebView Composable）。
 *
 * 登录成功（UiState.LoggedIn）由上层 NavHost 监听以决定跳转。
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is LoginViewModel.UiState.LoggedIn) {
            onLoggedIn()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = when (val s = state) {
                LoginViewModel.UiState.LoggingIn -> "请在下方扫码登录"
                LoginViewModel.UiState.LoggedIn -> "登录成功，正在跳转…"
                is LoginViewModel.UiState.Failed -> "登录失败：${s.reason}"
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1F2937))
                .padding(16.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )

        LoginWebView(
            initialUrl = BuildConfig.DF_WEB_LOGIN_URL,
            onLoginSuccess = viewModel::onCookiesHarvested,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
