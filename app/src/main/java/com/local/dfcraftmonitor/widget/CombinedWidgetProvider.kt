package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.local.dfcraftmonitor.data.monitor.WidgetCache

/**
 * 组合 Widget (3×2)：制造详情 + 今日盈亏。
 *
 * onUpdate 只做"从缓存重渲染"，不发起网络同步（同 CraftingDetailWidgetProvider）。
 */
class CombinedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetRemoteViewsApplier.updateAll(context, WidgetCache(context).loadForWidget(), force = true)
    }
}
