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
    /**
     * AMS 鉴权失效信号。
     * 当接口返回 ret != 0 且 sMsg 含登录/未登录/授权 等关键词时，
     * Worker 应停止周期同步、通知用户重新登录（spec 9.3 / 11.1）。
     */
    class AuthExpiredException(message: String) : RuntimeException(message)

    fun parse(body: String): CraftingSnapshot {
        val root = JSONObject(body)
        // 在深入解析前先看顶层 ret/iRet/sMsg；ret != 0 时是接口级错误。
        val ret = root.optInt("ret", 0)
        val sMsg = root.optString("sMsg", root.optString("msg", ""))
        if (ret != 0) {
            val text = sMsg.ifEmpty { "AMS ret=$ret" }
            if (AUTH_EXPIRED_KEYWORDS.any { it in text }) {
                throw AuthExpiredException("登录已失效：$text")
            }
            throw RuntimeException("AMS 接口错误：$text")
        }

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
                    // 制造物等级（来自 relateMap.objectPrice[x]）：
                    //   优先 objectInfo.quality > objectInfo.grade > objectInfo.level。
                    //   默认 0（灰）—— UI 层会回退到中性背景。
                    grade = objectInfo?.let {
                        optIntOrNull(it, "quality")
                            ?: optIntOrNull(it, "grade")
                            ?: optIntOrNull(it, "level")
                    } ?: 0,
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

    /** 字段不存在或为 null 时返回 null；存在时尝试取 Int（0 视为缺省，避免等级 0/缺省混淆）。 */
    private fun optIntOrNull(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        val v = json.optInt(key, 0)
        return v.takeIf { it > 0 }
    }

    private val AUTH_EXPIRED_KEYWORDS = listOf("登录", "未登录", "授权", "已过期", "失效")
}
