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
