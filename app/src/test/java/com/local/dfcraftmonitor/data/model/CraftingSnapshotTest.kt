package com.local.dfcraftmonitor.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * CraftingSnapshot 不可变 + value equality 行为测试。
 *
 * snapshot 只能通过 [CraftingSnapshot.create] 工厂构造，工厂内部用
 * Collections.unmodifiableList 包装 stations。本测试验证：
 * 1. 主构造函数被禁用（private）
 * 2. 通过 create 工厂构造后，运行时也保护（add/remove 抛 UnsupportedOperationException）
 * 3. value equality + copy 行为
 */
class CraftingSnapshotTest {

    private fun sampleStation() = CraftingStation(
        type = StationType.WORKBENCH,
        placeName = "工作台",
        status = "1",
        itemId = 100,
        itemName = "X",
        iconUrl = "p",
        avgPrice = 1000L,
        remainingSeconds = 3600L,
        finishAtEpochSeconds = 1234567890L,
    )

    private fun newSnapshot() = CraftingSnapshot.create(
        serverNowEpochSeconds = 0L,
        fetchedAtEpochMillis = 0L,
        stations = listOf(sampleStation()),
    )

    @Test
    fun stationsListRejectsAddAtRuntime() {
        val s = newSnapshot()
        try {
            (s.stations as java.util.List<CraftingStation>).add(sampleStation())
            fail("stations must be unmodifiable; add() should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(true)
        }
    }

    @Test
    fun stationsListRejectsRemoveAtRuntime() {
        val s = newSnapshot()
        try {
            (s.stations as java.util.List<CraftingStation>).remove(sampleStation())
            fail("stations must be unmodifiable; remove() should throw")
        } catch (e: UnsupportedOperationException) {
            assertTrue(true)
        }
    }

    @Test
    fun dataClassEqualityIsValueBased() {
        val a = CraftingSnapshot.create(100, 200, listOf(sampleStation()))
        val b = CraftingSnapshot.create(100, 200, listOf(sampleStation()))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun dataClassInequalityOnAnyField() {
        val base = CraftingSnapshot.create(100, 200, listOf(sampleStation()))
        assertNotEquals(base, CraftingSnapshot.create(100, 200, emptyList()))
        assertNotEquals(base, CraftingSnapshot.create(999, 200, listOf(sampleStation())))
        assertNotEquals(base, CraftingSnapshot.create(100, 999, listOf(sampleStation())))
    }

    @Test
    fun dataClassCopyWithModifiedFieldPreservesOtherFields() {
        val s = CraftingSnapshot.create(100, 200, listOf(sampleStation()))
        val s2 = s.copy(serverNowEpochSeconds = 999L)
        assertEquals(999L, s2.serverNowEpochSeconds)
        assertEquals(200L, s2.fetchedAtEpochMillis)
        assertEquals(s.stations, s2.stations)
    }

    @Test
    fun stationsListWithSameContentsAreEqual() {
        // List 的 equals 比较元素（顺序敏感）
        val s1 = CraftingSnapshot.create(0, 0, listOf(sampleStation()))
        val s2 = CraftingSnapshot.create(0, 0, listOf(sampleStation()))
        assertEquals(s1, s2)
    }

    @Test
    fun copyAfterConstructionStillImmutable() {
        // copy 出来的 snapshot 也应该保持 unmodifiable 保护
        val original = CraftingSnapshot.create(0, 0, listOf(sampleStation()))
        val copied = original.copy(serverNowEpochSeconds = 1L)
        try {
            (copied.stations as java.util.List<CraftingStation>).add(sampleStation())
            fail("copy() should preserve unmodifiable view")
        } catch (e: UnsupportedOperationException) {
            assertTrue(true)
        }
    }
}

