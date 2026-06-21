package com.local.dfcraftmonitor.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.BuildConfig
import com.local.dfcraftmonitor.ui.theme.SemanticColors

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAddingAccount) "新增账号" else "登录",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = {
                    if (showWebView) {
                        IconButton(onClick = { refreshSignal++ }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新登录页面",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
                windowInsets = WindowInsets.statusBars,
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
                AddAccountBanner()
            }

            LoginMethodSelector(
                selectedLoginMethod = selectedLoginMethod,
                onLoginMethodSelected = { selectedLoginMethod = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            when (val s = state) {
                LoginViewModel.UiState.LoggingIn -> StatusStrip("正在加载登录页面，请稍候…")
                LoginViewModel.UiState.LoggedIn -> StatusStrip("登录成功，正在跳转…")
                is LoginViewModel.UiState.Failed -> StatusStrip("登录失败：${s.reason}", isError = true)
            }

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
                        text = "正在准备扫码登录…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddAccountBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "请扫码登录新账号（已自动登出当前 WebView 登录态）",
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusStrip(text: String, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isError) SemanticColors.loss.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            color = if (isError) SemanticColors.loss else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
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

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        methods.forEachIndexed { index, (method, label) ->
            SegmentedButton(
                selected = method == selectedLoginMethod,
                onClick = { onLoginMethodSelected(method) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = methods.size,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                label = { Text(text = label) },
            )
        }
    }
}
