package com.local.dfcraftmonitor.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 通知权限状态（spec 9.1 Android 13+ POST_NOTIFICATIONS）。
 *
 * Android 13 以下：系统不要求运行时权限，返回 [Status.GRANTED_BY_PLATFORM]。
 * Android 13+：检查 POST_NOTIFICATIONS 实际状态，返回 [GRANTED]/[DENIED]。
 * 首次启动还没问过用户时也是 [DENIED]（denied 且可再申请）。
 */
enum class NotificationPermissionStatus {
    /** 系统不要求此权限（<Android 13），视为已授权 */
    GRANTED_BY_PLATFORM,
    /** 实际已授权 */
    GRANTED,
    /** 被拒但可再申请（系统未禁止） */
    DENIED,
    /** 被拒且系统拒绝再申请（用户勾了"不再询问"） */
    PERMANENTLY_DENIED,
}

object NotificationPermissionState {
    fun check(context: Context): NotificationPermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermissionStatus.GRANTED_BY_PLATFORM
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) NotificationPermissionStatus.GRANTED
        else NotificationPermissionStatus.DENIED
        // 注：检测 PERMANENTLY_DENIED 需要 Activity.shouldShowRequestPermissionRationale()，
        // 不在这里做（util 拿不到 Activity）；UI 层组合。
    }
}
