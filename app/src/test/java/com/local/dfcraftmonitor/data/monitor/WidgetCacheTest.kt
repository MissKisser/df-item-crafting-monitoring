package com.local.dfcraftmonitor.data.monitor

import com.local.dfcraftmonitor.data.backend.LocalDashboardData
import com.local.dfcraftmonitor.data.backend.MatchRecord
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCacheTest {

    @Test
    fun todayProfitIncludesIsoAndChineseTodayRecordsOnly() {
        val today = LocalDate.now().toString()
        val dashboard = LocalDashboardData.empty().copy(
            recentMatches = listOf(
                match(id = "iso", battleTime = "$today 09:00", netIncomeValue = 120_000),
                match(id = "cn", battleTime = "今天 12:00", netIncomeValue = 320_000),
                match(id = "cn2", battleTime = "今日 13:00", netIncomeValue = -20_000),
                match(id = "yesterday", battleTime = "昨天 18:00", netIncomeValue = 500_000),
                match(id = "empty", battleTime = "", netIncomeValue = 99_999),
            ),
        )

        assertEquals(420_000, WidgetCache.calculateTodayProfit(dashboard))
    }

    @Test
    fun formatProfitKeepsSignAndChineseUnits() {
        assertEquals("+42.0万", WidgetCache.formatProfit(420_000))
        assertEquals("-1.2亿", WidgetCache.formatProfit(-120_000_000))
        assertEquals("0", WidgetCache.formatProfit(0))
    }

    private fun match(
        id: String,
        battleTime: String,
        netIncomeValue: Long,
    ) = MatchRecord(
        id = id,
        mapName = "零号大坝",
        modeName = "烽火地带",
        result = "撤离成功",
        netIncome = netIncomeValue.toString(),
        netIncomeValue = netIncomeValue,
        operatorKills = "0",
        duration = "00:00",
        battleTime = battleTime,
    )
}
