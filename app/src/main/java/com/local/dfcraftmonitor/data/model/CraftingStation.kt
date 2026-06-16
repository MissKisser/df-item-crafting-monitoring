package com.local.dfcraftmonitor.data.model

/**
 * 单个特勤处工位在造什么、造多久、剩多久、均价的快照。
 * data class 自动提供 equals/hashCode/copy/toString，省去 diff/缓存 key 时的手写坑。
 *
 * itemName/iconUrl 在 relateMap 缺失时可能为 null（spike Java 版运行时实际如此）。
 */
data class CraftingStation(
    val type: StationType,
    val placeName: String,
    val status: String,
    val itemId: Long?,
    val itemName: String?,
    val iconUrl: String?,
    val avgPrice: Long?,
    val remainingSeconds: Long?,
    val finishAtEpochSeconds: Long?,
)
