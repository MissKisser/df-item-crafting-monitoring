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
    /**
     * 制造物等级（来自 relateMap.objectPrice 对象 grade / level 字段）。
     * 取值约定：1 灰 / 2 绿 / 3 蓝 / 4 紫 / 5 金 / 6 红 / 7 深红。
     * UI 用于决定卡片背景配色，与物品品质保持一致。
     */
    val grade: Int = 0,
)
