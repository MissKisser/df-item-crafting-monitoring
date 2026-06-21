package com.local.dfcraftmonitor.data.monitor

import kotlinx.serialization.Serializable

/**
 * Widget 渲染所需的精简数据载荷，按账号缓存在 [WidgetCache] 中。
 *
 * stations 中的 finishAtEpochSeconds 用于 RemoteViews Chronometer 倒计时，
 * 系统每秒自动递减显示，无需 app 进程存活。
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
) {
    @Serializable
    data class WidgetStation(
        val placeName: String,
        val itemName: String?,
        val finishAtEpochSeconds: Long?,
        val remainingSeconds: Long?,
        val status: String,
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
