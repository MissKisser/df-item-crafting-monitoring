package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.local.dfcraftmonitor.data.monitor.WidgetCache

/**
 * 今日密码 Widget (4×1)。
 *
 * 与 TodayProfitWidgetProvider 同形态：onUpdate 只做"从缓存重渲染"，
 * 不发起网络同步。同步由 WidgetRefreshWorker / Configure Activity 写 prefs 后
 * WidgetUpdater.forceUpdateAll() 触发。
 */
class DaySecretWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // force=true：拖放/系统 tick 触发的重渲染需立即显式重建。
        WidgetRemoteViewsApplier.updateAll(
            context, WidgetCache(context).loadForWidget(), force = true,
        )
    }
}
