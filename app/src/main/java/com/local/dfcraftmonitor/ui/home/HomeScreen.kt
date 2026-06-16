package com.local.dfcraftmonitor.ui.home

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.data.backend.DaySecret
import com.local.dfcraftmonitor.data.backend.MapSummary
import com.local.dfcraftmonitor.data.backend.ToolObjectSummary
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.ui.permission.NotificationPermissionState
import com.local.dfcraftmonitor.ui.permission.NotificationPermissionStatus
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val DfBackground = Color(0xFF070A0B)
private val DfPanel = Color(0xFF0D1312)
private val DfPanelAlt = Color(0xFF14201D)
private val DfLine = Color(0xFF25463C)
private val DfGreen = Color(0xFF0FF796)
private val DfAmber = Color(0xFFE7C75B)
private val DfRed = Color(0xFFE55D5D)
private val DfMuted = Color(0xFF9AA6A1)
private val DfFaint = Color(0xFF5D6B65)
private val DfWhite = Color(0xFFEAF2EE)

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Default.Home),
    TOOLS("工具", Icons.Default.Build),
    MINE("我的", Icons.Default.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val accountInfo = viewModel.accountMenuInfo()
    val toolCategories = dashboard.toolCategories
    val toolObjects = dashboard.toolObjects
    val daySecrets = dashboard.daySecrets
    val maps = dashboard.maps
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
        containerColor = DfBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("三角洲行动", fontWeight = FontWeight.SemiBold)
                        Text(
                            "行动情报终端",
                            style = MaterialTheme.typography.labelSmall,
                            color = DfMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DfBackground,
                    titleContentColor = Color.White,
                    actionIconContentColor = DfGreen,
                ),
                actions = {
                    if (selectedTab == MainTab.HOME) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B100F)) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DfGreen,
                            selectedTextColor = DfGreen,
                            indicatorColor = DfPanelAlt,
                            unselectedIconColor = DfMuted,
                            unselectedTextColor = DfMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DfBackground),
        ) {
            when (selectedTab) {
                MainTab.HOME -> HomeTab(
                    state = state,
                    accountInfo = accountInfo,
                    daySecrets = daySecrets,
                    marketObjects = toolObjects.take(3),
                    bannerImageUrl = dashboard.homeBannerImageUrl,
                    permissionStatus = permissionStatus,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onReLogin = { viewModel.clearDataAndLogout(onLogout) },
                )
                MainTab.TOOLS -> ToolsTab(
                    categories = toolCategories,
                    objects = toolObjects,
                    maps = maps,
                )
                MainTab.MINE -> MineTab(
                    accountInfo = accountInfo,
                    state = state,
                    profileImageUrl = dashboard.profileImageUrl,
                    onNavigateToSettings = onNavigateToSettings,
                    onLogout = { viewModel.clearDataAndLogout(onLogout) },
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    state: HomeViewModel.UiState,
    accountInfo: HomeViewModel.AccountMenuInfo,
    daySecrets: List<DaySecret>,
    marketObjects: List<ToolObjectSummary>,
    bannerImageUrl: String,
    permissionStatus: NotificationPermissionStatus,
    onRequestPermission: () -> Unit,
    onReLogin: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DailyBriefPanel(
                accountInfo = accountInfo,
                state = state,
                imageUrl = bannerImageUrl,
                daySecrets = daySecrets,
            )
        }
        if (permissionStatus == NotificationPermissionStatus.DENIED ||
            permissionStatus == NotificationPermissionStatus.PERMANENTLY_DENIED
        ) {
            item { NotificationPermissionBanner(onRequestAgain = onRequestPermission) }
        }
        item { QuickActionGrid() }
        item { DutySwitch() }
        item { SectionHeader("制作详情", "特勤处工位状态") }
        item {
            when (state) {
                HomeViewModel.UiState.Loading -> LoadingPanel("正在更新战报")
                HomeViewModel.UiState.NotLoggedIn -> LockedCraftingPanel(onReLogin)
                is HomeViewModel.UiState.Error -> EmptyPanel("同步异常", state.message)
                is HomeViewModel.UiState.AuthExpired -> AuthExpiredPanel(
                    reason = state.reason,
                    onReLogin = onReLogin,
                )
                is HomeViewModel.UiState.Success -> SnapshotContent(state.snapshot)
            }
        }
        item { SectionHeader("最新行情", "高价值物资波动") }
        item { MarketPreviewPanel(marketObjects) }
    }
}

@Composable
private fun DailyBriefPanel(
    accountInfo: HomeViewModel.AccountMenuInfo,
    state: HomeViewModel.UiState,
    imageUrl: String,
    daySecrets: List<DaySecret>,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(DfPanel, RoundedCornerShape(4.dp))
            .border(1.dp, DfLine, RoundedCornerShape(4.dp)),
    ) {
        RemoteImage(
            url = imageUrl,
            contentDescription = "行动战报背景",
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x99030706), Color(0xEE030706)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("烽火日报", color = DfGreen, style = MaterialTheme.typography.titleLarge)
                    Text("今日行动情报", color = DfMuted, style = MaterialTheme.typography.labelMedium)
                }
                StatusPill(
                    text = when (state) {
                        HomeViewModel.UiState.NotLoggedIn -> "访客"
                        HomeViewModel.UiState.Loading -> "同步中"
                        is HomeViewModel.UiState.AuthExpired -> "需绑定"
                        is HomeViewModel.UiState.Error -> "待同步"
                        is HomeViewModel.UiState.Success -> "已同步"
                    },
                    color = if (state is HomeViewModel.UiState.Error || state is HomeViewModel.UiState.AuthExpired) {
                        DfAmber
                    } else {
                        DfGreen
                    },
                )
            }
            Column {
                Text("当前账号", color = DfMuted, style = MaterialTheme.typography.labelMedium)
                Text(accountInfo.account, color = DfWhite, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "三角洲角色：${accountInfo.deltaRole}",
                    color = if (state is HomeViewModel.UiState.AuthExpired) DfAmber else DfGreen,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DaySecretPanel(daySecrets)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QuickActionGrid() {
    val actions = listOf("周报", "福利", "改枪", "百科", "地图")
    TacticalPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(DfPanelAlt, RoundedCornerShape(3.dp))
                        .border(1.dp, if (index == 0) DfGreen else DfLine, RoundedCornerShape(3.dp))
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when (label) {
                            "周报" -> "▰"
                            "福利" -> "◆"
                            "改枪" -> "⌁"
                            "百科" -> "▣"
                            else -> "◇"
                        },
                        color = if (index == 0) DfGreen else DfWhite,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        label,
                        color = if (index == 0) DfGreen else DfWhite,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DutySwitch() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchSegment("军需处", selected = false, modifier = Modifier.weight(1f))
        SwitchSegment("特勤处", selected = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SwitchSegment(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                if (selected) DfGreen.copy(alpha = 0.18f) else DfPanel,
                RoundedCornerShape(2.dp),
            )
            .border(1.dp, if (selected) DfGreen else DfLine, RoundedCornerShape(2.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) DfGreen else DfMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NotificationPermissionBanner(onRequestAgain: () -> Unit) {
    TacticalPanel(borderColor = DfAmber) {
        Text("通知权限未开启", color = DfAmber, style = MaterialTheme.typography.titleSmall)
        Text(
            "制造完成提醒会受影响，后台同步仍会继续。",
            color = DfMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onRequestAgain,
            colors = ButtonDefaults.buttonColors(containerColor = DfAmber, contentColor = Color.Black),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text("重新申请")
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "在线工位 ${snapshot.stations.size}",
            color = DfMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        snapshot.stations.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { station ->
                    StationCard(
                        station = station,
                        serverNowSeconds = snapshot.serverNowEpochSeconds,
                        nowMillis = nowMillis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StationCard(
    station: CraftingStation,
    serverNowSeconds: Long,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    TacticalPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(station.placeName, color = DfMuted, style = MaterialTheme.typography.labelMedium)
                Text("▤", color = DfFaint)
            }
            RemoteImage(
                url = station.iconUrl,
                contentDescription = station.itemName ?: station.placeName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
            )
            Text(
                station.itemName ?: "暂无制造物资",
                color = DfWhite,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatRemaining(station, serverNowSeconds, nowMillis),
                color = DfGreen,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "完成 ${formatEpoch(station.finishAtEpochSeconds)} · 均价 ${formatPrice(station.avgPrice)}",
                color = DfMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DaySecretPanel(daySecrets: List<DaySecret>) {
    Column {
        Text("今日密码", color = DfMuted, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            daySecrets.forEach { item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xCC101B18), RoundedCornerShape(2.dp))
                        .border(1.dp, DfLine, RoundedCornerShape(2.dp))
                        .padding(9.dp),
                ) {
                    Text(item.mapName, color = DfMuted, style = MaterialTheme.typography.labelSmall)
                    Text(
                        item.secret,
                        color = DfGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketPreviewPanel(objects: List<ToolObjectSummary>) {
    TacticalPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            objects.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(
                        url = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, color = DfWhite, style = MaterialTheme.typography.bodyMedium)
                        Text(item.category, color = DfMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(item.price, color = if (item.trend.startsWith("+")) DfGreen else DfRed)
                        Text(item.trend, color = DfMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedCraftingPanel(onReLogin: () -> Unit) {
    TacticalPanel(borderColor = DfAmber) {
        Text("未绑定行动档案", color = DfWhite, style = MaterialTheme.typography.titleMedium)
        Text(
            "绑定后查看特勤处制造进度与完成提醒。",
            color = DfMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(
            onClick = onReLogin,
            colors = ButtonDefaults.buttonColors(containerColor = DfGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("绑定账号")
        }
    }
}

@Composable
private fun ToolsTab(
    categories: List<String>,
    objects: List<ToolObjectSummary>,
    maps: List<MapSummary>,
) {
    var selectedObject by remember { mutableStateOf<ToolObjectSummary?>(null) }
    var priceObject by remember { mutableStateOf<ToolObjectSummary?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionHeader("工具", "物资行情 · 地图情报") }
        item { SearchShell() }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    CategoryChip(category)
                }
            }
        }
        items(objects, key = { it.id }) { item ->
            ToolObjectCard(
                item = item,
                onOpen = { selectedObject = item },
                onOpenPrice = { priceObject = item },
            )
        }
        item { MapPanel(maps) }
    }

    selectedObject?.let { item ->
        ObjectDetailDialog(item = item, onDismiss = { selectedObject = null })
    }
    priceObject?.let { item ->
        PriceDialog(item = item, onDismiss = { priceObject = null })
    }
}

@Composable
private fun SearchShell() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DfPanel, RoundedCornerShape(3.dp))
            .border(1.dp, DfLine, RoundedCornerShape(3.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = DfGreen, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Text("搜索物资、配件、地图", color = DfMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CategoryChip(label: String) {
    Box(
        modifier = Modifier
            .background(DfPanelAlt, RoundedCornerShape(2.dp))
            .border(1.dp, DfLine, RoundedCornerShape(2.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = DfWhite, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToolObjectCard(
    item: ToolObjectSummary,
    onOpen: () -> Unit,
    onOpenPrice: () -> Unit,
) {
    TacticalPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(
                url = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = DfWhite,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(item.category, color = DfMuted, style = MaterialTheme.typography.bodySmall)
                Text("${item.price} · ${item.trend}", color = DfGreen, style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("详情", color = DfGreen) }
                TextButton(onClick = onOpenPrice) { Text("行情", color = DfAmber) }
            }
        }
    }
}

@Composable
private fun MapPanel(maps: List<MapSummary>) {
    TacticalPanel {
        SectionHeader("地点", "危险区与行动路线", compact = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            maps.forEach { map ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(DfPanelAlt, RoundedCornerShape(2.dp))
                        .border(1.dp, DfLine, RoundedCornerShape(2.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(map.name, color = DfWhite, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MineTab(
    accountInfo: HomeViewModel.AccountMenuInfo,
    state: HomeViewModel.UiState,
    profileImageUrl: String,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionHeader("我的", "账号、绑定、隐私") }
        item {
            TacticalPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(
                        url = profileImageUrl,
                        contentDescription = "个人中心头像",
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("当前账号", color = DfMuted, style = MaterialTheme.typography.labelSmall)
                        Text(accountInfo.account, color = DfWhite, style = MaterialTheme.typography.titleMedium)
                        Text(
                            accountInfo.accountMark,
                            color = DfMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            TacticalPanel {
                StatusRow("三角洲角色", accountInfo.deltaRole)
                StatusRow("同步状态", if (state is HomeViewModel.UiState.Error) "异常" else "可用")
                StatusRow("账号保护", accountInfo.accountMark)
                StatusRow("互动操作", "已关闭")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DfGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("设置")
                }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Text("退出重新登录", color = DfRed)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DfMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = DfWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ObjectDetailDialog(item: ToolObjectSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, DfLine, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = DfGreen) }
        },
        containerColor = DfPanel,
        titleContentColor = Color.White,
        textContentColor = DfMuted,
        title = { Text(item.name) },
        text = {
            Column {
                RemoteImage(
                    url = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.4f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("战术分类：${item.category}")
                Text("市场均价：${item.price}")
                Text("行情波动：${item.trend}")
            }
        },
    )
}

@Composable
private fun PriceDialog(item: ToolObjectSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, DfLine, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = DfGreen) }
        },
        containerColor = DfPanel,
        titleContentColor = Color.White,
        textContentColor = DfMuted,
        title = { Text("${item.name} 行情走势") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("6小时前" to "41.2万", "3小时前" to "43.8万", "当前" to item.price).forEach { point ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(point.first)
                        Text(point.second, color = DfGreen)
                    }
                }
                Text("短线波动仅供行动前参考", color = DfMuted)
            }
        },
    )
}

@Composable
private fun AuthExpiredPanel(reason: String, onReLogin: () -> Unit) {
    TacticalPanel(borderColor = DfRed) {
        Text("登录已失效", color = DfRed, style = MaterialTheme.typography.titleMedium)
        Text(reason, color = DfMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        Button(
            onClick = onReLogin,
            colors = ButtonDefaults.buttonColors(containerColor = DfRed),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("重新登录")
        }
    }
}

@Composable
private fun LoadingPanel(text: String) {
    TacticalPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = DfGreen, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, color = DfMuted)
        }
    }
}

@Composable
private fun EmptyPanel(title: String, message: String) {
    TacticalPanel {
        Text(title, color = DfWhite, style = MaterialTheme.typography.titleMedium)
        Text(message, color = DfMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, compact: Boolean = false) {
    Column {
        Text(
            title,
            color = Color.White,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(subtitle, color = DfMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TacticalPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = DfLine,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(containerColor = DfPanel),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF101A17), Color(0xFF09100E)),
                    ),
                )
                .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                .padding(12.dp),
            content = content,
        )
    }
}

@Composable
private fun RemoteImage(
    url: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    val normalizedUrl = remember(url) {
        when {
            url.isNullOrBlank() -> null
            url.startsWith("//") -> "https:$url"
            else -> url
        }
    }

    LaunchedEffect(normalizedUrl) {
        image = null
        failed = false
        if (normalizedUrl != null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    URL(normalizedUrl).openStream().use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }.getOrNull()
            }
            image = loaded
            failed = loaded == null
        }
    }

    Box(
        modifier = modifier
            .background(DfPanelAlt, RoundedCornerShape(6.dp))
            .border(1.dp, DfLine, RoundedCornerShape(6.dp))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                if (failed) "IMG" else "DF",
                color = if (failed) DfAmber else DfGreen,
                style = MaterialTheme.typography.labelSmall,
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

private fun formatPrice(price: Long?): String =
    price?.let {
        if (it >= 10_000) "${it / 10_000}万" else it.toString()
    } ?: "-"
