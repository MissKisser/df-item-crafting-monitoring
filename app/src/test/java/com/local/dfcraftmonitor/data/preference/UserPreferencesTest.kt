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
        assertEquals(true, prefs.craftingNotificationEnabled)
        assertEquals(false, prefs.welcomeShown)
    }

    @Test
    fun customValues() {
        val prefs = UserPreferences(
            craftingNotificationEnabled = false,
            welcomeShown = true,
        )
        assertEquals(false, prefs.craftingNotificationEnabled)
        assertEquals(true, prefs.welcomeShown)
    }
}
