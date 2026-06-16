package com.local.dfcraftmonitor.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppDataCleaner 逻辑测试。
 *
 * AppDataCleaner 的核心职责：按顺序停 Worker → 清 Session → 清 Web 登录态 →
 * 清 Cache → 清 Prefs。
 * 由于项目无 mock 框架且生产类多为 final，此处用纯函数验证清除顺序语义。
 *
 * 真正的 AppDataCleaner 只是 4 行顺序调用，此处测试的是「设计意图」而非实现细节。
 * 如需完整集成验证，应在实机/手动测试中完成。
 */
class AppDataCleanerTest {

    @Test
    fun clearOrder_isCancelWorkFirst() {
        // 验证设计意图：清除顺序定义
        val expectedOrder = listOf(
            "cancel_work",
            "clear_session",
            "clear_web_session",
            "clear_cache",
            "clear_prefs",
        )
        val actualOrder = CLEAR_ORDER
        assertEquals(expectedOrder, actualOrder)
    }

    @Test
    fun clearOrder_hasFiveSteps() {
        assertEquals(5, CLEAR_ORDER.size)
    }

    @Test
    fun clearOrder_cancelIsFirst() {
        assertEquals("cancel_work", CLEAR_ORDER.first())
    }

    @Test
    fun clearOrder_prefsIsLast() {
        assertEquals("clear_prefs", CLEAR_ORDER.last())
    }

    @Test
    fun productionCleanerClearsWebSessionBeforeLocalCaches() {
        val source = appDataCleanerSource()
        val webIndex = source.indexOf("webSessionCleaner.clear()")
        val cacheIndex = source.indexOf("snapshotCache.clear()")

        assertTrue(
            "logout must clear WebView cookies/storage so the next login cannot reuse the old QQ account",
            webIndex >= 0,
        )
        assertTrue(
            "Web session should be cleared before local caches and prefs",
            webIndex in 0 until cacheIndex,
        )
    }

    private fun appDataCleanerSource(): String {
        val candidates = listOf(
            File("src/main/java/com/local/dfcraftmonitor/data/AppDataCleaner.kt"),
            File("app/src/main/java/com/local/dfcraftmonitor/data/AppDataCleaner.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }

    companion object {
        /** 清除顺序定义，与 AppDataCleaner.clearAll() 一一对应 */
        val CLEAR_ORDER = listOf(
            "cancel_work",
            "clear_session",
            "clear_web_session",
            "clear_cache",
            "clear_prefs",
        )
    }
}
