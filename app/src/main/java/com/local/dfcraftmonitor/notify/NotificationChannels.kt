package com.local.dfcraftmonitor.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 通知 channel 常量与注册逻辑。3 个 channel 对应 spec 7.4 三类通知。
 *
 * channel 一旦注册，重要性和名称就是用户在系统设置里能看到的；之后改 channel
 * 设置只对**新装/新升级**生效——已存在用户需手动到系统设置里改。
 */
object NotificationChannels {

    /** 常驻监控通知（低重要性，不响不抢屏） */
    const val MONITORING = "monitoring"

    /** 制造完成通知（默认重要性） */
    const val CRAFTING_COMPLETE = "crafting_complete"

    /** 授权失效通知（默认重要性） */
    const val AUTH_STATE = "auth_state"

    /** 通知 ID 常量，避免散落魔法数 */
    const val NOTIFICATION_ID_MONITORING = 1001
    const val NOTIFICATION_ID_AUTH = 1002
    /** 完成通知 ID 用 itemId hashCode 取正，保证多工位各自一条 */
    fun completionNotificationId(itemId: Long): Int =
        2000 + (itemId.hashCode() and 0x7FFF)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                MONITORING,
                "特勤处监控",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "常驻监控通知：4 工位摘要 + 下一项完成时间"
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CRAFTING_COMPLETE,
                "制造完成",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "物品制造完成提醒"
                setShowBadge(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                AUTH_STATE,
                "账号状态",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "登录失效、接口异常等账号/服务通知"
                setShowBadge(false)
            }
        )
    }
}
