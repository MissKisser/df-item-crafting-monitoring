package com.local.dfcraftmonitor.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.BuildConfig

/**
 * 登录页。支持首次登录和新增账号两种模式。
 *
 * - 首次登录：标题"登录"，无返回按钮，直接挂载 WebView
 * - 新增账号：标题"新增账号"，有返回按钮；进入页面先清空 WebView Cookie（防止自动登录），
 *   清空完成后再挂载 WebView，顶部横幅提示"请扫码登录新账号"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    isAddingAccount: Boolean = false,
    onBack: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    LaunchedEffect(isAddingAccount) {
        viewModel.isAddingAccount = isAddingAccount
        if (isAddingAccount) {
            viewModel.prepareFreshLogin()
        } else {
            // 非 addMode 不需要清 Cookie，直接就绪
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val freshLoginReady by viewModel.freshLoginReady.collectAsStateWithLifecycle()
    var refreshSignal by remember { mutableIntStateOf(0) }
    var selectedLoginMethod by remember { mutableStateOf(LoginMethod.QQ) }

    LaunchedEffect(state) {
        if (state is LoginViewModel.UiState.LoggedIn) {
            onLoggedIn()
        }
    }

    val showWebView = !isAddingAccount || freshLoginReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAddingAccount) "新增账号" else "登录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
                navigationIcon = {
                    if (isAddingAccount && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                            )
                        }
                    }
                },
                actions = {
                    if (showWebView) {
                        IconButton(onClick = { refreshSignal++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新登录页面")
                        }
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
            if (isAddingAccount) {
                Text(
                    text = "请扫码登录新账号（已自动登出当前 WebView 登录态）",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1F2937))
                        .padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = when (val s = state) {
                    LoginViewModel.UiState.LoggingIn -> "正在加载登录页面，请稍候…"
                    LoginViewModel.UiState.LoggedIn -> "登录成功，正在跳转…"
                    is LoginViewModel.UiState.Failed -> "登录失败：${s.reason}"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(16.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            LoginMethodSelector(
                selectedLoginMethod = selectedLoginMethod,
                onLoginMethodSelected = { selectedLoginMethod = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (showWebView) {
                LoginWebView(
                    initialUrl = loginUrlFor(selectedLoginMethod),
                    loginMethod = selectedLoginMethod,
                    refreshSignal = refreshSignal,
                    onLoginSuccess = viewModel::onCookiesHarvested,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "正在准备扫码登录…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private fun loginUrlFor(loginMethod: LoginMethod): String =
    when (loginMethod) {
        LoginMethod.QQ -> LEGACY_QQ_LOGIN_URL
        LoginMethod.WECHAT -> BuildConfig.DF_WEB_LOGIN_URL
    }

private const val LEGACY_QQ_LOGIN_URL = "https://pvp.qq.com/cp/a20161115tyf/page1.shtml"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginMethodSelector(
    selectedLoginMethod: LoginMethod,
    onLoginMethodSelected: (LoginMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val methods = listOf(
        LoginMethod.QQ to "QQ登录",
        LoginMethod.WECHAT to "微信登录",
    )

    SingleChoiceSegmentedButtonRow(
        modifier = modifier,
    ) {
        methods.forEachIndexed { index, (method, label) ->
            SegmentedButton(
                selected = method == selectedLoginMethod,
                onClick = { onLoginMethodSelected(method) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = methods.size,
                ),
                label = { Text(label) },
            )
        }
    }
}
