package com.local.dfcraftmonitor.widget

import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPayloadCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun payloadDeserializesOldJsonWithoutDaySecrets() {
        // 老格式 JSON 不含 daySecrets 字段
        val oldJson = """
            {"accountId":"a","nickname":"u","avatarUrl":"","areaName":"","todayProfitValue":0,
             "todayProfitText":"--","stations":[],"fetchedAtEpochMillis":0}
        """.trimIndent()
        val payload = json.decodeFromString<WidgetPayload>(oldJson)
        assertTrue("旧数据应回落到空列表", payload.daySecrets.isEmpty())
    }

    @Test
    fun payloadSerializesWithDefaultsEmptyList() {
        val empty = WidgetPayload.empty("a").copy() // daySecrets default emptyList()
        val text = json.encodeToString(WidgetPayload.serializer(), empty)
        // encodeDefaults=true → 字段始终写入；decode 后拿回同样数据。
        val round = json.decodeFromString<WidgetPayload>(text)
        assertEquals(empty.daySecrets, round.daySecrets)
        assertEquals(empty, round)
    }
}
