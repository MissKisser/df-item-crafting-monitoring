package com.local.dfcraftmonitor.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.BuildConfig

/**
 * 登录页。顶部状态条 + 内嵌 WebView。
 *
 * 改进：
 * - 进入页面后自动弹出二维码（JS 拦截 iframe + 自动点击登录按钮）
 * - 二维码自动放大至屏幕宽度（不超出屏幕边缘）
 * - 登录成功后自动跳转（Cookie 轮询检测）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var refreshSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        if (state is LoginViewModel.UiState.LoggedIn) {
            onLoggedIn()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = { refreshSignal++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新登录页面")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Text(
                text = when (val s = state) {
                    LoginViewModel.UiState.LoggingIn -> "正在加载登录页面，请稍候…"
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
                refreshSignal = refreshSignal,
                onLoginSuccess = viewModel::onCookiesHarvested,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
