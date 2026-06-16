package com.local.dfcraftmonitor.data.preference

/**
 * 监控模式：影响 WorkManager 轮询间隔与约束策略。
 *
 * - [LOW_POWER]：低电量模式下仍继续轮询（spec 6.2 低电量策略开关开启时的行为）
 * - [STABLE]：默认稳定模式，受 BATTERY_NOT_LOW 约束
 */
enum class MonitoringMode {
    LOW_POWER,
    STABLE,
}

/**
 * 用户偏好，V1 仅 3 个字段。后续版本可扩展（如通知开关、静默时段等）。
 *
 * - [monitoringMode]：监控模式
 * - [lowPowerPolicyEnabled]：低电量策略是否启用（启用后 WorkManager 不受 BATTERY_NOT_LOW 约束）
 * - [welcomeShown]：隐私说明是否已展示过（V1 仅标记，不做首屏强制弹窗）
 */
data class UserPreferences(
    val monitoringMode: MonitoringMode = MonitoringMode.STABLE,
    val lowPowerPolicyEnabled: Boolean = false,
    val welcomeShown: Boolean = false,
)
