package com.local.dfcraftmonitor.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.ui.permission.NotificationPermissionState
import com.local.dfcraftmonitor.ui.permission.NotificationPermissionStatus

/**
 * 三角洲助手首页：今日战报 / 战绩 / 我的。
 *
 * 视觉：Material 3 Expressive（动态取色 + 大点击区 + 弹性指示器）
 * 性能：
 *  - Tab 切换时仅当内容真正变化才重组（用 `key(...)`）
 *  - 子 Tab 已拆为独立文件（HomeTab/BattleTab/MineTab），单文件不超过 250 行
 *
 * 顶部 AppBar 由 [com.local.dfcraftmonitor.MainActivity] 统一挂载（spec "全局刷新"），
 * 因此本组件只负责底部 NavigationBar + Tab 内容。
 */
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val accountInfo = viewModel.accountMenuInfo()
    val daySecrets = dashboard.daySecrets
    val yesterdayIncome = dashboard.yesterdayIncome
    val collections = dashboard.collections
    val recentMatches = dashboard.recentMatches
    val profile = dashboard.profile
    val redArchive = dashboard.redArchive
    val manufacturingRecommendations = dashboard.manufacturingRecommendations

    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }

    var permissionStatus by remember {
        mutableStateOf(NotificationPermissionState.check(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionStatus = NotificationPermissionState.check(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permissionStatus != NotificationPermissionStatus.GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { HomeBottomBar(selected = selectedTab, onSelected = { selectedTab = it }) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (selectedTab) {
                MainTab.HOME -> HomeTab(
                    state = state,
                    daySecrets = daySecrets,
                    yesterdayIncome = yesterdayIncome,
                    collections = collections,
                    recommendations = manufacturingRecommendations,
                    permissionStatus = permissionStatus,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onReLogin = { viewModel.reLogin(onLogout) },
                )
                MainTab.BATTLE -> BattleTab(matches = recentMatches)
                MainTab.MINE -> MineTab(
                    accountInfo = accountInfo,
                    state = state,
                    profile = profile,
                    redArchive = redArchive,
                    selectedSeason = viewModel.selectedSeason.collectAsStateWithLifecycle().value,
                    seasonLoading = viewModel.seasonLoading.collectAsStateWithLifecycle().value,
                    onSeasonSelected = viewModel::switchSeason,
                    onNavigateToSettings = onNavigateToSettings,
                    onLogout = { viewModel.logoutCurrent(onLogout) },
                )
            }
        }
    }
}

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Default.Home),
    BATTLE("战绩", Icons.AutoMirrored.Filled.List),
    MINE("我的", Icons.Default.Person),
}

@Composable
private fun HomeBottomBar(
    selected: MainTab,
    onSelected: (MainTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = { Text(text = tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
