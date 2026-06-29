package com.local.dfcraftmonitor.data.preference

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val keyCraftingNotificationEnabled = booleanPreferencesKey("crafting_notification_enabled")
    private val keyWelcomeShown = booleanPreferencesKey("welcome_shown")
    private val keyWidgetLockedAccountId = stringPreferencesKey("widget_locked_account_id")
    // keyDaySecretWidgetVisibleMaps 复用 UserPrefsStore 的全局唯一定义，
    // 与 WidgetCache 直读路径共享同一 DataStore 实例与 key。

    val userPreferences: Flow<UserPreferences> = context.userPrefsStore.data.map { prefs ->
        UserPreferences(
            craftingNotificationEnabled = prefs[keyCraftingNotificationEnabled] ?: true,
            welcomeShown = prefs[keyWelcomeShown] ?: false,
            widgetLockedAccountId = prefs[keyWidgetLockedAccountId],
            daySecretWidgetVisibleMaps = prefs[keyDaySecretWidgetVisibleMaps] ?: emptySet(),
        )
    }

    suspend fun setCraftingNotificationEnabled(enabled: Boolean) {
        context.userPrefsStore.edit { it[keyCraftingNotificationEnabled] = enabled }
    }

    suspend fun setWelcomeShown(shown: Boolean) {
        context.userPrefsStore.edit { it[keyWelcomeShown] = shown }
    }

    suspend fun setWidgetLockedAccountId(accountId: String?) {
        context.userPrefsStore.edit { prefs ->
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
        context.userPrefsStore.edit { prefs ->
            if (maps.isEmpty()) {
                prefs.remove(keyDaySecretWidgetVisibleMaps)
            } else {
                prefs[keyDaySecretWidgetVisibleMaps] = maps
            }
        }
    }

    suspend fun clear() {
        context.userPrefsStore.edit { it.clear() }
    }
}
