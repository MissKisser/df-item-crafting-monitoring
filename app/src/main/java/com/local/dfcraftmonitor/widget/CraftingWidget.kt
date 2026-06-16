package com.local.dfcraftmonitor.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import com.local.dfcraftmonitor.data.monitor.SnapshotCache
import com.local.dfcraftmonitor.widget.ui.CraftingWidgetContent

/**
 * 特勤处制造监控桌面卡片（Glance + Compose）。
 *
 * spec 8.1：4×2 卡片，展示 4 工位状态 + 最近完成时间。
 * 数据来源：[SnapshotCache]。
 * 刷新触发：WorkManager 同步成功后 + HomeViewModel 手动刷新后（Step 5 实现）。
 */
class CraftingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 从 SnapshotCache 读取最新快照
        val snapshot = SnapshotCache(context).load()
        val now = System.currentTimeMillis()
        val state = snapshot?.let {
            WidgetState.fromStations(it.stations, it.fetchedAtEpochMillis, now)
        }

        provideContent {
            CraftingWidgetContent(state = state)
        }
    }
}
