package com.local.dfcraftmonitor.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户偏好仓库，DataStore Preferences 单文件 `user_prefs`。
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(
        name = "user_prefs",
    )

    private val keyCraftingNotificationEnabled = booleanPreferencesKey("crafting_notification_enabled")
    private val keyWelcomeShown = booleanPreferencesKey("welcome_shown")
    private val keyWidgetLockedAccountId = stringPreferencesKey("widget_locked_account_id")
    private val keyDaySecretWidgetVisibleMaps = stringSetPreferencesKey("day_secret_widget_visible_maps")

    val userPreferences: Flow<UserPreferences> = context.prefsStore.data.map { prefs ->
        UserPreferences(
            craftingNotificationEnabled = prefs[keyCraftingNotificationEnabled] ?: true,
            welcomeShown = prefs[keyWelcomeShown] ?: false,
            widgetLockedAccountId = prefs[keyWidgetLockedAccountId],
            daySecretWidgetVisibleMaps = prefs[keyDaySecretWidgetVisibleMaps] ?: emptySet(),
        )
    }

    suspend fun setCraftingNotificationEnabled(enabled: Boolean) {
        context.prefsStore.edit { it[keyCraftingNotificationEnabled] = enabled }
    }

    suspend fun setWelcomeShown(shown: Boolean) {
        context.prefsStore.edit { it[keyWelcomeShown] = shown }
    }

    suspend fun setWidgetLockedAccountId(accountId: String?) {
        context.prefsStore.edit { prefs ->
            if (accountId != null) {
                prefs[keyWidgetLockedAccountId] = accountId
            } else {
                prefs.remove(keyWidgetLockedAccountId)
            }
        }
    }

    /**
     * 设置"今日密码 4×1 桌面卡"显示的地图名集合。
     * 空集代表"未指定"——Widget 渲染层会用全部地图按字典序兜底显示前 4 个。
     */
    suspend fun setDaySecretWidgetVisibleMaps(maps: Set<String>) {
        context.prefsStore.edit { prefs ->
            if (maps.isEmpty()) {
                prefs.remove(keyDaySecretWidgetVisibleMaps)
            } else {
                prefs[keyDaySecretWidgetVisibleMaps] = maps
            }
        }
    }

    suspend fun clear() {
        context.prefsStore.edit { it.clear() }
    }
}
