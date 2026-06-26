package com.local.dfcraftmonitor.work

import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CompletionTimerScheduler.selectSchedulableStations] 纯函数单测。
 *
 * 重点验证首次同步漏通知 bug 的修复：当工位 finishAt <= now（首次同步就已完成 /
 * 系统时钟回退 / Worker 被 Doze 拖延），不再被静默丢弃，而是以 delaySeconds <= 0
 * 入结果列表，由调度器钳到 0 立即入队。
 *
 * 注意：纯函数本身返回原始 [ScheduledStation.delaySeconds]（可能为负），由
 * [CompletionTimerScheduler.scheduleOne] 负责钳到 0。这样测试可以断言
 * "负延迟被保留为负"，从而区分"静默丢弃"与"立即触发"两种语义。
 */
class CompletionTimerSchedulerTest {

    private fun station(
        itemId: Long = 1L,
        remainingSeconds: Long = 100L,
        finishAtEpochSeconds: Long = 2000L,
    ) = CraftingStation(
        type = StationType.TECHNOLOGY_CENTER,
        placeName = "技术中心",
        status = "1",
        itemId = itemId,
        itemName = "X",
        iconUrl = null,
        avgPrice = null,
        remainingSeconds = remainingSeconds,
        finishAtEpochSeconds = finishAtEpochSeconds,
    )

    private fun snapshot(vararg stations: CraftingStation) =
        CraftingSnapshot.create(1000L, 2000L, stations.toList())

    @Test
    fun futureStationWithinThresholdIsScheduled() {
        val now = 1000L
        val s = snapshot(station(itemId = 100, remainingSeconds = 600, finishAtEpochSeconds = 1600L))
        val result = CompletionTimerScheduler.selectSchedulableStations(s, now)
        assertEquals(1, result.size)
        assertEquals(100L, result.single().station.itemId)
        assertEquals(600L, result.single().delaySeconds)
    }

    @Test
    fun stationBeyondThresholdIsSkipped() {
        // remainingSeconds = 1800 > 900 阈值，不在精确计时窗口
        val now = 1000L
        val s = snapshot(station(itemId = 100, remainingSeconds = 1800, finishAtEpochSeconds = 2800L))
        assertTrue(
            CompletionTimerScheduler.selectSchedulableStations(s, now).isEmpty()
        )
    }

    @Test
    fun alreadyCompletedStationIsSkipped() {
        // remainingSeconds <= 0：已完成，不需调度
        val now = 1000L
        val s = snapshot(station(itemId = 100, remainingSeconds = 0, finishAtEpochSeconds = 1500L))
        assertTrue(
            CompletionTimerScheduler.selectSchedulableStations(s, now).isEmpty()
        )
    }

    /**
     * 核心 bug 修复用例：工位首次同步时已完成（finishAt 已过去）。
     * 旧实现：delaySeconds <= 0 → 静默丢弃 → 用户收不到任何通知。
     * 新实现：保留在结果列表，delaySeconds 为负 → scheduleOne 钳到 0 立即触发。
     */
    @Test
    fun firstSyncAlreadyExpiredStationIsImmediatelyFired() {
        val now = 2000L
        // finishAt = 1500，已经过去 500s；remaining 仍 > 0 表示服务器还没刷新成完成态
        val s = snapshot(
            station(itemId = 100, remainingSeconds = 100, finishAtEpochSeconds = 1500L)
        )
        val result = CompletionTimerScheduler.selectSchedulableStations(s, now)
        assertEquals("过期工位不应被静默丢弃", 1, result.size)
        assertEquals(100L, result.single().station.itemId)
        assertTrue(
            "延迟应 <= 0 触发立即执行，实际=${result.single().delaySeconds}",
            result.single().delaySeconds <= 0L,
        )
    }

    @Test
    fun stationWithNullFinishAtIsSkipped() {
        val now = 1000L
        val s = snapshot(
            CraftingStation(
                type = StationType.TECHNOLOGY_CENTER, placeName = "技术中心", status = "1",
                itemId = 100L, itemName = "X", iconUrl = null, avgPrice = null,
                remainingSeconds = 300L, finishAtEpochSeconds = null,
            )
        )
        assertTrue(
            CompletionTimerScheduler.selectSchedulableStations(s, now).isEmpty()
        )
    }

    @Test
    fun stationWithNullItemIdIsSkipped() {
        val now = 1000L
        val s = snapshot(
            CraftingStation(
                type = StationType.TECHNOLOGY_CENTER, placeName = "技术中心", status = "1",
                itemId = null, itemName = "X", iconUrl = null, avgPrice = null,
                remainingSeconds = 300L, finishAtEpochSeconds = 1300L,
            )
        )
        assertTrue(
            CompletionTimerScheduler.selectSchedulableStations(s, now).isEmpty()
        )
    }

    @Test
    fun nullRemainingSecondsIsSkipped() {
        val now = 1000L
        val s = snapshot(
            CraftingStation(
                type = StationType.TECHNOLOGY_CENTER, placeName = "技术中心", status = "1",
                itemId = 100L, itemName = "X", iconUrl = null, avgPrice = null,
                remainingSeconds = null, finishAtEpochSeconds = 1300L,
            )
        )
        assertTrue(
            CompletionTimerScheduler.selectSchedulableStations(s, now).isEmpty()
        )
    }

    @Test
    fun mixOfSchedulableExpiredAndSkippedStations() {
        val now = 2000L
        val s = snapshot(
            // 1) 正常调度：10 分钟后完成
            station(itemId = 100, remainingSeconds = 600, finishAtEpochSeconds = 2600L),
            // 2) 立即触发：finishAt 已过去（首次同步漏通知 bug 场景）
            station(itemId = 200, remainingSeconds = 100, finishAtEpochSeconds = 1500L),
            // 3) 超出阈值，跳过
            station(itemId = 300, remainingSeconds = 1800, finishAtEpochSeconds = 3800L),
            // 4) 已完成，跳过
            station(itemId = 400, remainingSeconds = 0, finishAtEpochSeconds = 1500L),
        )
        val result = CompletionTimerScheduler.selectSchedulableStations(s, now)
        assertEquals(2, result.size)
        val ids = result.mapNotNull { it.station.itemId }.sorted()
        assertEquals(listOf(100L, 200L), ids)
        // 验证 200 号确实是"立即触发"语义
        val fired200 = result.single { it.station.itemId == 200L }
        assertTrue(
            "200 号过期工位应被立即触发，实际 delay=${fired200.delaySeconds}",
            fired200.delaySeconds <= 0L,
        )
    }

    @Test
    fun exactlyAtThresholdIsIncluded() {
        val now = 1000L
        // remainingSeconds = 900 = THRESHOLD_SECONDS，边界值应包含
        val s = snapshot(station(itemId = 100, remainingSeconds = 900, finishAtEpochSeconds = 1900L))
        val result = CompletionTimerScheduler.selectSchedulableStations(s, now)
        assertEquals(1, result.size)
    }
}
