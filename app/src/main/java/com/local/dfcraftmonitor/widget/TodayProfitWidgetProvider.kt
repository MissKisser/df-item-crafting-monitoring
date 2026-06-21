package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.local.dfcraftmonitor.data.monitor.WidgetCache

/**
 * 今日盈亏 Widget (1×1)。
 *
 * onUpdate 只做"从缓存重渲染"，不发起网络同步（同 CraftingDetailWidgetProvider）。
 */
class TodayProfitWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetRemoteViewsApplier.updateAll(context, WidgetCache(context).loadForWidget(), force = true)
    }
}
