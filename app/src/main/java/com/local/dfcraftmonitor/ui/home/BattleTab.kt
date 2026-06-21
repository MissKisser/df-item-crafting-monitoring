package com.local.dfcraftmonitor.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.dfcraftmonitor.data.backend.MatchRecord
import com.local.dfcraftmonitor.ui.common.EmptyPanel
import com.local.dfcraftmonitor.ui.common.Formatters
import com.local.dfcraftmonitor.ui.common.RemoteImage
import com.local.dfcraftmonitor.ui.common.SectionHeader
import com.local.dfcraftmonitor.ui.common.TacticalPanel
import com.local.dfcraftmonitor.ui.common.formatCommaSafe
import com.local.dfcraftmonitor.ui.common.gradeBackgroundColor
import com.local.dfcraftmonitor.ui.theme.SemanticColors

/**
 * 战绩 Tab：今日盈亏 + 地图筛选 + 对局列表 + 对局详情。
 */
@Composable
internal fun BattleTab(matches: List<MatchRecord>) {
    var selectedMap by remember { mutableStateOf("全部") }
    var selectedMatch by remember { mutableStateOf<MatchRecord?>(null) }

    val mapLabels by remember(matches) {
        derivedStateOf {
            listOf("全部") + matches.asSequence()
                .map { it.mapName }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        }
    }
    val filteredMatches = remember(matches, selectedMap) {
        if (selectedMap == "全部") matches else matches.filter { it.mapName == selectedMap }
    }
    val todayProfit = remember(filteredMatches) {
        val today = Formatters.todayDateString()
        filteredMatches.asSequence()
            .filter { it.battleTime.startsWith(today) }
            .sumOf { it.netIncomeValue ?: 0L }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "battle-header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionHeader("战绩", "战绩生涯 · 最近对局", compact = true)
                Spacer(modifier = Modifier.weight(1f))
                TodayProfitCard(profit = todayProfit)
            }
        }
        item(key = "battle-filter") {
            BattleFilterPanel(
                maps = mapLabels,
                selectedMap = selectedMap,
                onMapSelected = { selectedMap = it },
            )
        }
        if (filteredMatches.isEmpty()) {
            item(key = "battle-empty") {
                EmptyPanel("暂无最近对局", "同步后会显示最近对局数据。")
            }
        } else {
            items(items = filteredMatches, key = { it.id }) { match ->
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
        profit > 0L -> SemanticColors.profit
        profit < 0L -> SemanticColors.loss
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = when {
        profit > 0L -> "+"
        profit < 0L -> "-"
        else -> ""
    }
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "今日盈亏",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "$sign${Formatters.prettyLong(if (profit < 0) -profit else profit)}",
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
        Text(
            text = "地图筛选",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(items = maps) { map ->
                FilterChip(label = map, selected = map == selectedMap, onClick = { onMapSelected(map) })
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        SemanticColors.onDarkSurface
    }
    Box(
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .border(1.dp, border, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun MatchCard(match: MatchRecord, onOpen: () -> Unit) {
    val resultColor = matchResultColor(match.result)
    val incomeColor = when {
        match.netIncomeValue == null -> SemanticColors.faintText
        match.netIncomeValue >= 0L -> SemanticColors.profit
        else -> SemanticColors.loss
    }
    TacticalPanel {
        Box(modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)) {
            if (match.battleTime.isNotBlank()) {
                Text(
                    text = match.battleTime,
                    color = SemanticColors.faintText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            if (match.netIncome.isNotBlank()) {
                Text(
                    text = match.netIncome,
                    color = incomeColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(top = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            gradeBackgroundColor(0),
                            MaterialTheme.shapes.small,
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.small,
                        ),
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
                            text = match.operatorName.ifBlank { "干员" }.take(1),
                            color = SemanticColors.gold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    Text(
                        text = match.mapName.ifBlank { "未知地图" },
                        color = SemanticColors.onDarkSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${match.modeName.ifBlank { "最近对局" }} · ${match.result.ifBlank { "结果待同步" }}",
                        color = resultColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "干员 ${match.operatorName.ifBlank { "未知" }}",
                        color = SemanticColors.onDarkSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "击败 ${match.operatorKills.ifBlank { "0" }} · 用时 ${match.duration.ifBlank { "-" }}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun matchResultColor(result: String): Color = when {
    result.contains("成功") -> com.local.dfcraftmonitor.ui.theme.SemanticProfit
    result.contains("失败") || result.contains("阵亡") -> com.local.dfcraftmonitor.ui.theme.SemanticLoss
    else -> com.local.dfcraftmonitor.ui.theme.SemanticOnDark
}

@Composable
private fun MatchDetailDialog(match: MatchRecord, onDismiss: () -> Unit) {
    BackHandler(enabled = true, onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "战绩详情",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            TacticalPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(
                                gradeBackgroundColor(0),
                                MaterialTheme.shapes.medium,
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.medium,
                            ),
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
                                text = match.operatorName.ifBlank { "干员" }.take(1),
                                color = SemanticColors.gold,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = match.operatorName.ifBlank { "未知干员" },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "地图 ${match.mapName.ifBlank { "-" }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "模式 ${match.modeName.ifBlank { "-" }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "时间 ${match.battleTime.ifBlank { "-" }}",
                            color = SemanticColors.gold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            TacticalPanel {
                SectionHeader("战斗结果", compact = true)
                Spacer(modifier = Modifier.height(8.dp))
                com.local.dfcraftmonitor.ui.common.StatusRow("结果", match.result.ifBlank { "-" })
                com.local.dfcraftmonitor.ui.common.StatusRow("用时", match.duration.ifBlank { "-" })
                com.local.dfcraftmonitor.ui.common.StatusRow("击败干员", match.operatorKills.ifBlank { "0" })
            }

            TacticalPanel {
                SectionHeader("收益", compact = true)
                Spacer(modifier = Modifier.height(8.dp))
                val incomeColor = when {
                    match.netIncomeValue == null -> SemanticColors.faintText
                    match.netIncomeValue >= 0L -> SemanticColors.profit
                    else -> SemanticColors.loss
                }
                com.local.dfcraftmonitor.ui.common.StatusRow(
                    label = "净收益",
                    value = match.netIncome.ifBlank { "-" },
                    valueColor = incomeColor,
                )
                match.broughtOutValue?.let {
                    com.local.dfcraftmonitor.ui.common.StatusRow(
                        label = "带出价值",
                        value = it.formatCommaSafe(),
                        valueColor = SemanticColors.gold,
                    )
                }
            }
        }
    }
}
