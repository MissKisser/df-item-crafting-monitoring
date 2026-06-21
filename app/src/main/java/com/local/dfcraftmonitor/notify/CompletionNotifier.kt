package com.local.dfcraftmonitor.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.local.dfcraftmonitor.MainActivity
import com.local.dfcraftmonitor.R
import com.local.dfcraftmonitor.data.model.CraftingStation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 制造完成通知（spec 7.4，IMPORTANCE_DEFAULT）。
 *
 * 每工位独立一条通知（按 itemId hashCode 取 notification id）。多个同时完成的工位
 * 在通知抽屉里自然堆叠，spec 7.4 允许但要求"合并后仍需清晰列出完成项"——本实现
 * 选择"独立条目"形态，更简单且符合 Android 通知范式。
 */
@Singleton
class CompletionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyCompleted(station: CraftingStation) {
        val title = "${station.placeName} · 制造完成"
        val body = station.itemName ?: "(无物品名)"

        val openPi = pendingActivityForStation(station)
        val notification = NotificationCompat.Builder(context, NotificationChannels.CRAFTING_COMPLETE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("完成 " + formatTime(station.finishAtEpochSeconds))
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        val id = station.itemId?.let { NotificationChannels.completionNotificationId(it) }
            ?: NotificationChannels.NOTIFICATION_ID_MONITORING + 1
        nm?.notify(id, notification)
    }

    private fun pendingActivityForStation(station: CraftingStation): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(NotificationIntents.EXTRA_ITEM_ID, station.itemId ?: -1L)
            .putExtra(NotificationIntents.EXTRA_STATION_PLACE, station.placeName)
            .putExtra(NotificationIntents.EXTRA_STATION_ITEM, station.itemName)
        return PendingIntent.getActivity(
            context, station.itemId?.toInt() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatTime(epochSeconds: Long?): String {
        if (epochSeconds == null || epochSeconds <= 0) return "-"
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        return fmt.format(Date(epochSeconds * 1000L))
    }
}
