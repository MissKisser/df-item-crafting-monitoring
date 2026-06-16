package com.local.dfcraftmonitor.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CookieUtils.parseCookieString 工具方法单测。覆盖空/null/空段/等号缺失/
 * 前后空格/等号在值里等边界。
 */
class CookieUtilsTest {

    @Test
    fun parsesSimplePairs() {
        val result = CookieUtils.parseCookieString("a=1; b=2; c=3")
        assertEquals("1", result["a"])
        assertEquals("2", result["b"])
        assertEquals("3", result["c"])
    }

    @Test
    fun returnsEmptyForNullAndEmpty() {
        assertTrue(CookieUtils.parseCookieString(null).isEmpty())
        assertTrue(CookieUtils.parseCookieString("").isEmpty())
    }

    @Test
    fun ignoresEmptySegments() {
        // 连续分号、结尾分号、首尾分号
        val r1 = CookieUtils.parseCookieString("a=1;;b=2;")
        assertEquals(2, r1.size)
        assertEquals("1", r1["a"])
        assertEquals("2", r1["b"])
        val r2 = CookieUtils.parseCookieString(";a=1")
        assertEquals("1", r2["a"])
    }

    @Test
    fun trimsLeadingAndTrailingWhitespace() {
        val r = CookieUtils.parseCookieString("  a = 1  ;  b = 2 ")
        assertEquals("1", r["a"])
        assertEquals("2", r["b"])
    }

    @Test
    fun valuesWithEqualsSignArePreserved() {
        // base64 padding 等会让 value 里出现 '='
        val r = CookieUtils.parseCookieString("token=abc==")
        assertEquals("abc==", r["token"])
    }

    @Test
    fun segmentsWithoutEqualsAreIgnored() {
        val r = CookieUtils.parseCookieString("a=1; orphan; b=2")
        assertEquals(2, r.size)
        assertEquals("1", r["a"])
        assertEquals("2", r["b"])
    }

    @Test
    fun segmentsStartingWithEqualsAreIgnored() {
        val r = CookieUtils.parseCookieString("a=1; =orphan; b=2")
        assertEquals(2, r.size)
    }

    @Test
    fun handlesLongCookieHeaderFromM2Spike() {
        // M2 实测 log 摘出的字段集合（脱敏长度）
        val header =
            "openid=o1234567890; acctype=qc; appid=1110543085; " +
                "access_token=abcdef1234567890abcdef1234567890; " +
                "p_uin=o1234567890; p_skey=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        val r = CookieUtils.parseCookieString(header)
        assertEquals("o1234567890", r["openid"])
        assertEquals("qc", r["acctype"])
        assertEquals("1110543085", r["appid"])
        assertEquals("abcdef1234567890abcdef1234567890", r["access_token"])
        assertEquals("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", r["p_skey"])
    }
}
