package com.local.dfcraftmonitor.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.ui.permission.NotificationPermissionState
import com.local.dfcraftmonitor.ui.permission.NotificationPermissionStatus
import kotlinx.coroutines.delay

/**
 * 主界面：账号状态 + 工位卡片列表 + 倒计时 + 下拉刷新 + 通知权限 Banner。
 *
 * spec M2 增强：
 * - 通知权限被拒时显示 Banner 提示
 * - 登录失效（AuthExpired）时显示"重新登录"按钮
 * - 进入时申请通知权限（Android 13+）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accountInfo = viewModel.accountMenuInfo()
    val context = LocalContext.current

    // 通知权限状态：进入页面时查一次，权限申请后回调时再查一次
    var permissionStatus by remember {
        mutableStateOf(NotificationPermissionState.check(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        permissionStatus = NotificationPermissionState.check(context)
    }

    // Android 13+ 且未授权时，进入页面就申请一次
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permissionStatus != NotificationPermissionStatus.GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            var menuExpanded by remember { mutableStateOf(false) }
            TopAppBar(
                title = { Text("特勤处监控") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(
                                    "当前账号",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    accountInfo.account,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Text(
                                    "AppID ${accountInfo.appId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Text(
                                    "三角洲角色",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    accountInfo.deltaRole,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("退出重新登录") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.clearDataAndLogout(onLogout)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 通知权限 Banner：被拒时显示（spec 11.1 "通知权限关闭"）
            if (permissionStatus == NotificationPermissionStatus.DENIED ||
                permissionStatus == NotificationPermissionStatus.PERMANENTLY_DENIED
            ) {
                NotificationPermissionBanner(
                    onRequestAgain = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    HomeViewModel.UiState.Loading -> Center { CircularProgressIndicator() }
                    HomeViewModel.UiState.NotLoggedIn -> Center { Text("未登录") }
                    is HomeViewModel.UiState.Error -> Center { Text("错误：${s.message}") }
                    is HomeViewModel.UiState.AuthExpired -> AuthExpiredPanel(
                        reason = s.reason,
                        onReLogin = { viewModel.clearDataAndLogout(onLogout) },
                    )
                    is HomeViewModel.UiState.Success -> SnapshotContent(s.snapshot)
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(onRequestAgain: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "通知权限未开启",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "将接收不到制造完成提醒。后台同步仍在运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onRequestAgain,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("重新申请")
            }
        }
    }
}

@Composable
private fun AuthExpiredPanel(reason: String, onReLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "登录已失效",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onReLogin) {
            Text("重新登录")
        }
    }
}

@Composable
private fun SnapshotContent(snapshot: CraftingSnapshot) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snapshot.serverNowEpochSeconds) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "服务器时间：${snapshot.serverNowEpochSeconds} · 工位 ${snapshot.stations.size}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(snapshot.stations, key = { "${it.type}-${it.placeName}" }) { station ->
            StationCard(station, snapshot.serverNowEpochSeconds, nowMillis)
        }
    }
}

@Composable
private fun StationCard(
    station: CraftingStation,
    serverNowSeconds: Long,
    nowMillis: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(station.placeName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatRemaining(station, serverNowSeconds, nowMillis),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = station.itemName ?: "(无物品名)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "完成 ${formatEpoch(station.finishAtEpochSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun formatRemaining(
    station: CraftingStation,
    serverNowSeconds: Long,
    nowMillis: Long,
): String {
    val finishSec = station.finishAtEpochSeconds ?: return "-"
    val serverNowMillis = serverNowSeconds * 1000L
    val offset = nowMillis - serverNowMillis
    val remaining = (finishSec * 1000L - (serverNowMillis + offset)) / 1000L
    return when {
        remaining <= 0 -> "已完成"
        else -> "剩余 ${formatDuration(remaining)}"
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}时${m}分${s}秒" else "${m}分${s}秒"
}

private fun formatEpoch(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "-"
    val date = java.util.Date(epochSeconds * 1000L)
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA)
    return fmt.format(date)
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
