package com.local.dfcraftmonitor.data.remote

import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把 AMS 特勤处接口的 JSON 响应解析成 [CraftingSnapshot]。
 *
 * 从 spike 期的 AmsCraftingParser（Java）迁移而来，逻辑 1:1：
 * - jData.data.data 三层嵌套
 * - placeData[] 每个工位：placeType/placeName/Status/objectId/leftTime/pushTime
 *   （注意 Status 是大写开头，腾讯接口契约，不允许改）
 * - relateMap[objectId] 反查 objectName/pic/avgPrice
 * - nowTime 作服务器当前秒
 *
 * 纯函数式、可单测，无 Android 依赖。
 */
object AmsCraftingParser {
    fun parse(body: String): CraftingSnapshot {
        val root = JSONObject(body)
        val payload = root
            .getJSONObject("jData")
            .getJSONObject("data")
            .getJSONObject("data")
        val relateMap: JSONObject? = payload.optJSONObject("relateMap")
        val placeData: JSONArray? = payload.optJSONArray("placeData")

        val stations = mutableListOf<CraftingStation>()
        if (placeData != null) {
            for (i in 0 until placeData.length()) {
                val item = placeData.getJSONObject(i)
                val objectId = optLongObject(item, "objectId")
                val objectInfo: JSONObject? = if (relateMap != null && objectId != null) {
                    relateMap.optJSONObject(objectId.toString())
                } else {
                    null
                }
                stations += CraftingStation(
                    type = StationType.fromPlaceType(item.optString("placeType", "")),
                    placeName = item.optString("placeName", ""),
                    status = item.optString("Status", ""),
                    itemId = objectId,
                    itemName = objectInfo?.optString("objectName")?.takeIf { it.isNotEmpty() },
                    iconUrl = objectInfo?.optString("pic")?.takeIf { it.isNotEmpty() },
                    avgPrice = objectInfo?.let { optLongObject(it, "avgPrice") },
                    remainingSeconds = optLongObject(item, "leftTime"),
                    finishAtEpochSeconds = optLongObject(item, "pushTime"),
                )
            }
        }

        return CraftingSnapshot.create(
            serverNowEpochSeconds = payload.optLong("nowTime", 0L),
            fetchedAtEpochMillis = System.currentTimeMillis(),
            stations = stations,
        )
    }

    /** 字段不存在或为 null 时返回 null；存在时尝试取 Long。 */
    private fun optLongObject(json: JSONObject, key: String): Long? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optLong(key)
    }
}
