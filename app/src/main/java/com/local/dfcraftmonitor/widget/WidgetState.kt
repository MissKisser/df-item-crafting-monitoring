package com.local.dfcraftmonitor.widget

import com.local.dfcraftmonitor.data.model.CraftingStation

/**
 * Widget 渲染用的精简状态，从 SnapshotCache 转换而来。
 *
 * - [stations]：最多 4 个工位摘要
 * - [fetchedAtEpochMillis]：数据拉取时间，用于新鲜度判断
 * - [isStale]：spec 8.2 新鲜度规则——超过 15 分钟视为过时
 */
data class WidgetState(
    val stations: List<StationInfo>,
    val fetchedAtEpochMillis: Long,
    val isStale: Boolean,
) {
    data class StationInfo(
        val placeName: String,
        val itemName: String?,
        val remainingSeconds: Long?,
        val status: String,
    )

    companion object {
        /** 数据过时阈值（毫秒），spec 8.2：15 分钟 */
        const val STALE_THRESHOLD_MS = 15L * 60 * 1000

        fun fromStations(
            stations: List<CraftingStation>,
            fetchedAtEpochMillis: Long,
            nowEpochMillis: Long,
        ): WidgetState {
            val isStale = (nowEpochMillis - fetchedAtEpochMillis) > STALE_THRESHOLD_MS
            return WidgetState(
                stations = stations.map { s ->
                    StationInfo(
                        placeName = s.placeName,
                        itemName = s.itemName,
                        remainingSeconds = s.remainingSeconds,
                        status = s.status,
                    )
                },
                fetchedAtEpochMillis = fetchedAtEpochMillis,
                isStale = isStale,
            )
        }
    }
}
