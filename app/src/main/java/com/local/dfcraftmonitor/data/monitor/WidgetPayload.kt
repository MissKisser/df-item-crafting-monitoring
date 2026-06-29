package com.local.dfcraftmonitor.data.monitor

import kotlinx.serialization.Serializable

/**
 * Widget 渲染所需的精简数据载荷，按账号缓存在 [WidgetCache] 中。
 *
 * stations 中的 finishAtEpochSeconds 用于 RemoteViews Chronometer 倒计时，
 * 系统每秒自动递减显示，无需 app 进程存活。
 *
 * daySecrets 是新增的"今日密码"列表，供 4×1 桌面卡片渲染；老缓存无此字段时
 * 由 WidgetCache 的 Json 配置（ignoreUnknownKeys=true）反序列化为空列表。
 * 与 data.backend.DaySecret 故意解耦：widget 包不能依赖 backend 包。
 */
@Serializable
data class WidgetPayload(
    val accountId: String,
    val nickname: String,
    val avatarUrl: String,
    val areaName: String,
    val todayProfitValue: Long,
    val todayProfitText: String,
    val stations: List<WidgetStation>,
    val fetchedAtEpochMillis: Long,
    val daySecrets: List<DaySecretEntry> = emptyList(),
) {
    @Serializable
    data class WidgetStation(
        val placeName: String,
        val itemName: String?,
        val finishAtEpochSeconds: Long?,
        val remainingSeconds: Long?,
        val status: String,
    )

    /** 与 data.backend.DaySecret 解耦——widget 包不能依赖 backend 包。 */
    @Serializable
    data class DaySecretEntry(
        val mapName: String,
        val secret: String,
    )

    companion object {
        fun empty(accountId: String) = WidgetPayload(
            accountId = accountId,
            nickname = "",
            avatarUrl = "",
            areaName = "",
            todayProfitValue = 0L,
            todayProfitText = "--",
            stations = emptyList(),
            fetchedAtEpochMillis = 0L,
        )
    }
}
