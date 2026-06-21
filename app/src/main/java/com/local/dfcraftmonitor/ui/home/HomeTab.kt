package com.local.dfcraftmonitor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.dfcraftmonitor.data.backend.CollectionItem
import com.local.dfcraftmonitor.data.backend.DaySecret
import com.local.dfcraftmonitor.data.backend.IncomeSummary
import com.local.dfcraftmonitor.data.backend.ManufacturingRecommendation
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.ui.common.ErrorPanel
import com.local.dfcraftmonitor.ui.common.Formatters
import com.local.dfcraftmonitor.ui.common.LoadingPanel
import com.local.dfcraftmonitor.ui.common.LockedPanel
import com.local.dfcraftmonitor.ui.common.RemoteImage
import com.local.dfcraftmonitor.ui.common.SectionHeader
import com.local.dfcraftmonitor.ui.common.TacticalPanel
import com.local.dfcraftmonitor.ui.common.gradeBackgroundColor
import com.local.dfcraftmonitor.ui.permission.NotificationPermissionStatus
import com.local.dfcraftmonitor.ui.theme.SemanticColors
import kotlinx.coroutines.delay

/**
 * 首页 Tab：今日密码 + 制造详情 + 制造推荐。
 *
 * 性能要点：
 *  - LazyColumn 项均带 [key]，滚动时复用稳定
 *  - 倒计时合并到一个 [nowMillis] 状态，每秒整页只重组一次
 *  - 子组件参数全部标 [androidx.compose.runtime.Stable] 类型（List / 简单值）
 */
@Composable
internal fun HomeTab(
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "daily-brief") {
            DailyBriefPanel(daySecrets = daySecrets)
        }
        item(key = "income-collection") {
            IncomeCollectionPanel(
                income = yesterdayIncome,
                collections = collections,
            )
        }
        if (permissionStatus == NotificationPermissionStatus.DENIED ||
            permissionStatus == NotificationPermissionStatus.PERMANENTLY_DENIED
        ) {
            item(key = "permission-banner") {
                NotificationPermissionBanner(onRequestAgain = onRequestPermission)
            }
        }
        item(key = "section-crafting") {
            SectionHeader("制造详情")
        }
        item(key = "crafting-content") {
            when (state) {
                HomeViewModel.UiState.Loading -> LoadingPanel("正在更新战报")
                HomeViewModel.UiState.NotLoggedIn -> LockedPanel(
                    title = "未绑定行动档案",
                    description = "绑定后查看特勤处制造进度与完成提醒。",
                    actionLabel = "绑定账号",
                    onAction = onReLogin,
                )
                is HomeViewModel.UiState.Error -> ErrorPanel(
                    title = "同步异常",
                    message = state.message,
                )
                is HomeViewModel.UiState.AuthExpired -> ErrorPanel(
                    title = "登录已失效",
                    message = state.reason,
                    actionLabel = "重新登录",
                    onAction = onReLogin,
                )
                is HomeViewModel.UiState.Success -> SnapshotContent(state.snapshot)
            }
        }
        item(key = "section-recommend") {
            SectionHeader("制造推荐", "参考小程序行情与当前物资热度")
        }
        item(key = "recommendation-list") {
            ManufacturingRecommendationPanel(recommendations)
        }
    }
}

@Composable
private fun DailyBriefPanel(daySecrets: List<DaySecret>) {
    TacticalPanel {
        Text(
            text = "今日密码",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        DaySecretList(daySecrets = daySecrets)
    }
}

@Composable
private fun DaySecretList(daySecrets: List<DaySecret>) {
    val listState = rememberLazyListState()
    val scrollProgress by remember(daySecrets) {
        derivedStateOf {
            val total = daySecrets.size
            if (total <= 1) {
                0f
            } else {
                val info = listState.layoutInfo
                if (info.totalItemsCount == 0) {
                    0f
                } else {
                    val firstIdx = listState.firstVisibleItemIndex
                    val firstOffset = listState.firstVisibleItemScrollOffset
                    val itemSize = info.visibleItemsInfo.firstOrNull()?.size ?: 1
                    (firstIdx.toFloat() + firstOffset.toFloat() / itemSize.coerceAtLeast(1)) /
                        total.coerceAtLeast(1)
                }
            }.coerceIn(0f, 1f)
        }
    }
    val canScroll by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > info.visibleItemsInfo.size
        }
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 6.dp),
    ) {
        if (daySecrets.isEmpty()) {
            item(key = "empty") {
                Text(
                    "今日暂无密码数据",
                    color = SemanticColors.faintText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(items = daySecrets, key = { it.mapName }) { item ->
                DaySecretChip(item)
            }
        }
    }
    if (canScroll) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(3.dp),
        ) {
            val trackWidthPx = maxWidth.value
            val thumbFraction = 1f / daySecrets.size.coerceAtLeast(1)
            val thumbWidthDp = maxWidth * thumbFraction
            val maxOffsetDp = maxWidth - thumbWidthDp
            val thumbOffsetDp = maxOffsetDp * scrollProgress
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(1.5.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .width(thumbWidthDp)
                    .offset(x = thumbOffsetDp)
                    .height(3.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(1.5.dp),
                    ),
            )
        }
    }
}

@Composable
private fun DaySecretChip(item: DaySecret) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.extraSmall,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.mapName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.secret,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IncomeCollectionPanel(
    income: IncomeSummary,
    collections: List<CollectionItem>,
) {
    TacticalPanel {
        SectionHeader("带出收藏品", compact = true)
        if (collections.isEmpty() && income.amount.isBlank()) {
            Text(
                text = "暂无带出收藏品",
                color = SemanticColors.faintText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
            return@TacticalPanel
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            item(key = "yesterday-income") {
                IncomeChip(
                    text = income.amount.ifBlank { "暂无" },
                    sign = when {
                        income.rawValue != null && income.rawValue > 0L -> "+"
                        else -> ""
                    },
                    color = incomeColor(income),
                    hasData = income.rawValue != null,
                )
            }
            if (collections.isEmpty()) {
                item(key = "empty-collection") {
                    CollectionEmptyChip()
                }
            } else {
                items(items = collections, key = { it.id }) { item ->
                    CollectionChip(item)
                }
            }
        }
    }
}

private fun incomeColor(income: IncomeSummary): Color = when {
    income.rawValue == null -> com.local.dfcraftmonitor.ui.theme.SemanticWarn
    income.rawValue >= 0L -> com.local.dfcraftmonitor.ui.theme.SemanticProfit
    else -> com.local.dfcraftmonitor.ui.theme.SemanticLoss
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
            .width(116.dp)
            .height(IncomeCollectionCardHeight)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.small,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small,
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "昨日收益",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = if (hasData) "$sign$text" else text,
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
            .width(116.dp)
            .height(IncomeCollectionCardHeight)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.small,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small,
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "暂无收藏品",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CollectionChip(item: CollectionItem) {
    Box(
        modifier = Modifier
            .width(116.dp)
            .height(IncomeCollectionCardHeight)
            .background(
                gradeBackgroundColor(item.grade),
                MaterialTheme.shapes.small,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small,
            )
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
            Text(
                text = item.name,
                color = SemanticColors.onDarkSurface,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.value.ifBlank { item.mapName },
                color = SemanticColors.gold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (item.count > 1) {
            Text(
                text = "x${item.count}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

private val IncomeCollectionCardHeight = 108.dp

@Composable
private fun NotificationPermissionBanner(onRequestAgain: () -> Unit) {
    TacticalPanel(borderColor = SemanticColors.warn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = SemanticColors.warn,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "通知权限未开启",
                    color = SemanticColors.warn,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "制造完成提醒会受影响，后台同步仍会继续。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Button(
            onClick = onRequestAgain,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = SemanticColors.warn,
                contentColor = Color.Black,
            ),
            modifier = Modifier.padding(top = 12.dp),
        ) { Text("重新申请") }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        snapshot.stations.chunked(2).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(
                        url = station.iconUrl,
                        contentDescription = station.itemName ?: station.placeName,
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f),
                        showContainer = false,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        text = station.itemName ?: "暂无制造物资",
                        color = SemanticColors.onDarkSurface,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatRemaining(station, serverNowSeconds, nowMillis),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = station.placeName,
            color = SemanticColors.gold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        )
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
        else -> "剩余 ${Formatters.duration(remaining)}"
    }
}

@Composable
private fun ManufacturingRecommendationPanel(objects: List<ManufacturingRecommendation>) {
    TacticalPanel {
        if (objects.isEmpty()) {
            Text(
                text = "暂无制造推荐",
                color = SemanticColors.faintText,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                objects.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    gradeBackgroundColor(item.grade),
                                    MaterialTheme.shapes.small,
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            RemoteImage(
                                url = item.imageUrl,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                showContainer = false,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = SemanticColors.onDarkSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(
                                    item.placeName.takeIf { it.isNotBlank() },
                                    item.period.takeIf { it.isNotBlank() }?.let { "周期${it}h" },
                                    item.perCount.takeIf { it.isNotBlank() }?.let { "产出x$it" },
                                ).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "售价 ${item.salePrice} / 成本 ${item.costPrice} / 手续 ${item.fee}" +
                                    (if (item.bail.isNotBlank() && item.bail != "0") " / 保证金 ${item.bail}" else ""),
                                color = SemanticColors.faintText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "每小时利润",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = item.profitPerHour.ifBlank { "-" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
