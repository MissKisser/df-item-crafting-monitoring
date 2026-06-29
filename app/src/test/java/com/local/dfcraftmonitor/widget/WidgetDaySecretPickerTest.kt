package com.local.dfcraftmonitor.widget

import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 锁住"今日密码 4×1 卡片"的选格逻辑。这是 buildDaySecret 内部的纯函数，
 * 与 RemoteViews 渲染解耦，因此能在不依赖 Robolectric 的纯 JUnit 下测。
 *
 * 规则（spec §3.2）：
 * - payload=null 或 daySecrets 为空 → 全部 4 格显示 "--"
 * - prefs 空 → 按 mapName 字典序填前 4 个，剩余格 "--"
 * - prefs 非空 → 按 prefs 顺序填 4 个；若 prefs 中的某个名字不在 daySecrets 里则该格 "--"
 * - 长地图名 → 由 layout ellipsize 兜底（不在 builder 逻辑里），此处只保证数据正确
 */
class WidgetDaySecretPickerTest {

    private val five = listOf(
        WidgetPayload.DaySecretEntry("巴克什", "1234"),
        WidgetPayload.DaySecretEntry("黎明区", "5678"),
        WidgetPayload.DaySecretEntry("零号大坝", "9012"),
        WidgetPayload.DaySecretEntry("长夜", "3456"),
        WidgetPayload.DaySecretEntry("复苏广场", "7890"),
    )

    @Test fun payloadNull_allCellsUnselected() {
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(null, emptySet())
        assertEquals(4, cells.size)
        assertNull(cells[0]); assertNull(cells[1]); assertNull(cells[2]); assertNull(cells[3])
    }

    @Test fun emptyDaySecrets_allCellsUnselected() {
        val payload = WidgetPayload.empty("a").copy(daySecrets = emptyList())
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(payload, emptySet())
        assertEquals(listOf(null, null, null, null), cells)
    }

    @Test fun prefsEmpty_usesLexicographicTopFour() {
        val payload = WidgetPayload.empty("a").copy(daySecrets = five)
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(payload, emptySet())
        // Kotlin String.compareTo 用 UTF-16 代码单元排序（不是拼音序）。
        // 实测排序：复苏广场 / 巴克什 / 长夜 / 零号大坝 / 黎明区
        assertEquals("复苏广场", cells[0]?.mapName)
        assertEquals("巴克什", cells[1]?.mapName)
        assertEquals("长夜", cells[2]?.mapName)
        assertEquals("零号大坝", cells[3]?.mapName)
    }

    @Test fun prefsNonEmpty_preservesPrefsOrder() {
        val payload = WidgetPayload.empty("a").copy(daySecrets = five)
        val prefs = linkedSetOf("零号大坝", "巴克什", "长夜", "黎明区")  // 故意非字典序
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(payload, prefs)
        assertEquals("零号大坝", cells[0]?.mapName)
        assertEquals("巴克什", cells[1]?.mapName)
        assertEquals("长夜", cells[2]?.mapName)
        assertEquals("黎明区", cells[3]?.mapName)
    }

    @Test fun prefsIncludesUnknownName_thatCellIsNull() {
        val payload = WidgetPayload.empty("a").copy(daySecrets = five)
        val prefs = linkedSetOf("巴克什", "不存在的地图", "长夜", "黎明区")
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(payload, prefs)
        assertEquals("巴克什", cells[0]?.mapName)
        assertNull(cells[1])            // "不存在的地图" 找不到
        assertEquals("长夜", cells[2]?.mapName)
        assertEquals("黎明区", cells[3]?.mapName)
    }

    @Test fun overflow_threeEntriesOneCellUnselected() {
        val three = listOf(
            WidgetPayload.DaySecretEntry("巴克什", "1234"),
            WidgetPayload.DaySecretEntry("黎明区", "5678"),
            WidgetPayload.DaySecretEntry("零号大坝", "9012"),
        )
        val payload = WidgetPayload.empty("a").copy(daySecrets = three)
        // prefs 三选三，第 4 格 null
        val prefs = linkedSetOf("巴克什", "黎明区", "零号大坝")
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(payload, prefs)
        assertEquals("巴克什", cells[0]?.mapName)
        assertEquals("黎明区", cells[1]?.mapName)
        assertEquals("零号大坝", cells[2]?.mapName)
        assertNull(cells[3])
    }

    @Test fun overflow_noPrefsOnlyThree_lexicographicTopsFill3() {
        val three = listOf(
            WidgetPayload.DaySecretEntry("巴克什", "1234"),
            WidgetPayload.DaySecretEntry("黎明区", "5678"),
            WidgetPayload.DaySecretEntry("零号大坝", "9012"),
        )
        val payload = WidgetPayload.empty("a").copy(daySecrets = three)
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(payload, emptySet())
        assertEquals("巴克什", cells[0]?.mapName)
        assertEquals("零号大坝", cells[1]?.mapName)
        assertEquals("黎明区", cells[2]?.mapName)
        assertNull(cells[3])
    }

    @Test fun longMapName_doesNotCorruptData() {
        val one = listOf(WidgetPayload.DaySecretEntry("新赛季限定·复苏广场", "1234"))
        val payload = WidgetPayload.empty("a").copy(daySecrets = one)
        val cells = WidgetRemoteViewsBuilder.pickDaySecretCells(payload, setOf("新赛季限定·复苏广场"))
        assertEquals("新赛季限定·复苏广场", cells[0]?.mapName)
        assertEquals("1234", cells[0]?.secret)
        // layout 中 map_name_* 已 ellipsize=end，渲染层兜底
        assertNull(cells[1])
    }
}
