package com.local.dfcraftmonitor.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住"今日密码 4×1 picker"的核心 cap 行为。
 *
 * 产品决策（与本测试配套）：
 * - 选满 [maxSelection] 时**用户必须先取消一个才能勾新的**——不允许自动踢旧的塞新的。
 *   这是为了"我点哪个就动哪个"的确定性体验；自动挤掉会让人困惑。
 * - 选满时点未勾选行 → 拒绝（[DaySecretPickerState.ToggleResult.RejectedAtCap]），
 *   UI 层应弹 Snackbar 提示用户。
 * - "全选"在 [available] 数量 > 上限时**仍执行**——按入参顺序取前 [maxSelection] 个
 *   填入。语义是"快速填上可见列表的前 N 个"，不应被拒绝。
 * - 任何时候 `snapshotForSave()` 永远不超过 [maxSelection]（防御性兜底）。
 */
class DaySecretPickerStateTest {

    @Test fun initial_emptyState_isAtCapFalse() {
        val s = DaySecretPickerState(maxSelection = 4)
        assertTrue(s.selected.isEmpty())
        assertFalse(s.isAtCap)
    }

    @Test fun initial_withExistingSelection_doesNotExceedCap() {
        val s = DaySecretPickerState(maxSelection = 4, initial = setOf("A", "B"))
        assertEquals(setOf("A", "B"), s.selected)
        assertFalse(s.isAtCap)
    }

    @Test fun toggle_belowCap_addsItem_returnsToggled() {
        val s = DaySecretPickerState(maxSelection = 4, initial = setOf("A", "B", "C"))
        val result = s.toggle("D")
        assertEquals(DaySecretPickerState.ToggleResult.Toggled, result)
        assertEquals(setOf("A", "B", "C", "D"), s.selected)
        assertTrue(s.isAtCap)
    }

    @Test fun toggle_atCap_rejectsNewItem_returnsRejectedAtCap_stateUnchanged() {
        val s = DaySecretPickerState(maxSelection = 4, initial = setOf("A", "B", "C", "D"))
        val result = s.toggle("E")
        assertEquals(DaySecretPickerState.ToggleResult.RejectedAtCap, result)
        // 拒绝时已选集合**不能**被踢——产品决策是用户必须先手动取消。
        assertEquals(setOf("A", "B", "C", "D"), s.selected)
        assertTrue(s.isAtCap)
    }

    @Test fun toggle_atCap_allowsRemovingExistingItem() {
        val s = DaySecretPickerState(maxSelection = 4, initial = setOf("A", "B", "C", "D"))
        val result = s.toggle("B")
        assertEquals(DaySecretPickerState.ToggleResult.Toggled, result)
        assertEquals(setOf("A", "C", "D"), s.selected)
        assertFalse(s.isAtCap)
        // 取消后又能勾新的
        assertEquals(DaySecretPickerState.ToggleResult.Toggled, s.toggle("E"))
    }

    @Test fun toggle_preservesUserOrder() {
        val s = DaySecretPickerState(maxSelection = 4)
        s.toggle("D"); s.toggle("A"); s.toggle("B"); s.toggle("C")
        // 顺序是 LinkedHashSet 真实顺序——不是字典序
        assertEquals(listOf("D", "A", "B", "C"), s.selected.toList())
    }

    @Test fun selectAll_belowCap_returnsPickedCount_andFillsAll() {
        val s = DaySecretPickerState(maxSelection = 4)
        val picked = s.selectAll(listOf("A", "B", "C"))
        assertEquals(3, picked)
        assertEquals(setOf("A", "B", "C"), s.selected)
        assertFalse(s.isAtCap)
    }

    @Test fun selectAll_exactlyAtCap_returnsAll() {
        val s = DaySecretPickerState(maxSelection = 4)
        val picked = s.selectAll(listOf("A", "B", "C", "D"))
        assertEquals(4, picked)
        assertEquals(setOf("A", "B", "C", "D"), s.selected)
        assertTrue(s.isAtCap)
    }

    @Test fun selectAll_aboveCap_takesFirstMaxSelection() {
        // 关键的产品行为：5+ 个地图时"全选"按入参顺序取前 4 个填入，**不**拒绝执行。
        val s = DaySecretPickerState(maxSelection = 4, initial = setOf("A"))
        val picked = s.selectAll(listOf("A", "B", "C", "D", "E"))
        assertEquals(4, picked)
        // 取前 4 个："A", "B", "C", "D"（E 落选）
        assertEquals(setOf("A", "B", "C", "D"), s.selected)
        assertTrue(s.isAtCap)
    }

    @Test fun selectAll_aboveCap_preservesInputOrder_notLexicographic() {
        // 关键行为：按入参顺序，不是字典序。
        // 入参故意是字典反序，确保排序逻辑不是按 compareTo。
        val s = DaySecretPickerState(maxSelection = 3)
        val picked = s.selectAll(listOf("D", "C", "B", "A"))
        assertEquals(3, picked)
        assertEquals(setOf("D", "C", "B"), s.selected)
    }

    @Test fun selectAll_empty_returnsEmpty() {
        val s = DaySecretPickerState(maxSelection = 4)
        val picked = s.selectAll(emptyList())
        assertEquals(0, picked)
        assertTrue(s.selected.isEmpty())
    }

    @Test fun clear_emptiesState() {
        val s = DaySecretPickerState(maxSelection = 4, initial = setOf("A", "B", "C"))
        s.clear()
        assertTrue(s.selected.isEmpty())
        assertFalse(s.isAtCap)
    }

    @Test fun snapshotForSave_respectsCap() {
        // 防御性兜底：万一 UI 误传了一个超过 cap 的集合，save 端再 take 一次。
        val s = DaySecretPickerState(maxSelection = 4)
        s.toggle("A"); s.toggle("B"); s.toggle("C"); s.toggle("D")
        val snap = s.snapshotForSave()
        assertEquals(4, snap.size)
        assertEquals(setOf("A", "B", "C", "D"), snap)
    }

    @Test fun snapshotForSave_emptyWhenNothingSelected() {
        val s = DaySecretPickerState(maxSelection = 4)
        assertTrue(s.snapshotForSave().isEmpty())
    }

    @Test fun isAtCap_reflectsCurrentSizeNotMaxSelection() {
        val s = DaySecretPickerState(maxSelection = 2)
        assertFalse(s.isAtCap)
        s.toggle("A")
        assertFalse(s.isAtCap)
        s.toggle("B")
        assertTrue(s.isAtCap)
        // 选满后第 3 个被拒绝
        assertEquals(DaySecretPickerState.ToggleResult.RejectedAtCap, s.toggle("C"))
    }

    @Test fun maxSelectionZero_orNegative_throws() {
        val exZero = runCatching { DaySecretPickerState(maxSelection = 0) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException for maxSelection=0", exZero is IllegalArgumentException)
        val exNeg = runCatching { DaySecretPickerState(maxSelection = -1) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException for maxSelection=-1", exNeg is IllegalArgumentException)
    }

    @Test fun deselectClearsItemInUserOrder() {
        // 用户取消中间一项后再勾回来，顺序保持之前的相对顺序
        val s = DaySecretPickerState(maxSelection = 4)
        s.toggle("A"); s.toggle("B"); s.toggle("C")
        s.toggle("B")  // 取消
        s.toggle("B")  // 重新勾选——加回末尾
        assertEquals(listOf("A", "C", "B"), s.selected.toList())
    }

    @Test fun unknownNamesAreAllowed() {
        // toggle 不存在的项应当作为"未选"走添加分支（不抛异常）
        val s = DaySecretPickerState(maxSelection = 4)
        assertEquals(DaySecretPickerState.ToggleResult.Toggled, s.toggle("不存在的地图"))
        assertEquals(setOf("不存在的地图"), s.selected)
    }

    @Test fun snapshotForSave_doesNotMutateInternalState() {
        val s = DaySecretPickerState(maxSelection = 4)
        s.toggle("A"); s.toggle("B")
        val before = s.selected.toList()
        s.snapshotForSave()
        val after = s.selected.toList()
        assertEquals(before, after)
    }

    @Test fun rejectedToggle_doesNotMutateState() {
        val s = DaySecretPickerState(maxSelection = 2, initial = setOf("A", "B"))
        s.toggle("C")  // 拒绝
        // 状态完全不变
        assertEquals(setOf("A", "B"), s.selected)
    }

    @Test fun selectAll_afterPartialSelection_replacesEntireSelection() {
        // 全选的语义是"重新选"——覆盖之前已勾的，不是并集。
        val s = DaySecretPickerState(maxSelection = 4, initial = setOf("X", "Y"))
        val picked = s.selectAll(listOf("A", "B", "C"))
        assertEquals(3, picked)
        assertEquals(setOf("A", "B", "C"), s.selected)
        // X、Y 不再是已选
        assertNotEquals(setOf("X", "Y"), s.selected)
    }

    @Test fun constantsAlign() {
        // 文件级常量与状态机内部断言互锁：以后若有人改 DEFAULT_MAX_SELECTION 而忘了状态机，
        // 这个测试会逼着同步改（或反之）。是个轻量的"两个常量对齐"检查。
        assertEquals(4, DEFAULT_MAX_SELECTION)
        val s = DaySecretPickerState(maxSelection = DEFAULT_MAX_SELECTION)
        s.toggle("1"); s.toggle("2"); s.toggle("3"); s.toggle("4")
        assertTrue(s.isAtCap)
        assertEquals(DaySecretPickerState.ToggleResult.RejectedAtCap, s.toggle("5"))
    }
}
