package com.local.dfcraftmonitor.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.local.dfcraftmonitor.MainActivity
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 常驻监控通知（spec 7.4，IMPORTANCE_LOW）。
 *
 * 内容（spec 7.4 必须含）：
 * - 监控是否开启
 * - 最近同步时间
 * - 下一项即将完成
 * - 至少一个快捷操作（暂停 / 手动刷新 / 打开）
 *
 * 这里简化为：4 工位摘要 + 下一项完成倒计时 + 暂停/打开两个 action。
 */
@Singleton
class MonitoringNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyMonitoring(snapshot: CraftingSnapshot) {
        val nextStation = snapshot.stations
            .filter { (it.remainingSeconds ?: 0) > 0 }
            .minByOrNull { it.finishAtEpochSeconds ?: Long.MAX_VALUE }

        val title = "特勤处监控"
        val body = buildString {
            append("工位 ").append(snapshot.stations.size).append(" 个")
            if (nextStation != null) {
                append(" · 下一项 ")
                append(nextStation.itemName ?: nextStation.placeName)
                append(" ")
                append(formatRemaining(nextStation))
            }
        }

        val openPi = pendingActivity(MainActivity::class.java, 0)
        val pausePi = pendingBroadcast(NotificationIntents.ACTION_PAUSE_SYNC, 1)

        val notification = NotificationCompat.Builder(context, NotificationChannels.MONITORING)
            .setSmallIcon(0)  // TODO M2.1: 加专用通知图标资源
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("最近同步 " + formatTime(snapshot.fetchedAtEpochMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .addAction(0, "打开", openPi)
            .addAction(0, "暂停", pausePi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.notify(NotificationChannels.NOTIFICATION_ID_MONITORING, notification)
    }

    fun clear() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(NotificationChannels.NOTIFICATION_ID_MONITORING)
    }

    private fun pendingActivity(cls: Class<*>, requestCode: Int): PendingIntent {
        val intent = Intent(context, cls)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pendingBroadcast(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatRemaining(station: CraftingStation): String {
        val remaining = station.remainingSeconds ?: return "未知"
        if (remaining <= 0) return "已完成"
        val h = remaining / 3600
        val m = (remaining % 3600) / 60
        return if (h > 0) "${h}时${m}分" else "${m}分"
    }

    private fun formatTime(epochMillis: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.CHINA)
        return fmt.format(Date(epochMillis))
    }
}
