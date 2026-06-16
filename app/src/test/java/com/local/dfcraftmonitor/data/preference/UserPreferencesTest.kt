package com.local.dfcraftmonitor.data.preference

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * UserPreferences data class 默认值测试。
 */
class UserPreferencesTest {

    @Test
    fun defaultValues() {
        val prefs = UserPreferences()
        assertEquals(MonitoringMode.STABLE, prefs.monitoringMode)
        assertEquals(false, prefs.lowPowerPolicyEnabled)
        assertEquals(false, prefs.welcomeShown)
    }

    @Test
    fun customValues() {
        val prefs = UserPreferences(
            monitoringMode = MonitoringMode.LOW_POWER,
            lowPowerPolicyEnabled = true,
            welcomeShown = true,
        )
        assertEquals(MonitoringMode.LOW_POWER, prefs.monitoringMode)
        assertEquals(true, prefs.lowPowerPolicyEnabled)
        assertEquals(true, prefs.welcomeShown)
    }

    @Test
    fun monitoringModeValues() {
        // 确认枚举只有两个值，按声明顺序排列
        val modes = MonitoringMode.entries
        assertEquals(2, modes.size)
        assertEquals(MonitoringMode.LOW_POWER, modes[0])
        assertEquals(MonitoringMode.STABLE, modes[1])
    }

    @Test
    fun monitoringModeValueOf() {
        assertEquals(MonitoringMode.STABLE, MonitoringMode.valueOf("STABLE"))
        assertEquals(MonitoringMode.LOW_POWER, MonitoringMode.valueOf("LOW_POWER"))
    }
}
