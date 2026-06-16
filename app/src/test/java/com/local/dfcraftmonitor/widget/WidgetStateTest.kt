package com.local.dfcraftmonitor.widget

import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WidgetState 纯逻辑测试：fromStations 转换 + 新鲜度规则（spec 8.2）。
 */
class WidgetStateTest {

    private val station1 = CraftingStation(
        type = StationType.ARMORY,
        placeName = "武器工位",
        status = "crafting",
        itemId = 1001L,
        itemName = "突击步枪",
        iconUrl = null,
        avgPrice = 500L,
        remainingSeconds = 3600L,
        finishAtEpochSeconds = 1700000000L,
    )

    private val station2 = CraftingStation(
        type = StationType.WORKBENCH,
        placeName = "防具工位",
        status = "idle",
        itemId = null,
        itemName = null,
        iconUrl = null,
        avgPrice = null,
        remainingSeconds = null,
        finishAtEpochSeconds = null,
    )

    @Test
    fun fromStations_mapsCorrectly() {
        val now = 100_000_000L
        val state = WidgetState.fromStations(
            stations = listOf(station1, station2),
            fetchedAtEpochMillis = 90_000L,
            nowEpochMillis = now,
        )

        assertEquals(2, state.stations.size)

        val ws1 = state.stations[0]
        assertEquals("武器工位", ws1.placeName)
        assertEquals("突击步枪", ws1.itemName)
        assertEquals(3600L, ws1.remainingSeconds)
        assertEquals("crafting", ws1.status)

        val ws2 = state.stations[1]
        assertEquals("防具工位", ws2.placeName)
        assertEquals(null, ws2.itemName)
        assertEquals(null, ws2.remainingSeconds)
        assertEquals("idle", ws2.status)
    }

    @Test
    fun fromStations_freshData_isNotStale() {
        // 数据刚拉 1 分钟，不应过时
        val fetchedAt = 100_000L
        val now = 100_000L + 60_000L  // 1 min later
        val state = WidgetState.fromStations(
            stations = listOf(station1),
            fetchedAtEpochMillis = fetchedAt,
            nowEpochMillis = now,
        )
        assertFalse(state.isStale)
    }

    @Test
    fun fromStations_exactly15min_isNotStale() {
        // 刚好 15 分钟，不超过阈值，不算过时
        val fetchedAt = 100_000L
        val now = 100_000L + WidgetState.STALE_THRESHOLD_MS
        val state = WidgetState.fromStations(
            stations = listOf(station1),
            fetchedAtEpochMillis = fetchedAt,
            nowEpochMillis = now,
        )
        assertFalse(state.isStale)
    }

    @Test
    fun fromStations_over15min_isStale() {
        // 超过 15 分钟，应标记为过时
        val fetchedAt = 100_000L
        val now = 100_000L + WidgetState.STALE_THRESHOLD_MS + 1L
        val state = WidgetState.fromStations(
            stations = listOf(station1),
            fetchedAtEpochMillis = fetchedAt,
            nowEpochMillis = now,
        )
        assertTrue(state.isStale)
    }

    @Test
    fun fromStations_emptyStations() {
        val state = WidgetState.fromStations(
            stations = emptyList(),
            fetchedAtEpochMillis = 100_000L,
            nowEpochMillis = 100_000L,
        )
        assertEquals(0, state.stations.size)
        assertFalse(state.isStale)
    }

    @Test
    fun staleThreshold_is15Minutes() {
        assertEquals(15L * 60 * 1000, WidgetState.STALE_THRESHOLD_MS)
    }
}
