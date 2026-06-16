package com.local.dfcraftmonitor.ui.home

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import kotlinx.coroutines.delay

/**
 * 主界面：账号状态 + 工位卡片列表 + 倒计时 + 下拉刷新（按钮形式）。
 *
 * 倒计时策略：每秒 tick 一次，本地重新计算 "剩余 N 秒"（基于 snapshot 内
 * 的服务器时间，不依赖客户端时间，避免本机时钟漂移影响显示）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("特勤处监控") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                HomeViewModel.UiState.Loading -> Center { CircularProgressIndicator() }
                HomeViewModel.UiState.NotLoggedIn -> Center { Text("未登录") }
                is HomeViewModel.UiState.Error -> Center { Text("错误：${s.message}") }
                is HomeViewModel.UiState.Success -> SnapshotContent(s.snapshot)
            }
        }
    }
}

@Composable
private fun SnapshotContent(snapshot: CraftingSnapshot) {
    // 每秒 tick：本机当前时间，用于倒计时显示。
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
    // 服务器时间到本机时间的偏移：本机 nowMillis 对应 serverNowSeconds
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
