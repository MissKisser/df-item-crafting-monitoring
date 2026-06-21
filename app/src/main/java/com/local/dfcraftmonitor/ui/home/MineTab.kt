package com.local.dfcraftmonitor.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.dfcraftmonitor.data.backend.PlayerProfile
import com.local.dfcraftmonitor.data.backend.RedArchiveRecord
import com.local.dfcraftmonitor.ui.common.RemoteImage
import com.local.dfcraftmonitor.ui.common.SectionHeader
import com.local.dfcraftmonitor.ui.common.StatusRow
import com.local.dfcraftmonitor.ui.common.TacticalPanel
import com.local.dfcraftmonitor.ui.common.gradeBackgroundColor
import com.local.dfcraftmonitor.ui.theme.SemanticColors

/**
 * 我的 Tab：玩家档案 + 生涯统计 + 大红藏馆。
 */
@Composable
internal fun MineTab(
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
    // 登录/退出登录入口已上移到顶栏设置按钮 → 设置页（任务5）
    @Suppress("UNUSED_EXPRESSION")
    onNavigateToSettings
    @Suppress("UNUSED_EXPRESSION")
    onLogout

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "mine-header") {
            SectionHeader("我的", "个人资料 · 大红藏馆")
        }
        item(key = "mine-profile") {
            ProfilePanel(
                profile = profile,
                selectedSeason = selectedSeason,
                seasonLoading = seasonLoading,
                onSeasonSelected = onSeasonSelected,
            )
        }
        item(key = "mine-stats") {
            MineStatsPanel(profile = profile, state = state, accountInfo = accountInfo)
        }
        item(key = "mine-red-archive") {
            RedArchiveSection(records = redArchive)
        }
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
                    .size(96.dp)
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
                RemoteImage(
                    url = profile.avatarUrl,
                    contentDescription = "个人头像",
                    modifier = Modifier.size(76.dp),
                    showContainer = false,
                )
                RemoteImage(
                    url = profile.avatarFrameUrl,
                    contentDescription = "头像框",
                    modifier = Modifier.size(96.dp),
                    showContainer = false,
                    showPlaceholder = false,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "昵称",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = profile.nickname.ifBlank { "昵称待同步" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "当前区 ${profile.areaName.ifBlank { "待同步" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "头像框",
                    color = SemanticColors.gold,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = "生涯赛季",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Box {
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small,
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.small,
                        )
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (seasonLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = selectedSeason.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = "选择赛季",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth(),
        ) {
            RankBlock(
                "当前段位",
                profile.currentRankName,
                profile.currentRankIconUrl,
                Modifier.weight(1f),
            )
            RankBlock(
                "最高段位",
                profile.highestRankName,
                profile.highestRankIconUrl,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RankBlock(
    label: String,
    value: String,
    iconUrl: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = iconUrl,
            contentDescription = label,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = value.ifBlank { "-" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MineStatsPanel(
    profile: PlayerProfile,
    state: HomeViewModel.UiState,
    accountInfo: HomeViewModel.AccountMenuInfo,
) {
    // 角色绑定行与同步状态行已移除（按用户要求）
    TacticalPanel {
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
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(items = mapLabels) { map ->
                FilterChipLazy(label = map, selected = map == selectedMap, onClick = { selectedMap = map })
            }
        }
        if (filtered.isEmpty()) {
            Text(
                text = "暂无出红记录",
                color = SemanticColors.faintText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
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
private fun FilterChipLazy(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (selected) MaterialTheme.colorScheme.primary else SemanticColors.onDarkSurface
    Box(
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .border(1.dp, border, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RedArchiveRow(record: RedArchiveRecord, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                gradeBackgroundColor(record.grade),
                MaterialTheme.shapes.small,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = record.imageUrl,
            contentDescription = record.name,
            modifier = Modifier.size(56.dp),
            showContainer = false,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.name,
                color = SemanticColors.onDarkSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${record.mapName} · ${record.foundTime}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = record.value,
            color = SemanticColors.gold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RedRecordDialog(record: RedArchiveRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.medium,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("出红记录") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            gradeBackgroundColor(record.grade),
                            MaterialTheme.shapes.medium,
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.medium,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(
                        url = record.imageUrl,
                        contentDescription = record.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.2f),
                        showContainer = false,
                    )
                }
                Spacer(modifier = Modifier.padding(top = 10.dp))
                StatusRow("物品", record.name)
                StatusRow("地图", record.mapName.ifBlank { "-" })
                StatusRow("估值", record.value.ifBlank { "-" })
                StatusRow("时间", record.foundTime.ifBlank { "-" })
            }
        },
    )
}
