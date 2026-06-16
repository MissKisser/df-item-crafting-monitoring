package com.local.dfcraftmonitor.data.model

/**
 * 一次拉取的特勤处制造快照。stations 是不可变列表，对外封装避免下游误改。
 */
data class CraftingSnapshot(
    val serverNowEpochSeconds: Long,
    val fetchedAtEpochMillis: Long,
    val stations: List<CraftingStation>,
)
