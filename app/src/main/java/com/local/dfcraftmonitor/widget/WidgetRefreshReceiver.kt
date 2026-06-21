package com.local.dfcraftmonitor.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.local.dfcraftmonitor.data.monitor.WidgetCache

/**
 * Widget 刷新接收器。
 *
 * 刷新按钮会先用本地缓存恢复桌面卡片，再入队一次同步。
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            restoreAndRequestRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.local.dfcraftmonitor.widget.ACTION_REFRESH"

        fun restoreAndRequestRefresh(context: Context) {
            restoreCachedViews(context)
            requestRefresh(context)
        }

        fun restoreCachedViews(context: Context) {
            // force=true：手动点刷新按钮的即时反馈，单次重建（不再像旧链路串出三连重建）。
            WidgetRemoteViewsApplier.updateAll(context, WidgetCache(context).loadForWidget(), force = true)
        }

        fun requestRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
