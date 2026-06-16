package com.local.dfcraftmonitor.data.remote

import com.local.dfcraftmonitor.data.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 解析器单测。fixture 用单行 JSON raw string（多行 raw string 含换行符，org.json 不接受）。
 *
 * 覆盖：
 * - 正常 4 工位（含大小写敏感的 Status 字段、relateMap 反查）
 * - placeData 为空 / 缺失
 * - relateMap 缺失（itemName/iconUrl 应为 null）
 * - nowTime 取服务器时间
 * - 未识别 placeType 落到 UNKNOWN
 */
class AmsCraftingParserTest {

    @Test
    fun parsesAllFourStationsWithRelateMap() {
        val body = """{"ret":0,"iRet":0,"sMsg":"succ","jData":{"data":{"data":{"nowTime":1781569871,"placeData":[{"placeType":"tech","placeName":"技术中心","Status":"1","objectId":1001,"leftTime":16323,"pushTime":1781586194},{"placeType":"workbench","placeName":"工作台","Status":"1","objectId":1002,"leftTime":19865,"pushTime":1781589736},{"placeType":"armory","placeName":"防具台","Status":"1","objectId":1003,"leftTime":23288,"pushTime":1781593159},{"placeType":"pharmacy","placeName":"制药台","Status":"1","objectId":1004,"leftTime":23309,"pushTime":1781593180}],"relateMap":{"1001":{"objectName":"骨架狙击枪托","pic":"//example.com/1.png","avgPrice":50000},"1002":{"objectName":"5.56*45mm M855A1 APC+","pic":"//example.com/2.png","avgPrice":200},"1003":{"objectName":"精英防弹背心","pic":"//example.com/3.png","avgPrice":8000},"1004":{"objectName":"精密护甲维修包","pic":"//example.com/4.png","avgPrice":1200}}}}}}"""
        val snapshot = AmsCraftingParser.parse(body)

        assertEquals(1781569871L, snapshot.serverNowEpochSeconds)
        assertEquals(4, snapshot.stations.size)
        val tech = snapshot.stations[0]
        assertEquals(StationType.TECHNOLOGY_CENTER, tech.type)
        assertEquals("技术中心", tech.placeName)
        assertEquals("1", tech.status)
        assertEquals(1001L, tech.itemId)
        assertEquals("骨架狙击枪托", tech.itemName)
        assertEquals("//example.com/1.png", tech.iconUrl)
        assertEquals(50000L, tech.avgPrice)
        assertEquals(16323L, tech.remainingSeconds)
        assertEquals(1781586194L, tech.finishAtEpochSeconds)
    }

    @Test
    fun emptyPlaceDataProducesEmptyStationsList() {
        val body = """{"jData":{"data":{"data":{"nowTime":100,"placeData":[]}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        assertEquals(0, snapshot.stations.size)
        assertEquals(100L, snapshot.serverNowEpochSeconds)
    }

    @Test
    fun missingPlaceDataYieldsEmptyListWithoutCrashing() {
        val body = """{"jData":{"data":{"data":{"nowTime":100}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        assertEquals(0, snapshot.stations.size)
    }

    @Test
    fun missingRelateMapLeavesItemNameAndIconNull() {
        val body = """{"jData":{"data":{"data":{"nowTime":100,"placeData":[{"placeType":"workbench","placeName":"工作台","Status":"1","objectId":99,"leftTime":10,"pushTime":110}]}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        val s = snapshot.stations.single()
        assertEquals(99L, s.itemId)
        assertNull("itemName must be null when relateMap missing", s.itemName)
        assertNull("iconUrl must be null when relateMap missing", s.iconUrl)
        assertNull(s.avgPrice)
    }

    @Test
    fun unknownPlaceTypeFallsBackToUnknown() {
        val body = """{"jData":{"data":{"data":{"nowTime":1,"placeData":[{"placeType":"futureStation","placeName":"未开工位","Status":"0","leftTime":0,"pushTime":0}]}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        assertEquals(StationType.UNKNOWN, snapshot.stations.single().type)
    }

    @Test
    fun statusFieldIsCaseSensitiveCapitalS() {
        // 这是腾讯接口真实坑：Status 是大写 S。验证 parser 正确取这个 key。
        val body = """{"jData":{"data":{"data":{"nowTime":1,"placeData":[{"placeType":"tech","placeName":"X","Status":"myStatus"}]}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        assertEquals("myStatus", snapshot.stations.single().status)
    }

    @Test
    fun fieldsOptedToStringDefaultToEmpty() {
        val body = """{"jData":{"data":{"data":{"nowTime":1,"placeData":[{"placeType":"tech"}]}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        val s = snapshot.stations.single()
        assertEquals("", s.placeName)
        assertEquals("", s.status)
    }

    @Test
    fun numericFieldsAreNullWhenAbsent() {
        val body = """{"jData":{"data":{"data":{"nowTime":1,"placeData":[{"placeType":"tech","placeName":"X","Status":"1"}]}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        val s = snapshot.stations.single()
        assertNull(s.itemId)
        assertNull(s.remainingSeconds)
        assertNull(s.finishAtEpochSeconds)
    }

    @Test
    fun fetchedAtMillisIsRecent() {
        val body = """{"jData":{"data":{"data":{"nowTime":1,"placeData":[]}}}}"""
        val before = System.currentTimeMillis()
        val snapshot = AmsCraftingParser.parse(body)
        val after = System.currentTimeMillis()
        assertTrue(
            "fetchedAtEpochMillis must be between before and after",
            snapshot.fetchedAtEpochMillis in before..after,
        )
    }

    @Test
    fun objectInfoIsResolvedViaRelateMapEvenIfObjectIdIsStringNumber() {
        // relateMap 的 key 在 JSON 里实际是字符串化的数字。
        val body = """{"jData":{"data":{"data":{"nowTime":1,"placeData":[{"placeType":"tech","placeName":"X","Status":"1","objectId":42}],"relateMap":{"42":{"objectName":"FOUND","pic":"P","avgPrice":99}}}}}}"""
        val snapshot = AmsCraftingParser.parse(body)
        val s = snapshot.stations.single()
        assertEquals("FOUND", s.itemName)
        assertEquals("P", s.iconUrl)
        assertEquals(99L, s.avgPrice)
    }

    // ---- AuthExpiredException 识别（spec 9.3 退避表） ----

    @Test(expected = AmsCraftingParser.AuthExpiredException::class)
    fun retNonZeroWithLoginKeywordThrowsAuthExpired() {
        val body = """{"ret":-1,"sMsg":"您还没有登录","iRet":-1}"""
        AmsCraftingParser.parse(body)
    }

    @Test(expected = AmsCraftingParser.AuthExpiredException::class)
    fun retNonZeroWithNotLoggedInKeywordThrowsAuthExpired() {
        val body = """{"ret":-1,"sMsg":"用户未登录","iRet":-1}"""
        AmsCraftingParser.parse(body)
    }

    @Test(expected = AmsCraftingParser.AuthExpiredException::class)
    fun retNonZeroWithExpiredKeywordThrowsAuthExpired() {
        val body = """{"ret":-1,"sMsg":"授权已过期","iRet":-1}"""
        AmsCraftingParser.parse(body)
    }

    @Test(expected = RuntimeException::class)
    fun retNonZeroWithGenericMessageThrowsRuntimeException() {
        // 不是登录/未登录/授权/失效 类消息 → 通用 RuntimeException
        val body = """{"ret":-1,"sMsg":"系统繁忙","iRet":-1}"""
        AmsCraftingParser.parse(body)
    }

    @Test
    fun retNonZeroFallsBackToMsgKeyIfNoSMsg() {
        val body = """{"ret":-1,"msg":"未登录"}"""
        try {
            AmsCraftingParser.parse(body)
            fail("应该抛 AuthExpiredException")
        } catch (e: AmsCraftingParser.AuthExpiredException) {
            assertTrue(e.message!!.contains("未登录"))
        }
    }

    @Test
    fun authExpiredExceptionMessageIncludesAMSServerText() {
        val body = """{"ret":-1,"sMsg":"请重新登录"}"""
        try {
            AmsCraftingParser.parse(body)
            fail()
        } catch (e: AmsCraftingParser.AuthExpiredException) {
            assertTrue("消息应包含 '请重新登录'", e.message!!.contains("请重新登录"))
        }
    }
}
