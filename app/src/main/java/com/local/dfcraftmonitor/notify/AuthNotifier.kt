package com.local.dfcraftmonitor.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.local.dfcraftmonitor.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 授权状态通知（spec 7.4 / 9.3 / 11.1）。两种场景：
 * - notifyAuthExpired: 凭据失效，提示用户重新登录
 * - notifyLoggedOut: 用户主动退出登录后，SessionHolder 变空，通知用户后台已停
 */
@Singleton
class AuthNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyAuthExpired(reason: String) {
        val openPi = pendingActivity(0)
        val notification = NotificationCompat.Builder(context, NotificationChannels.AUTH_STATE)
            .setSmallIcon(0)  // TODO M2.1
            .setContentTitle("登录已失效")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\n请重新登录以恢复后台监控。"))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.notify(NotificationChannels.NOTIFICATION_ID_AUTH, notification)
    }

    fun notifyLoggedOut() {
        val openPi = pendingActivity(0)
        val notification = NotificationCompat.Builder(context, NotificationChannels.AUTH_STATE)
            .setSmallIcon(0)  // TODO M2.1
            .setContentTitle("后台监控已停止")
            .setContentText("已退出登录。重新登录以恢复特勤处监控。")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.notify(NotificationChannels.NOTIFICATION_ID_AUTH, notification)
    }

    fun clear() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(NotificationChannels.NOTIFICATION_ID_AUTH)
    }

    private fun pendingActivity(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
