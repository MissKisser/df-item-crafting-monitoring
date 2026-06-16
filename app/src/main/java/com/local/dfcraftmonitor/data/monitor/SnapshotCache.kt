package com.local.dfcraftmonitor.data.monitor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地缓存最近一份特勤处快照，专用于"未完成→完成" diff。
 *
 * V1 不存历史、不存主数据（主数据还是 M3 架构里即拉即用）。
 * 重启后丢失历史——这是 spec M2 阶段的简化（决策已与用户确认）。
 *
 * 实现：DataStore<Preferences> + 单一 stringPreferencesKey 存 JSON 字符串。
 * 不用 Room/Proto 是因为只 1 份快照 + 单读单写，DataStore 足够。
 */
@Singleton
class SnapshotCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.snapshotStore: DataStore<Preferences> by preferencesDataStore(
        name = "snapshot_cache",
    )

    private val key = stringPreferencesKey("last_snapshot_v1")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 存最近一份快照（覆盖写）。Worker 同步成功后调用。
     * stations 序列化为精简视图避免膨胀。
     */
    fun save(snapshot: CraftingSnapshot) {
        val payload = SnapshotPayload.from(snapshot)
        val text = json.encodeToString(SnapshotPayload.serializer(), payload)
        runBlocking {
            context.snapshotStore.edit { prefs -> prefs[key] = text }
        }
    }

    /**
     * 读最近一份快照，无则返回 null。Worker 同步前调，用于 diff。
     */
    fun load(): CraftingSnapshot? {
        val text = runBlocking {
            context.snapshotStore.data.first()[key]
        } ?: return null
        return try {
            json.decodeFromString(SnapshotPayload.serializer(), text).toSnapshot()
        } catch (e: Exception) {
            // 解析失败（schema 变更）当 null 处理，避免 Worker crash
            null
        }
    }

    fun clear() {
        runBlocking {
            context.snapshotStore.edit { prefs -> prefs.remove(key) }
        }
    }

    @Serializable
    internal data class SnapshotPayload(
        val serverNowEpochSeconds: Long,
        val fetchedAtEpochMillis: Long,
        val stations: List<StationDto>,
    ) {
        @Serializable
        internal data class StationDto(
            val type: String,
            val placeName: String,
            val status: String,
            val itemId: Long?,
            val itemName: String?,
            val iconUrl: String?,
            val avgPrice: Long?,
            val remainingSeconds: Long?,
            val finishAtEpochSeconds: Long?,
        )

        companion object {
            fun from(snapshot: CraftingSnapshot) = SnapshotPayload(
                serverNowEpochSeconds = snapshot.serverNowEpochSeconds,
                fetchedAtEpochMillis = snapshot.fetchedAtEpochMillis,
                stations = snapshot.stations.map { s ->
                    StationDto(
                        type = s.type.name,
                        placeName = s.placeName,
                        status = s.status,
                        itemId = s.itemId,
                        itemName = s.itemName,
                        iconUrl = s.iconUrl,
                        avgPrice = s.avgPrice,
                        remainingSeconds = s.remainingSeconds,
                        finishAtEpochSeconds = s.finishAtEpochSeconds,
                    )
                },
            )
        }

        fun toSnapshot(): CraftingSnapshot = CraftingSnapshot.create(
            serverNowEpochSeconds = serverNowEpochSeconds,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            stations = stations.map { d ->
                CraftingStation(
                    type = runCatching { StationType.valueOf(d.type) }
                        .getOrDefault(StationType.UNKNOWN),
                    placeName = d.placeName,
                    status = d.status,
                    itemId = d.itemId,
                    itemName = d.itemName,
                    iconUrl = d.iconUrl,
                    avgPrice = d.avgPrice,
                    remainingSeconds = d.remainingSeconds,
                    finishAtEpochSeconds = d.finishAtEpochSeconds,
                )
            },
        )
    }
}
