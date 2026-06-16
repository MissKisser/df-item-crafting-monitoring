package com.local.dfcraftmonitor.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户偏好仓库，DataStore Preferences 单文件 `user_prefs`。
 *
 * 暴露 [Flow]<[UserPreferences]> 供 ViewModel / Widget 观察，
 * 以及 suspend 写方法供 UI 一次性修改。
 *
 * 设计决策（已与用户确认）：
 * - 单 DataStore 文件存放所有偏好（V1 仅 3 字段，无拆分必要）
 * - 不使用 Proto DataStore（字段少 + 无嵌套结构，Preferences 足够）
 * - 默认值定义在 [UserPreferences] data class 上，仓库只负责读写
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(
        name = "user_prefs",
    )

    // ── Keys ──────────────────────────────────────────────────────────
    private val keyMonitoringMode = stringPreferencesKey("monitoring_mode")
    private val keyLowPowerPolicyEnabled = booleanPreferencesKey("low_power_policy_enabled")
    private val keyWelcomeShown = booleanPreferencesKey("welcome_shown")

    // ── Read ──────────────────────────────────────────────────────────

    /** 观察 用户偏好 变化，发射时始终非 null（缺失字段取默认值）。 */
    val userPreferences: Flow<UserPreferences> = context.prefsStore.data.map { prefs ->
        UserPreferences(
            monitoringMode = runCatching {
                MonitoringMode.valueOf(prefs[keyMonitoringMode] ?: MonitoringMode.STABLE.name)
            }.getOrDefault(MonitoringMode.STABLE),
            lowPowerPolicyEnabled = prefs[keyLowPowerPolicyEnabled] ?: false,
            welcomeShown = prefs[keyWelcomeShown] ?: false,
        )
    }

    // ── Write ─────────────────────────────────────────────────────────

    suspend fun setMonitoringMode(mode: MonitoringMode) {
        context.prefsStore.edit { it[keyMonitoringMode] = mode.name }
    }

    suspend fun setLowPowerPolicyEnabled(enabled: Boolean) {
        context.prefsStore.edit { it[keyLowPowerPolicyEnabled] = enabled }
    }

    suspend fun setWelcomeShown(shown: Boolean) {
        context.prefsStore.edit { it[keyWelcomeShown] = shown }
    }

    /** 清除全部偏好（退出登录 / 清数据 时调用）。 */
    suspend fun clear() {
        context.prefsStore.edit { it.clear() }
    }
}
