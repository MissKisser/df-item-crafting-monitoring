package com.local.dfcraftmonitor.data.preference

/**
 * 用户偏好。
 *
 * - [craftingNotificationEnabled]：制造完成通知开关
 * - [welcomeShown]：隐私说明是否已展示过
 * - [widgetLockedAccountId]：桌面卡片锁定显示的账号 ID，null 表示跟随当前账号
 * - [daySecretWidgetVisibleMaps]："今日密码 4×1 桌面卡"显示的地图名集合；
 *   空集代表未指定——Widget 渲染层会用全部地图按字典序兜底显示前 4 个。
 */
data class UserPreferences(
    val craftingNotificationEnabled: Boolean = true,
    val welcomeShown: Boolean = false,
    val widgetLockedAccountId: String? = null,
    val daySecretWidgetVisibleMaps: Set<String> = emptySet(),
)
