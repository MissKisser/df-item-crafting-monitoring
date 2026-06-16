package com.local.dfcraftmonitor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * StationType 映射单测。这是从腾讯接口的 placeType 字符串到内部枚举的契约——
 * 字符串值（"tech"/"workbench"/"pharmacy"/"armory"）是腾讯定义的，不允许改。
 */
class StationTypeTest {

    @Test
    fun mapsAllFourKnownTypes() {
        assertEquals(StationType.TECHNOLOGY_CENTER, StationType.fromPlaceType("tech"))
        assertEquals(StationType.WORKBENCH, StationType.fromPlaceType("workbench"))
        assertEquals(StationType.PHARMACY, StationType.fromPlaceType("pharmacy"))
        assertEquals(StationType.ARMORY, StationType.fromPlaceType("armory"))
    }

    @Test
    fun unknownStringFallsBackToUnknown() {
        assertEquals(StationType.UNKNOWN, StationType.fromPlaceType("futureStation"))
        assertEquals(StationType.UNKNOWN, StationType.fromPlaceType(""))
    }

    @Test
    fun nullFallsBackToUnknown() {
        assertEquals(StationType.UNKNOWN, StationType.fromPlaceType(null))
    }

    @Test
    fun isCaseSensitive() {
        // 接口契约是全小写；"Tech"/"WORKBENCH" 不应被识别（防御性编程）
        assertEquals(StationType.UNKNOWN, StationType.fromPlaceType("Tech"))
        assertEquals(StationType.UNKNOWN, StationType.fromPlaceType("WORKBENCH"))
    }
}
