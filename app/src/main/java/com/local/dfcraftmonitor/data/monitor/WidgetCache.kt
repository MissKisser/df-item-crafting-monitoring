package com.local.dfcraftmonitor.data.monitor

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.dfcraftmonitor.data.backend.LocalDashboardData
import com.local.dfcraftmonitor.data.backend.PlayerProfile
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget 数据缓存的 DataStore 单例。
 *
 * [preferencesDataStore] 的委托必须定义在文件顶层（全局单例），
 * 否则每个 [WidgetCache] 实例都会为同一个 `widget_cache.preferences_pb` 文件
 * 创建独立的 DataStore，DataStore 启动时会抛
 * "There are multiple DataStores active for the same file"，
 * 进而导致 Application.onCreate 崩溃、APP 无法启动。
 *
 * 见 WidgetRefreshReceiver / 各 AppWidgetProvider 会手动 new WidgetCache 的路径。
 */
private val Context.widgetStore by preferencesDataStore(name = "widget_cache")

/**
 * Widget 数据缓存。
 *
 * 使用 DataStore Preferences 存储按账号隔离的 [WidgetPayload]。
 * 同时镜像 currentAccountId 和 widgetLockedAccountId，供 Widget Provider 同步读取。
 *
 * 写入时机：
 * - [SyncCoordinator] 同步成功后（工位数据）
 * - [HomeViewModel] 刷新仪表盘后（资料 + 今日盈亏）
 * - 账号切换 / Widget 锁定变更时（镜像字段）
 */
@Singleton
class WidgetCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun keyForAccount(accountId: String) = stringPreferencesKey("widget_data_$accountId")
    private val keyCurrentAccountId = stringPreferencesKey("current_account_id")
    private val keyLockedAccountId = stringPreferencesKey("widget_locked_account_id")

    /** 保存某账号的 widget 数据。 */
    fun save(accountId: String, payload: WidgetPayload) {
        runBlocking {
            context.widgetStore.edit { it[keyForAccount(accountId)] = json.encodeToString(WidgetPayload.serializer(), payload) }
        }
    }

    /** 读取某账号的 widget 数据，无则返回 null。 */
    fun load(accountId: String): WidgetPayload? {
        val text = runBlocking { context.widgetStore.data.first() }[keyForAccount(accountId)]
            ?: return null
        return runCatching { json.decodeFromString<WidgetPayload>(text) }.getOrNull()
    }

    /** 镜像：当前账号 ID。 */
    fun setCurrentAccountId(accountId: String) {
        runBlocking {
            context.widgetStore.edit { it[keyCurrentAccountId] = accountId }
        }
    }

    /** 镜像：Widget 锁定账号 ID。 */
    fun setLockedAccountId(accountId: String?) {
        runBlocking {
            context.widgetStore.edit { prefs ->
                if (accountId != null) {
                    prefs[keyLockedAccountId] = accountId
                } else {
                    prefs.remove(keyLockedAccountId)
                }
            }
        }
    }

    /**
     * 解析 widget 应显示的账号 ID：
     * 优先取锁定账号，其次取当前账号。
     */
    fun resolveAccountId(): String? {
        val prefs = runBlocking { context.widgetStore.data.first() }
        return prefs[keyLockedAccountId] ?: prefs[keyCurrentAccountId]
    }

    /**
     * 读取 widget 应显示的数据。
     */
    fun loadForWidget(): WidgetPayload? {
        val accountId = resolveAccountId() ?: return null
        return load(accountId)
    }

    /**
     * 从工位快照 + 仪表盘资料构建并保存 WidgetPayload。
     * 如果已有缓存，则合并更新（保留未变更部分）。
     */
    fun updateFromSync(
        accountId: String,
        snapshot: CraftingSnapshot,
        existing: WidgetPayload? = load(accountId),
    ) {
        val payload = WidgetPayload(
            accountId = accountId,
            nickname = existing?.nickname ?: "",
            avatarUrl = existing?.avatarUrl?.normalizedAvatarUrl() ?: "",
            areaName = existing?.areaName ?: "",
            todayProfitValue = existing?.todayProfitValue ?: 0L,
            todayProfitText = existing?.todayProfitText ?: "--",
            stations = snapshot.stations.map { it.toWidgetStation() },
            fetchedAtEpochMillis = System.currentTimeMillis(),
        )
        save(accountId, payload)
    }

    /**
     * 从仪表盘数据更新资料和今日盈亏。
     */
    fun updateFromDashboard(
        accountId: String,
        dashboard: LocalDashboardData,
        existing: WidgetPayload? = load(accountId),
    ) {
        val profile = dashboard.profile
        val todayProfit = calculateTodayProfit(dashboard)
        val payload = WidgetPayload(
            accountId = accountId,
            nickname = profile.nickname.ifBlank { existing?.nickname ?: "" },
            avatarUrl = profile.avatarUrl.normalizedAvatarUrl()
                .ifBlank { existing?.avatarUrl?.normalizedAvatarUrl() ?: "" },
            areaName = profile.areaName.ifBlank { existing?.areaName ?: "" },
            todayProfitValue = todayProfit,
            todayProfitText = formatProfit(todayProfit),
            stations = existing?.stations ?: emptyList(),
            fetchedAtEpochMillis = System.currentTimeMillis(),
        )
        save(accountId, payload)
    }

    fun clear(accountId: String) {
        runBlocking {
            context.widgetStore.edit { it.remove(keyForAccount(accountId)) }
        }
    }

    fun clearAll() {
        runBlocking {
            context.widgetStore.edit { it.clear() }
        }
    }

    private fun CraftingStation.toWidgetStation() = WidgetPayload.WidgetStation(
        placeName = placeName,
        itemName = itemName,
        finishAtEpochSeconds = finishAtEpochSeconds,
        remainingSeconds = remainingSeconds,
        status = status,
    )

    companion object {
        /** 今日盈亏：筛选当天 battleTime 的 netIncomeValue 之和。 */
        fun calculateTodayProfit(dashboard: LocalDashboardData): Long {
            val today = LocalDate.now().toString()
            return dashboard.recentMatches
                .filter { it.battleTime.isTodayBattleTime(today) }
                .sumOf { it.netIncomeValue ?: 0L }
        }

        fun formatProfit(value: Long): String {
            return when {
                value > 0 -> "+${formatNumber(value)}"
                value < 0 -> "-${formatNumber(-value)}"
                else -> "0"
            }
        }

        private fun formatNumber(value: Long): String {
            return when {
                value >= 100_000_000 -> String.format("%.1f亿", value / 100_000_000.0)
                value >= 10_000 -> String.format("%.1f万", value / 10_000.0)
                else -> value.toString()
            }
        }

        private fun String.isTodayBattleTime(today: String): Boolean {
            val value = trim()
            return value.startsWith(today) ||
                value.startsWith("今天") ||
                value.startsWith("今日")
        }
    }
}

private fun String.normalizedAvatarUrl(): String {
    val value = trim()
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://", ignoreCase = true) -> "https://${value.drop(7)}"
        value.startsWith("https://", ignoreCase = true) -> value
        else -> ""
    }
}
