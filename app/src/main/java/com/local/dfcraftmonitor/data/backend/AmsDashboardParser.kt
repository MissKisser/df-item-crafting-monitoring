package com.local.dfcraftmonitor.data.backend

import org.json.JSONArray
import org.json.JSONObject

object AmsDashboardParser {

    fun parseDaySecrets(body: String): List<DaySecret> {
        val list = payload(body)?.optJSONArray("list") ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val mapName = item.optString("mapName").ifBlank { item.optString("map") }
            val secret = item.optString("secret")
            if (mapName.isBlank() || secret.isBlank()) {
                null
            } else {
                DaySecret(mapName = mapName, secret = secret)
            }
        }
    }

    fun parseToolObjects(body: String): List<ToolObjectSummary> {
        val list = payload(body)?.optJSONArray("list") ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val id = item.optString("objectID").ifBlank { item.optString("id") }
            val name = item.optString("objectName")
            if (id.isBlank() || name.isBlank()) {
                null
            } else {
                ToolObjectSummary(
                    id = id,
                    name = name,
                    category = item.displayCategory(),
                    price = item.optLongOrNull("avgPrice")?.formatWanPrice().orEmpty(),
                    trend = "今日行情",
                    imageUrl = item.optString("pic").ifBlank { item.optString("prePic") },
                )
            }
        }
    }

    fun parseManufacturingPlaces(body: String): List<MapSummary> {
        val list = payload(body)?.optJSONArray("list") ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val name = item.optString("placeName")
                .ifBlank { item.optString("name") }
                .ifBlank { item.optString("place") }
            val key = item.optString("placeType")
                .ifBlank { item.optString("type") }
                .ifBlank { name }
            if (name.isBlank()) {
                null
            } else {
                MapSummary(name = name, routeKey = key)
            }
        }
    }

    private fun JSONObject.displayCategory(): String {
        val primary = optString("primaryClass").toDisplayPrimary()
        val secondary = optString("secondClassCN")
            .ifBlank { optString("thirdClassCN") }
            .ifBlank { optString("secondClass") }
        return listOf(primary, secondary)
            .filter { it.isNotBlank() }
            .joinToString(" / ")
            .ifBlank { "物资" }
    }

    private fun String.toDisplayPrimary(): String = when (this) {
        "gun" -> "枪械"
        "acc" -> "配件"
        "ammo" -> "子弹"
        "protect" -> "防具"
        "props" -> "道具"
        "vehicle" -> "载具"
        else -> this
    }

    private fun payload(body: String): JSONObject? {
        val root = JSONObject(body)
        val ret = root.optInt("ret", 0)
        val iRet = root.optInt("iRet", 0)
        if (ret != 0 || iRet != 0) return null

        val data = root
            .optJSONObject("jData")
            ?.optJSONObject("data")
            ?: return null
        if (data.optInt("code", 0) != 0) return null

        return data.optJSONObject("data")
    }

    private inline fun <reified T> JSONArray.mapObjectsNotNullTyped(
        crossinline block: (JSONObject) -> T?,
    ): List<T> {
        val result = mutableListOf<T>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            block(item)?.let(result::add)
        }
        return result
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun Long.formatWanPrice(): String {
        if (this <= 0L) return ""
        return if (this >= 10_000L) {
            val value = this / 10_000.0
            val formatted = if (value >= 100) {
                value.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
            }
            "${formatted}万"
        } else {
            toString()
        }
    }
}
