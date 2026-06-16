package com.local.dfcraftmonitor.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.dfcraftmonitor.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 通知 action 接收器（spec 7.4 暂停/打开）。
 *
 * Manifest 已声明（receiver 标签）。用 Hilt 注入 WorkScheduler。
 *
 * 当前支持 action：ACTION_PAUSE_SYNC（停止周期 Worker）+ 隐式关掉常驻通知。
 * 后续 spec M3 加 ACTION_RESUME_SYNC / ACTION_RELOGIN。
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var monitoringNotifier: MonitoringNotifier
    @Inject lateinit var authNotifier: AuthNotifier

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationIntents.ACTION_PAUSE_SYNC -> {
                workScheduler.cancel()
                monitoringNotifier.clear()
                authNotifier.clear()
            }
            // ACTION_RESUME_SYNC / ACTION_RELOGIN 留待 spec M3 设置页
        }
    }
}
