package com.local.dfcraftmonitor.data.model

import java.util.Collections

/**
 * 一次拉取的特勤处制造快照。stations 是不可变列表，对外封装避免下游误改。
 *
 * Kotlin List<T> 类型上无 mutating 方法，编译期保证。但为防反射/Java 互操作绕过
 * 强类型，主构造里在 init 阶段用 `Collections.unmodifiableList` 包一层。
 * （data class val 字段不能重赋值，所以用 companion factory 强制走这条路。）
 */
data class CraftingSnapshot private constructor(
    val serverNowEpochSeconds: Long,
    val fetchedAtEpochMillis: Long,
    val stations: List<CraftingStation>,
) {
    companion object {
        /**
         * 工厂方法：唯一对外构造入口，保证 stations 是 unmodifiable view。
         * 同时作为生产代码（[AmsCraftingParser]）和测试代码构造 snapshot 的唯一途径。
         */
        fun create(
            serverNowEpochSeconds: Long,
            fetchedAtEpochMillis: Long,
            stations: List<CraftingStation>,
        ): CraftingSnapshot = CraftingSnapshot(
            serverNowEpochSeconds,
            fetchedAtEpochMillis,
            Collections.unmodifiableList(stations.toList()),
        )
    }
}
