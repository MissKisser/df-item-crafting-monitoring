package com.local.dfcraftmonitor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class G_tkCalculatorTest {

    @Test
    public void emptySkeyReturnsQqFallbackConstant() {
        // QQ 前端 getACSRFToken 对空串的实际兜底返回 54048。
        assertEquals(54048, G_tkCalculator.calc(""));
        assertEquals(54048, G_tkCalculator.calc(null));
    }

    @Test
    public void sameInputProducesSameOutput() {
        // g_tk 必须是确定性的：同一 skey 多次调用结果一致。
        String skey = "@abcDEF123";
        int first = G_tkCalculator.calc(skey);
        int second = G_tkCalculator.calc(skey);
        assertEquals(first, second);
    }

    @Test
    public void resultIsNonNegativeInt() {
        // hash & 0x7FFFFFFF 保证落在 [0, 2^31-1]，g_tk 永远非负。
        for (String skey : new String[]{"a", "AAAA", "1234567890", "@_@-test-skey"}) {
            int gtk = G_tkCalculator.calc(skey);
            assertTrue("g_tk for " + skey + " should be >= 0", gtk >= 0);
        }
    }

    @Test
    public void knownSampleMatchesReferenceImplementation() {
        // 锁定本实现与 QQ 前端公开 hash32_s 公式一致。
        // 公式：hash=5381; hash += (hash<<5) + char; 结果 = hash & 0x7FFFFFFF。
        // 这里用同一公式在测试内独立重算，与 G_tkCalculator.calc 的输出比对。
        String skey = "0123456789abcZ";
        long expected = 5381L;
        for (int i = 0; i < skey.length(); i++) {
            expected += (expected << 5) + skey.charAt(i);
        }
        expected &= 0x7FFFFFFFL;
        assertEquals((int) expected, G_tkCalculator.calc(skey));
    }

    @Test
    public void pickSkeyPrefersPSkeyOverSkey() {
        String cookie = "uin=o123456; skey=fallback-skey; p_skey=primary-skey";
        assertEquals("primary-skey", G_tkCalculator.pickSkeyFromCookie(cookie));
    }

    @Test
    public void pickSkeyFallsBackToSkeyWhenPSkeyAbsent() {
        String cookie = "uin=o123456; skey=fallback-skey";
        assertEquals("fallback-skey", G_tkCalculator.pickSkeyFromCookie(cookie));
    }

    @Test
    public void pickSkeyReturnsEmptyWhenNeitherPresent() {
        assertEquals("", G_tkCalculator.pickSkeyFromCookie("uin=o123456"));
        assertEquals("", G_tkCalculator.pickSkeyFromCookie(""));
        assertEquals("", G_tkCalculator.pickSkeyFromCookie(null));
    }
}
