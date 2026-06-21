package com.local.dfcraftmonitor.data.preference

/**
 * 用户偏好。
 *
 * - [craftingNotificationEnabled]：制造完成通知开关
 * - [welcomeShown]：隐私说明是否已展示过
 * - [widgetLockedAccountId]：桌面卡片锁定显示的账号 ID，null 表示跟随当前账号
 */
data class UserPreferences(
    val craftingNotificationEnabled: Boolean = true,
    val welcomeShown: Boolean = false,
    val widgetLockedAccountId: String? = null,
)
