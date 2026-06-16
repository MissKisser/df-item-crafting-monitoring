package com.local.dfcraftmonitor.widget.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.local.dfcraftmonitor.widget.WidgetState

/**
 * Widget 主体 Compose 内容。
 *
 * 布局：
 * ┌─────────────────────────────────┐
 * │ 特勤处监控            数据时间  │
 * ├─────────────────────────────────┤
 * │ 工位1: 物品名      剩余 XX:XX  │
 * │ 工位2: 物品名      剩余 XX:XX  │
 * │ 工位3: 物品名      剩余 XX:XX  │
 * │ 工位4: 物品名      剩余 XX:XX  │
 * ├─────────────────────────────────┤
 * │ ⚠ 数据可能过时（>15min时显示） │
 * └─────────────────────────────────┘
 */
@Composable
fun CraftingWidgetContent(state: WidgetState?) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(android.graphics.Color.parseColor("#1A1A2E"))
            .padding(12.dp),
    ) {
        if (state == null || state.stations.isEmpty()) {
            // 无数据状态
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(
                    text = "特勤处监控",
                    style = TextStyle(
                        color = ColorProvider(android.graphics.Color.WHITE),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "暂无数据，请先登录",
                    style = TextStyle(
                        color = ColorProvider(android.graphics.Color.parseColor("#B0B0B0")),
                        fontSize = 13.sp,
                    ),
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                // 标题行
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Text(
                        text = "特勤处监控",
                        style = TextStyle(
                            color = ColorProvider(android.graphics.Color.WHITE),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        text = formatFetchTime(state.fetchedAtEpochMillis),
                        style = TextStyle(
                            color = ColorProvider(android.graphics.Color.parseColor("#808080")),
                            fontSize = 11.sp,
                        ),
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 工位列表
                state.stations.forEach { station ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Text(
                            text = station.placeName,
                            style = TextStyle(
                                color = ColorProvider(android.graphics.Color.parseColor("#CCCCCC")),
                                fontSize = 12.sp,
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            text = station.itemName ?: "空闲",
                            style = TextStyle(
                                color = ColorProvider(android.graphics.Color.WHITE),
                                fontSize = 12.sp,
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            text = formatRemaining(station.remainingSeconds),
                            style = TextStyle(
                                color = ColorProvider(getRemainingColor(station.remainingSeconds)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                // 过时提示（spec 8.2）
                if (state.isStale) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "⚠ 数据可能过时",
                        style = TextStyle(
                            color = ColorProvider(android.graphics.Color.parseColor("#FFA500")),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }
}

/** 格式化剩余时间：null→"--", 0或负→"已完成", 正数→"HH:MM:SS" 或 "MM:SS" */
private fun formatRemaining(seconds: Long?): String {
    if (seconds == null) return "--"
    if (seconds <= 0) return "已完成"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

/** 剩余时间对应颜色：已完成=绿, <5min=红, <15min=橙, 其他=白 */
private fun getRemainingColor(seconds: Long?): Int {
    if (seconds == null) return android.graphics.Color.parseColor("#808080")
    if (seconds <= 0) return android.graphics.Color.parseColor("#4CAF50")
    if (seconds < 300) return android.graphics.Color.parseColor("#F44336")
    if (seconds < 900) return android.graphics.Color.parseColor("#FF9800")
    return android.graphics.Color.WHITE
}

/** 格式化拉取时间为相对时间 */
private fun formatFetchTime(epochMillis: Long): String {
    val ago = System.currentTimeMillis() - epochMillis
    return when {
        ago < 60_000 -> "刚刚"
        ago < 3600_000 -> "${ago / 60_000}分钟前"
        ago < 86400_000 -> "${ago / 3600_000}小时前"
        else -> DateUtils.getRelativeTimeSpanString(
            epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
}
