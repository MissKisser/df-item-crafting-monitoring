package com.local.dfcraftmonitor.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 触发桌面卡片刷新的统一入口。
 *
 * 调用方：[com.local.dfcraftmonitor.data.monitor.SyncCoordinator]（Worker 同步成功后）
 * 和 [com.local.dfcraftmonitor.ui.home.HomeViewModel]（手动刷新成功后）。
 *
 * Glance 的 `updateAll` 会重新调用 [CraftingWidget.provideGlance]，
 * 从 SnapshotCache 读取最新数据并渲染。
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun refresh() {
        runCatching {
            CraftingWidget().updateAll(context)
        }
        // 静默失败：Widget 刷新失败不应影响主流程
    }
}
