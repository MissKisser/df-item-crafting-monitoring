package com.local.dfcraftmonitor.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AmsCredential 契约单测。重点：
 * - qq() 工厂写死 acctype="qc"
 * - create() 显式四参（修复了 Java 版 fromCookieString 丢失 acctype 的 bug）
 * - cookieHeader() 输出格式契约（与腾讯接口的 qc Cookie 形态对齐）
 * - data class 提供的 equals/hashCode
 */
class AmsCredentialTest {

    @Test
    fun qqFactoryUsesQcAcctype() {
        val c = AmsCredential.qq("openid-123", "appid-456", "token-789")
        assertEquals("openid-123", c.openid)
        assertEquals("appid-456", c.appid)
        assertEquals("token-789", c.accessToken)
        assertEquals("qc", c.acctype)
    }

    @Test
    fun createFactoryPreservesAllFourFields() {
        val c = AmsCredential.create("o", "qc", "a", "t")
        assertEquals("o", c.openid)
        assertEquals("qc", c.acctype)
        assertEquals("a", c.appid)
        assertEquals("t", c.accessToken)
    }

    @Test
    fun cookieHeaderHasContractedOrder() {
        // 顺序敏感：openid/acctype/appid/access_token 是腾讯接口认可的字段序列
        val c = AmsCredential.create("o", "qc", "a", "t")
        assertEquals("openid=o; acctype=qc; appid=a; access_token=t", c.cookieHeader())
    }

    @Test
    fun isCompleteRequiresAllFourFieldsNonBlank() {
        assertTrue(AmsCredential.create("o", "qc", "a", "t").isComplete())
        assertFalse(AmsCredential.create("", "qc", "a", "t").isComplete())
        assertFalse(AmsCredential.create("o", "", "a", "t").isComplete())
        assertFalse(AmsCredential.create("o", "qc", "", "t").isComplete())
        assertFalse(AmsCredential.create("o", "qc", "a", "").isComplete())
        // 空白字符（trim 后）也视为空
        assertFalse(AmsCredential.create(" ", "qc", "a", "t").isComplete())
    }

    @Test
    fun dataClassEqualityIsValueBased() {
        val a = AmsCredential.create("o", "qc", "a", "t")
        val b = AmsCredential.create("o", "qc", "a", "t")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun dataClassInequalityOnAnyField() {
        val base = AmsCredential.create("o", "qc", "a", "t")
        assertNotEquals(base, AmsCredential.create("o2", "qc", "a", "t"))
        assertNotEquals(base, AmsCredential.create("o", "qc2", "a", "t"))
        assertNotEquals(base, AmsCredential.create("o", "qc", "a2", "t"))
        assertNotEquals(base, AmsCredential.create("o", "qc", "a", "t2"))
    }

    @Test
    fun platformDerivesTencentAreaAndGameAppId() {
        val qq = AmsCredential.create("o", "qc", "1110543085", "t").platform
        assertEquals("QQ区", qq.areaName)
        assertEquals("1", qq.sArea)
        assertEquals("101491592", qq.gameAppId)
        assertEquals("qq", qq.channelKey)

        val wechat = AmsCredential.create("o", "wx", "wx1cd4fbe9335888fe", "t").platform
        assertEquals("微信区", wechat.areaName)
        assertEquals("3", wechat.sArea)
        assertEquals("wx1cd4fbe9335888fe", wechat.gameAppId)
        assertEquals("weixin", wechat.channelKey)
    }
}
