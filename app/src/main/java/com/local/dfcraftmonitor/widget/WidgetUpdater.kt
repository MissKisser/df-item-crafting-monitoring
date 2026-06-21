package com.local.dfcraftmonitor.widget

import android.content.Context
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 桌面卡片统一刷新入口。替代旧版 Glance 的 WidgetRefresher。
 *
 * 调用方：
 * - [com.local.dfcraftmonitor.data.monitor.SyncCoordinator] 同步成功后
 * - [com.local.dfcraftmonitor.ui.home.HomeViewModel] 手动刷新 / 切换账号后
 * - [com.local.dfcraftmonitor.work.CompletionTimerWorker] 制造完成时
 * - [WidgetRefreshReceiver] 刷新按钮点击后（经由 WidgetRefreshWorker → SyncCoordinator）
 *
 * 默认走 [WidgetRemoteViewsApplier] 的变化检测（force=false）以避免无意义重建与闪烁；
 * 手动刷新按钮的即时反馈用 [forceUpdateAll]。
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetCache: WidgetCache,
) {
    /** 刷新全部已放置的 Widget（变化检测，无变化时跳过重建）。 */
    fun updateAll() {
        WidgetRemoteViewsApplier.updateAll(context, widgetCache.loadForWidget(), force = false)
    }

    /** 强制刷新全部 Widget，绕过变化检测（用于手动点刷新按钮的即时反馈）。 */
    fun forceUpdateAll() {
        WidgetRemoteViewsApplier.updateAll(context, widgetCache.loadForWidget(), force = true)
    }
}
