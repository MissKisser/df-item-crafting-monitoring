package com.local.dfcraftmonitor.data.monitor

import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SyncDiff.completed 纯函数单测（spec 7.4 完成检测）。
 *
 * 规则：旧快照某工位"在做"（remainingSeconds > 0），新快照同 itemId 工位
 * "已完成"（remainingSeconds <= 0）→ 触发完成通知。
 */
class SyncDiffTest {

    private fun station(
        itemId: Long? = 1L,
        type: StationType = StationType.TECHNOLOGY_CENTER,
        placeName: String = "技术中心",
        remainingSeconds: Long? = 100L,
    ) = CraftingStation(
        type = type,
        placeName = placeName,
        status = if ((remainingSeconds ?: 0) <= 0) "0" else "1",
        itemId = itemId,
        itemName = "X",
        iconUrl = null,
        avgPrice = null,
        remainingSeconds = remainingSeconds,
        finishAtEpochSeconds = null,
    )

    private fun snapshot(vararg stations: CraftingStation) =
        CraftingSnapshot.create(1000L, 2000L, stations.toList())

    @Test
    fun emptyOldSnapshotProducesNoCompletion() {
        val old = snapshot()
        val new = snapshot(station(remainingSeconds = 0))
        assertTrue(SyncDiff.completed(old, new).isEmpty())
    }

    /**
     * 首次同步场景：[SyncCoordinator.handleSuccess] 中 `oldSnapshot == null`
     * 时不走 [SyncDiff.completed] 路径（路径 A）——这会漏掉首次同步就已完成
     * 的工位。该测试把这个语义显式锁住：路径 A 在首次同步下确实不会通知，
     * 这类工位依赖 [CompletionTimerScheduler] 的"立即触发"路径（见
     * CompletionTimerSchedulerTest#firstSyncAlreadyExpiredStationIsImmediatelyFired）。
     *
     * 两个测试合在一起构成了对首次同步漏通知 bug 修复的回归保护。
     */
    @Test
    fun firstSyncHasNoOldSnapshotSoCompletionGoesThroughTimerPath() {
        // 这里模拟 SyncCoordinator 里 oldSnapshot == null 的情况：
        // 既然没有 oldSnapshot 可 diff，路径 A 自然不触发——这正是 bug 的源头。
        // 我们用一个空 old snapshot 来近似（语义等价：没有"在做"的工位 → 无 diff）。
        val old = snapshot()
        val new = snapshot(
            station(itemId = 100, remainingSeconds = 100),   // 还在做（服务器返回未刷新）
        )
        // 路径 A 不会通知（合理：没有 inProgress→completed 转变）
        assertTrue(SyncDiff.completed(old, new).isEmpty())
        // 路径 B 由 CompletionTimerScheduler 负责：剩余 <= 900s 触发精确定时器，
        // 若 finishAt 已过期则立即触发。详见 CompletionTimerSchedulerTest。
    }

    @Test
    fun singleStationTransitionFromInProgressToCompletedTriggersNotification() {
        val old = snapshot(station(itemId = 100, remainingSeconds = 3600))
        val new = snapshot(station(itemId = 100, remainingSeconds = 0))
        val completed = SyncDiff.completed(old, new)
        assertEquals(1, completed.size)
        assertEquals(100L, completed.single().itemId)
    }

    @Test
    fun stillInProgressDoesNotTrigger() {
        val old = snapshot(station(itemId = 100, remainingSeconds = 3600))
        val new = snapshot(station(itemId = 100, remainingSeconds = 1800))
        assertTrue(SyncDiff.completed(old, new).isEmpty())
    }

    @Test
    fun multipleCompletedInSingleSync() {
        val old = snapshot(
            station(itemId = 100, remainingSeconds = 3600),
            station(itemId = 200, remainingSeconds = 1800),
            station(itemId = 300, remainingSeconds = 7200),
        )
        val new = snapshot(
            station(itemId = 100, remainingSeconds = 0),       // 完成
            station(itemId = 200, remainingSeconds = 0),       // 完成
            station(itemId = 300, remainingSeconds = 3600),    // 仍进行
        )
        val completed = SyncDiff.completed(old, new)
        assertEquals(2, completed.size)
        assertTrue(completed.any { it.itemId == 100L })
        assertTrue(completed.any { it.itemId == 200L })
    }

    @Test
    fun stationsAddedInNewSnapshotAreNotConsideredCompletion() {
        // 新出现的工位在旧快照里没有 → 不算"完成"
        val old = snapshot(station(itemId = 100, remainingSeconds = 3600))
        val new = snapshot(
            station(itemId = 100, remainingSeconds = 0),
            station(itemId = 999, remainingSeconds = 0),  // 新增
        )
        val completed = SyncDiff.completed(old, new)
        assertEquals(1, completed.size)
        assertEquals(100L, completed.single().itemId)
    }

    @Test
    fun nullItemIdFallsBackToTypePlusPlaceName() {
        // itemId 为 null 时用 type + placeName 配对
        val old = snapshot(
            CraftingStation(
                type = StationType.WORKBENCH, placeName = "工作台", status = "1",
                itemId = null, itemName = "X", iconUrl = null, avgPrice = null,
                remainingSeconds = 3600, finishAtEpochSeconds = null,
            )
        )
        val new = snapshot(
            CraftingStation(
                type = StationType.WORKBENCH, placeName = "工作台", status = "0",
                itemId = null, itemName = "X", iconUrl = null, avgPrice = null,
                remainingSeconds = 0, finishAtEpochSeconds = null,
            )
        )
        assertEquals(1, SyncDiff.completed(old, new).size)
    }

    @Test
    fun nullRemainingSecondsInOldMeansAlreadyCompletedNoNotification() {
        // 旧快照 remainingSeconds 为 null（数据缺失）→ 不算在做 → 不通知
        val old = snapshot(station(itemId = 100, remainingSeconds = null))
        val new = snapshot(station(itemId = 100, remainingSeconds = 0))
        assertTrue(SyncDiff.completed(old, new).isEmpty())
    }
}
