package com.local.dfcraftmonitor.ui.home

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.local.dfcraftmonitor.data.backend.CollectionItem
import com.local.dfcraftmonitor.data.backend.DaySecret
import com.local.dfcraftmonitor.data.backend.IncomeSummary
import com.local.dfcraftmonitor.data.backend.MapSummary
import com.local.dfcraftmonitor.data.backend.MatchRecord
import com.local.dfcraftmonitor.data.backend.ManufacturingRecommendation
import com.local.dfcraftmonitor.data.backend.PlayerProfile
import com.local.dfcraftmonitor.data.backend.RedArchiveRecord
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

/**
 * 物品等级背景色，与三角洲小程序 itemBackColor 一致。
 * grade: 1=灰 2=绿 3=蓝 4=紫 5=金 6=红 7=深红
 */
private fun gradeBackgroundColor(grade: Int): Color = when (grade) {
    2 -> Color(0xFF081916)
    3 -> Color(0xFF101B25)
    4 -> Color(0xFF1B1729)
    5 -> Color(0xFF271F16)
    6 -> Color(0xFF2B1A1B)
    7 -> Color(0xFF2B1E1F)
    else -> Color(0xFF191D1C)
}

// 昨日收益卡片与带出收藏品卡片的统一高度，保证两排卡片视觉一致。
private val IncomeCollectionCardHeight = 108.dp

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Default.Home),
    BATTLE("战绩", Icons.AutoMirrored.Filled.List),
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
        containerColor = DfBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("三角洲行动", fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DfBackground,
                    titleContentColor = Color.White,
                    actionIconContentColor = DfGreen,
                ),
                actions = {
                    // 所有 Tab 都有主动刷新按钮
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    // 任务5：设置按钮上移到顶栏右上角（所有页签可见），登录/退出登录收入设置页。
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
                MainTab.BATTLE -> BattleTab(
                    matches = recentMatches,
                )
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

@Composable
private fun HomeTab(
    state: HomeViewModel.UiState,
    daySecrets: List<DaySecret>,
    yesterdayIncome: IncomeSummary,
    collections: List<CollectionItem>,
    recommendations: List<ManufacturingRecommendation>,
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
                daySecrets = daySecrets,
            )
        }
        item {
            IncomeCollectionPanel(
                income = yesterdayIncome,
                collections = collections,
            )
        }
        if (permissionStatus == NotificationPermissionStatus.DENIED ||
            permissionStatus == NotificationPermissionStatus.PERMANENTLY_DENIED
        ) {
            item { NotificationPermissionBanner(onRequestAgain = onRequestPermission) }
        }
        item { SectionHeader("制造详情") }
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
        item { SectionHeader("制造推荐", "参考小程序行情与当前物资热度") }
        item { ManufacturingRecommendationPanel(recommendations) }
    }
}

@Composable
private fun DailyBriefPanel(
    daySecrets: List<DaySecret>,
) {
    TacticalPanel {
        DaySecretPanel(daySecrets)
    }
}

@Composable
private fun IncomeCollectionPanel(
    income: IncomeSummary,
    collections: List<CollectionItem>,
) {
    val incomeColor = when {
        income.rawValue == null -> DfAmber
        income.rawValue >= 0L -> DfGreen
        else -> DfRed
    }
    val incomeSign = when {
        income.rawValue != null && income.rawValue > 0L -> "+"
        else -> ""
    }
    val incomeText = income.amount.ifBlank { "暂无" }

    TacticalPanel {
        SectionHeader("带出收藏品", compact = true)
        if (collections.isEmpty() && incomeText.isBlank()) {
            Text("暂无带出收藏品", color = DfMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                // 昨日收益卡片：与收藏品卡片同等尺寸，置于最左侧
                item(key = "yesterdayIncome") {
                    IncomeChip(
                        text = incomeText,
                        sign = incomeSign,
                        color = incomeColor,
                        hasData = income.rawValue != null,
                    )
                }
                if (collections.isEmpty()) {
                    item(key = "emptyCollection") {
                        CollectionEmptyChip()
                    }
                } else {
                    items(collections, key = { it.id }) { item ->
                        CollectionChip(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeChip(
    text: String,
    sign: String,
    color: Color,
    hasData: Boolean,
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .height(IncomeCollectionCardHeight)
            .background(DfPanelAlt, RoundedCornerShape(3.dp))
            .border(1.dp, DfLine, RoundedCornerShape(3.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("昨日收益", color = DfMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            if (hasData) "$sign$text" else text,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionEmptyChip() {
    Column(
        modifier = Modifier
            .width(112.dp)
            .height(IncomeCollectionCardHeight)
            .background(DfPanelAlt, RoundedCornerShape(3.dp))
            .border(1.dp, DfLine, RoundedCornerShape(3.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("暂无收藏品", color = DfMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CollectionChip(item: CollectionItem) {
    Box(
        modifier = Modifier
            .width(112.dp)
            .height(IncomeCollectionCardHeight)
            .background(gradeBackgroundColor(item.grade), RoundedCornerShape(3.dp))
            .border(1.dp, DfLine, RoundedCornerShape(3.dp))
            .padding(8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            RemoteImage(
                url = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.size(64.dp),
                showContainer = false,
            )
            Text(item.name, color = DfWhite, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.value.ifBlank { item.mapName }, color = DfAmber, style = MaterialTheme.typography.labelSmall)
        }
        // 数量标记在右上角（仅数量大于 1 时显示）
        if (item.count > 1) {
            Text(
                "x${item.count}",
                color = DfGreen,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        snapshot.stations.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    Box(modifier = modifier.aspectRatio(1f)) {
        TacticalPanel(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize(),
            ) {
                // 图片区域（占上半部分）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            DfPanelAlt,
                            shape = RoundedCornerShape(3.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(
                        url = station.iconUrl,
                        contentDescription = station.itemName ?: station.placeName,
                        modifier = Modifier.fillMaxWidth(0.75f).aspectRatio(1f),
                        showContainer = false,
                    )
                }
                // 文字区域
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        station.itemName ?: "暂无制造物资",
                        color = DfWhite,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatRemaining(station, serverNowSeconds, nowMillis),
                        color = DfGreen,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // placeName 显示在左上角
        Text(
            station.placeName,
            color = DfAmber,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
    }
}

@Composable
private fun DaySecretPanel(daySecrets: List<DaySecret>) {
    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            val total = daySecrets.size
            if (total <= 1) 0f
            else {
                val info = listState.layoutInfo
                if (info.totalItemsCount == 0) 0f
                else {
                    val firstIdx = listState.firstVisibleItemIndex
                    val firstOffset = listState.firstVisibleItemScrollOffset
                    val itemSize = info.visibleItemsInfo.firstOrNull()?.size ?: 1
                    (firstIdx.toFloat() + firstOffset.toFloat() / itemSize.coerceAtLeast(1)) / total.coerceAtLeast(1)
                }
            }.coerceIn(0f, 1f)
        }
    }
    val canScroll by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > info.visibleItemsInfo.size
        }
    }
    Column {
        Text("今日密码", color = DfMuted, style = MaterialTheme.typography.labelMedium)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(daySecrets, key = { it.mapName }) { item ->
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .background(Color(0xCC101B18), RoundedCornerShape(2.dp))
                        .border(1.dp, DfLine, RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        item.mapName,
                        color = DfMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.secret,
                        color = DfGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        // 半透明滑块指示器
        if (canScroll) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp),
            ) {
                val trackWidthPx = maxWidth.value
                val thumbFraction = 1f / daySecrets.size.coerceAtLeast(1)
                val thumbWidthDp = maxWidth * thumbFraction
                val maxOffsetDp = maxWidth - thumbWidthDp
                val thumbOffsetDp = maxOffsetDp * scrollProgress
                // 轨道
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DfLine.copy(alpha = 0.3f), RoundedCornerShape(1.5.dp)),
                )
                // 滑块
                Box(
                    modifier = Modifier
                        .width(thumbWidthDp)
                        .offset(x = thumbOffsetDp)
                        .height(3.dp)
                        .background(DfGreen.copy(alpha = 0.5f), RoundedCornerShape(1.5.dp)),
                )
            }
        }
    }
}

@Composable
private fun ManufacturingRecommendationPanel(objects: List<ManufacturingRecommendation>) {
    TacticalPanel {
        if (objects.isEmpty()) {
            Text("暂无制造推荐", color = DfMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                objects.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(gradeBackgroundColor(item.grade), RoundedCornerShape(3.dp))
                                .border(1.dp, DfLine, RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RemoteImage(
                                url = item.imageUrl,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                showContainer = false,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = DfWhite,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    item.placeName.takeIf { it.isNotBlank() },
                                    item.period.takeIf { it.isNotBlank() }?.let { "周期${it}h" },
                                    item.perCount.takeIf { it.isNotBlank() }?.let { "产出x$it" },
                                ).joinToString(" · "),
                                color = DfMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "售价 ${item.salePrice} / 成本 ${item.costPrice} / 手续 ${item.fee}${if (item.bail.isNotBlank() && item.bail != "0") " / 保证金 ${item.bail}" else ""}",
                                color = DfFaint,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("每小时利润", color = DfMuted, style = MaterialTheme.typography.labelSmall)
                            Text(
                                item.profitPerHour.ifBlank { "-" },
                                color = DfGreen,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
private fun BattleTab(
    matches: List<MatchRecord>,
) {
    var selectedMap by remember { mutableStateOf("全部") }
    var selectedMatch by remember { mutableStateOf<MatchRecord?>(null) }
    val mapLabels = remember(matches) {
        listOf("全部") + matches.map { it.mapName }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val filteredMatches = matches.filter { match ->
        selectedMap == "全部" || match.mapName == selectedMap
    }

    val todayProfit = remember(filteredMatches) {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        filteredMatches
            .filter { it.battleTime.startsWith(todayStr) }
            .sumOf { it.netIncomeValue ?: 0L }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionHeader("战绩", "战绩生涯 · 最近对局")
                Spacer(modifier = Modifier.weight(1f))
                TodayProfitCard(profit = todayProfit)
            }
        }
        item {
            BattleFilterPanel(
                maps = mapLabels,
                selectedMap = selectedMap,
                onMapSelected = { selectedMap = it },
            )
        }
        if (filteredMatches.isEmpty()) {
            item { EmptyPanel("暂无最近对局", "同步后会显示最近对局数据。") }
        } else {
            items(filteredMatches, key = { it.id }) { match ->
                MatchCard(match = match, onOpen = { selectedMatch = match })
            }
        }
    }

    selectedMatch?.let { match ->
        MatchDetailDialog(match = match, onDismiss = { selectedMatch = null })
    }
}

@Composable
private fun TodayProfitCard(profit: Long) {
    val color = when {
        profit > 0L -> DfGreen
        profit < 0L -> DfRed
        else -> DfMuted
    }
    val sign = when {
        profit > 0L -> "+"
        profit < 0L -> "-"
        else -> ""
    }
    val absValue = kotlin.math.abs(profit)
    val formatted = when {
        absValue >= 100_000_000L -> String.format(java.util.Locale.US, "%.1f亿", absValue / 100_000_000.0)
        absValue >= 10_000L -> String.format(java.util.Locale.US, "%.1f万", absValue / 10_000.0)
        else -> absValue.toString()
    }
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = DfPanel),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text("今日盈亏", color = DfMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                "$sign$formatted",
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BattleFilterPanel(
    maps: List<String>,
    selectedMap: String,
    onMapSelected: (String) -> Unit,
) {
    TacticalPanel {
        Text("地图筛选", color = DfMuted, style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(maps) { map ->
                FilterChip(label = map, selected = map == selectedMap, onClick = { onMapSelected(map) })
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) DfGreen.copy(alpha = 0.18f) else DfPanelAlt, RoundedCornerShape(2.dp))
            .border(1.dp, if (selected) DfGreen else DfLine, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) DfGreen else DfWhite, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MatchCard(match: MatchRecord, onOpen: () -> Unit) {
    // 撤离状态配色：成功绿、失败红、其余白
    val resultColor = matchResultColor(match.result)
    // 收益配色：正绿、负红、未知灰
    val incomeColor = when {
        match.netIncomeValue == null -> DfMuted
        match.netIncomeValue >= 0L -> DfGreen
        else -> DfRed
    }
    TacticalPanel {
        Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            // 对局时间，右上角
            if (match.battleTime.isNotBlank()) {
                Text(
                    match.battleTime,
                    color = DfFaint,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            // 收益，右下角（与小程序净收益一致：原始千分位，负数=亏损）
            if (match.netIncome.isNotBlank()) {
                Text(
                    match.netIncome,
                    color = incomeColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(top = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 干员头像（与小程序一致，由 ArmedForceId 查 assetsId.sqlImg 得到 CDN 图）。
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(gradeBackgroundColor(0), RoundedCornerShape(3.dp))
                        .border(1.dp, DfLine, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (match.operatorImageUrl.isNotBlank()) {
                        RemoteImage(
                            url = match.operatorImageUrl,
                            contentDescription = match.operatorName.ifBlank { "干员" },
                            modifier = Modifier.fillMaxSize(),
                            showContainer = false,
                        )
                    } else {
                        Text(
                            match.operatorName.ifBlank { "干员" }.take(1),
                            color = DfAmber,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(match.mapName.ifBlank { "未知地图" }, color = DfWhite, style = MaterialTheme.typography.titleMedium)
                    // 撤离结果按状态着色
                    Text(
                        "${match.modeName.ifBlank { "最近对局" }} · ${match.result.ifBlank { "结果待同步" }}",
                        color = resultColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    // 使用的干员（白色）
                    Text(
                        "干员 ${match.operatorName.ifBlank { "未知" }}",
                        color = DfWhite,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "击败 ${match.operatorKills.ifBlank { "0" }} · 用时 ${match.duration.ifBlank { "-" }}",
                        color = DfGreen,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** 撤离状态配色：成功绿、失败红、其余白。 */
private fun matchResultColor(result: String): Color = when {
    result.contains("成功") -> DfGreen
    result.contains("失败") || result.contains("阵亡") -> DfRed
    else -> DfWhite
}

@Composable
private fun MatchDetailDialog(match: MatchRecord, onDismiss: () -> Unit) {
    // 战绩详情重构为整屏页面（仿小程序 recordDetails 页）。
    BackHandler(enabled = true, onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize().background(DfBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶部：返回 + 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = DfGreen)
                }
                Text("战绩详情", color = DfWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // 干员 + 地图概览卡（与小程序详情顶部一致）
            TacticalPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(gradeBackgroundColor(0), RoundedCornerShape(4.dp))
                            .border(1.dp, DfLine, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (match.operatorImageUrl.isNotBlank()) {
                            RemoteImage(
                                url = match.operatorImageUrl,
                                contentDescription = match.operatorName.ifBlank { "干员" },
                                modifier = Modifier.fillMaxSize(),
                                showContainer = false,
                            )
                        } else {
                            Text(
                                match.operatorName.ifBlank { "干员" }.take(1),
                                color = DfAmber,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            match.operatorName.ifBlank { "未知干员" },
                            color = DfWhite,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text("地图 ${match.mapName.ifBlank { "-" }}", color = DfMuted, style = MaterialTheme.typography.bodySmall)
                        Text("模式 ${match.modeName.ifBlank { "-" }}", color = DfMuted, style = MaterialTheme.typography.bodySmall)
                        Text("时间 ${match.battleTime.ifBlank { "-" }}", color = DfAmber, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 战斗结果
            TacticalPanel {
                SectionHeader("战斗结果", compact = true)
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow("结果", match.result.ifBlank { "-" })
                StatusRow("用时", match.duration.ifBlank { "-" })
                StatusRow("击败干员", match.operatorKills.ifBlank { "0" })
            }

            // 收益（净收益/带出）
            TacticalPanel {
                SectionHeader("收益", compact = true)
                Spacer(modifier = Modifier.height(8.dp))
                val incomeColor = when {
                    match.netIncomeValue == null -> DfMuted
                    match.netIncomeValue >= 0L -> DfGreen
                    else -> DfRed
                }
                StatusRow("净收益", match.netIncome.ifBlank { "-" }, valueColor = incomeColor)
                match.broughtOutValue?.let {
                    StatusRow("带出价值", it.formatCommaSafe(), valueColor = DfAmber)
                }
            }
        }
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
                Text(priceSummary(item), color = DfGreen, style = MaterialTheme.typography.bodyMedium)
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
    profile: PlayerProfile,
    redArchive: List<RedArchiveRecord>,
    selectedSeason: SeasonOption,
    seasonLoading: Boolean,
    onSeasonSelected: (SeasonOption) -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    // 登录/退出登录入口已上移到顶栏设置按钮 → 设置页（任务5），
    // 这里保留参数仅为兼容调用方，不再在本页渲染对应按钮。
    if (false) {
        onNavigateToSettings()
        onLogout()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionHeader("我的", "个人资料 · 大红藏馆") }
        item {
            ProfilePanel(
                profile = profile,
                selectedSeason = selectedSeason,
                seasonLoading = seasonLoading,
                onSeasonSelected = onSeasonSelected,
            )
        }
        item {
            MineStatsPanel(profile = profile, state = state, accountInfo = accountInfo)
        }
        item {
            RedArchiveSection(records = redArchive)
        }
        // 登录/退出登录按钮已移至「设置」页（任务5），此处不再展示。
    }
}

@Composable
private fun ProfilePanel(
    profile: PlayerProfile,
    selectedSeason: SeasonOption,
    seasonLoading: Boolean,
    onSeasonSelected: (SeasonOption) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    TacticalPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .background(gradeBackgroundColor(0), RoundedCornerShape(6.dp))
                    .border(1.dp, DfLine, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(
                    url = profile.avatarUrl,
                    contentDescription = "个人头像",
                    modifier = Modifier.size(66.dp),
                    showContainer = false,
                )
                RemoteImage(
                    url = profile.avatarFrameUrl,
                    contentDescription = "头像框",
                    modifier = Modifier.size(86.dp),
                    showContainer = false,
                    showPlaceholder = false,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("昵称", color = DfMuted, style = MaterialTheme.typography.labelSmall)
                Text(profile.nickname.ifBlank { "昵称待同步" }, color = DfWhite, style = MaterialTheme.typography.titleMedium)
                Text(
                    "当前区 ${profile.areaName.ifBlank { "待同步" }}",
                    color = DfMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("头像框", color = DfAmber, style = MaterialTheme.typography.labelSmall)
            }
        }
        // 赛季筛选下拉
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
        ) {
            Text("生涯赛季", color = DfMuted, style = MaterialTheme.typography.labelSmall)
            Box {
                Row(
                    modifier = Modifier
                        .background(DfPanelAlt, RoundedCornerShape(4.dp))
                        .border(1.dp, DfLine, RoundedCornerShape(4.dp))
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (seasonLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = DfGreen,
                        )
                    }
                    Text(
                        selectedSeason.label,
                        color = DfWhite,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "选择赛季",
                        tint = DfMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    SEASON_OPTIONS.forEach { season ->
                        DropdownMenuItem(
                            text = { Text(season.label) },
                            onClick = {
                                onSeasonSelected(season)
                                dropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            RankBlock("当前段位", profile.currentRankName, profile.currentRankIconUrl, Modifier.weight(1f))
            RankBlock("最高段位", profile.highestRankName, profile.highestRankIconUrl, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankBlock(label: String, value: String, iconUrl: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(DfPanelAlt, RoundedCornerShape(3.dp))
            .border(1.dp, DfLine, RoundedCornerShape(3.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(url = iconUrl, contentDescription = label, modifier = Modifier.size(42.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, color = DfMuted, style = MaterialTheme.typography.labelSmall)
            Text(value.ifBlank { "-" }, color = DfWhite, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MineStatsPanel(
    profile: PlayerProfile,
    state: HomeViewModel.UiState,
    accountInfo: HomeViewModel.AccountMenuInfo,
) {
    TacticalPanel {
        // 按用户要求：移除角色绑定行与同步状态行
        StatusRow("烽火地带带出总价值", profile.totalBringOutValue.ifBlank { "-" })
        StatusRow("撤离率", profile.evacuationRate.ifBlank { "-" })
        StatusRow("击败干员", profile.operatorKills.ifBlank { "0" })
        StatusRow("赚损比", profile.profitLossRatio.ifBlank { "-" })
    }
}

@Composable
private fun RedArchiveSection(records: List<RedArchiveRecord>) {
    var selectedMap by remember { mutableStateOf("全部") }
    var selectedRecord by remember { mutableStateOf<RedArchiveRecord?>(null) }
    val mapLabels = remember(records) {
        listOf("全部") + records.map { it.mapName }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val filtered = records.filter { selectedMap == "全部" || it.mapName == selectedMap }

    TacticalPanel {
        SectionHeader("大红藏馆", "出红记录", compact = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(mapLabels) { map ->
                FilterChip(label = map, selected = map == selectedMap, onClick = { selectedMap = map })
            }
        }
        if (filtered.isEmpty()) {
            Text("暂无出红记录", color = DfMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                filtered.forEach { record ->
                    RedArchiveRow(record = record, onOpen = { selectedRecord = record })
                }
            }
        }
    }

    selectedRecord?.let { record ->
        RedRecordDialog(record = record, onDismiss = { selectedRecord = null })
    }
}

@Composable
private fun RedArchiveRow(record: RedArchiveRecord, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradeBackgroundColor(record.grade), RoundedCornerShape(3.dp))
            .border(1.dp, DfLine, RoundedCornerShape(3.dp))
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(url = record.imageUrl, contentDescription = record.name, modifier = Modifier.size(52.dp), showContainer = false)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(record.name, color = DfWhite, style = MaterialTheme.typography.bodyMedium)
            Text("${record.mapName} · ${record.foundTime}", color = DfMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(record.value, color = DfAmber, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RedRecordDialog(record: RedArchiveRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(4.dp),
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = DfGreen) } },
        containerColor = DfPanel,
        titleContentColor = Color.White,
        textContentColor = DfMuted,
        title = { Text("出红记录") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(gradeBackgroundColor(record.grade), RoundedCornerShape(3.dp))
                        .border(1.dp, DfLine, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(url = record.imageUrl, contentDescription = record.name, modifier = Modifier.fillMaxWidth().aspectRatio(2.2f), showContainer = false)
                }
                Spacer(modifier = Modifier.height(10.dp))
                StatusRow("物品", record.name)
                StatusRow("地图", record.mapName.ifBlank { "-" })
                StatusRow("估值", record.value.ifBlank { "-" })
                StatusRow("时间", record.foundTime.ifBlank { "-" })
            }
        },
    )
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color = DfWhite) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DfMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
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
                Text("市场均价：${item.price.ifBlank { "暂无价格" }}")
                Text("行情波动：${item.trend.ifBlank { "暂无走势数据" }}")
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
                if (item.pricePoints.isEmpty()) {
                    Text("暂无走势数据", color = DfMuted)
                } else {
                    item.pricePoints.forEach { point ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(point.label)
                            Text(point.price, color = DfGreen)
                        }
                    }
                    Text("短线波动仅供行动前参考", color = DfMuted)
                }
            }
        },
    )
}

private fun priceSummary(item: ToolObjectSummary): String =
    listOf(item.price, item.trend)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "暂无行情" }

private fun trendColor(trend: String): Color = when {
    trend.startsWith("+") -> DfGreen
    trend.startsWith("-") -> DfRed
    else -> DfMuted
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
private fun SectionHeader(title: String, subtitle: String? = null, compact: Boolean = false) {
    Column {
        Text(
            title,
            color = Color.White,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (subtitle != null) {
            Text(subtitle, color = DfMuted, style = MaterialTheme.typography.labelSmall)
        }
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
    showContainer: Boolean = true,
    showPlaceholder: Boolean = true,
) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    val normalizedUrl = remember(url) {
        when {
            url.isNullOrBlank() -> null
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://", ignoreCase = true) -> url.replaceFirst("http:", "https:", ignoreCase = true)
            url.startsWith("https:" + "//", ignoreCase = true) -> url
            else -> null
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
            .then(if (showContainer) Modifier.background(DfPanelAlt, RoundedCornerShape(6.dp)).border(1.dp, DfLine, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showPlaceholder) {
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

/** 千分位原始数值（与小程序 addComma 一致），用于战绩带出价值等需要精确金额的场景。 */
private fun Long.formatCommaSafe(): String {
    val sign = if (this < 0) "-" else ""
    val value = kotlin.math.abs(this)
    return sign + value.toString().replace(Regex("(?=(?:\\d{3})+$"), ",").removePrefix(",")
}
