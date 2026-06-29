package com.local.dfcraftmonitor.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * `user_prefs` 的全局 DataStore 委托与共享 key 的唯一来源。
 *
 * DataStore 契约：**同名文件只能有一个活跃委托实例**，否则在进程内同时激活两个实例时会抛
 * `"There are multiple DataStores active for the same file"`（见 androidx.datastore 1.1.x）。
 * 因此本文件的顶层委托必须是全仓 `user_prefs` 的**唯一 owner**——所有读写方
 * （[UserPreferencesRepository] 写入、`data.monitor.WidgetCache` 只读直读）都必须引用它，
 * 严禁在其它文件再声明 `preferencesDataStore(name = "user_prefs")`。
 *
 * 历史上 `WidgetCache` 曾为"避免与 data.preference 循环引用"而另开同名委托——但实测
 * data.preference 是叶子包（无本仓 import），不存在循环依赖；故改为集中到此单文件。
 */
internal val Context.userPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** "今日密码 4×1 桌面卡"已选地图名集合的 key，全局唯一定义。 */
internal val keyDaySecretWidgetVisibleMaps = stringSetPreferencesKey("day_secret_widget_visible_maps")
