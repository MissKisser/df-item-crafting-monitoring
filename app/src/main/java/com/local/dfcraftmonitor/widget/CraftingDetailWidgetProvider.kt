package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.local.dfcraftmonitor.data.monitor.WidgetCache

/**
 * 制造详情 Widget (4×1)。
 *
 * 系统在 updatePeriodMillis（15 分钟）间隔或 Widget 被拖放到桌面时调用 onUpdate。
 * 此时从 [WidgetCache] 读取缓存数据构建 RemoteViews。
 * 秒级倒计时由 Chronometer 控件自动处理。
 *
 * 注意：onUpdate 只做"从缓存重渲染"，不再发起网络同步——网络同步归
 * WorkManager 周期任务 + 用户手动点刷新按钮，避免系统 tick 串出多次重建导致闪烁。
 */
class CraftingDetailWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // force=true：拖放/系统周期 tick 触发的重渲染属于"应恢复正确显示"的场景，
        // 显式强制一次，确保卡片立即有内容（不再发起网络同步）。
        WidgetRemoteViewsApplier.updateAll(context, WidgetCache(context).loadForWidget(), force = true)
    }
}
