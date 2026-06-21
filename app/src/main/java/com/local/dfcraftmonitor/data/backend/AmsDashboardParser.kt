package com.local.dfcraftmonitor.data.backend

import org.json.JSONArray
import org.json.JSONObject

object AmsDashboardParser {

    /**
     * 干员图标 CDN 前缀/后缀。与小程序一致，来自 dfm/config.list 的
     * config.cdnFormat.object.prefix / suffix（如 prefix="https://.../"，suffix="png"）。
     * 该值长期稳定，作为运行时取不到 config 时的兜底。
     */
    internal const val OPERATOR_ICON_PREFIX_FALLBACK =
        "https://game.gtimg.cn/images/dfm/cp/a20240807community/iteration/"
    internal const val OPERATOR_ICON_SUFFIX_FALLBACK = "png"
    internal const val OBJECT_ICON_PREFIX_FALLBACK =
        "https://playerhub.df.qq.com/playerhub/60004/object/"
    internal const val OBJECT_ICON_SUFFIX_FALLBACK = "png"

    /**
     * 与小程序 utils/assetsId.js 完全一致的干员映射表（ArmedForceId → 干员名 / 图标文件名）。
     * 战绩接口（dfm PHq59Y / 详情 ylP3eG）只返回 ArmedForceId，小程序在前端据此查这两张表。
     */
    private val OPERATOR_NAMES_BY_ARMED_FORCE_ID = mapOf(
        40005 to "露娜", 10010 to "威龙", 40010 to "骇爪", 20003 to "蜂医",
        30008 to "牧羊人", 10007 to "红狼", 30009 to "乌鲁鲁", 20004 to "蛊",
        30010 to "深蓝", 10011 to "无名", 10012 to "疾风", 40011 to "银翼",
        30011 to "比特", 20005 to "蝶", 40012 to "回响",
        50001 to "赤枭", 50002 to "赤枭亲卫", 50003 to "赤枭亲卫",
    )
    private val OPERATOR_ICONS_BY_ARMED_FORCE_ID = mapOf(
        40005 to "war-role6", 10010 to "war-role9", 40010 to "war-role4", 20003 to "war-role2",
        30008 to "war-role7", 10007 to "war-role5", 30009 to "war-role10", 20004 to "war-role3",
        30010 to "war-role8", 10011 to "war-role1", 10012 to "war-role11", 40011 to "war-role12",
        30011 to "war-role13", 20005 to "war-role14", 40012 to "war-role15",
    )

    /** 根据 ArmedForceId 解析干员名（无匹配时返回空）。 */
    fun operatorName(armedForceId: Long?): String =
        armedForceId?.let { OPERATOR_NAMES_BY_ARMED_FORCE_ID[it.toInt()] }.orEmpty()

    /**
     * 根据 ArmedForceId 拼接干员头像 URL，与小程序 imgPrefix+sqlImg[ArmedForceId]+"."+imgSuffix 一致。
     * @param prefix cdnFormat.object.prefix；为空时使用兜底前缀
     * @param suffix cdnFormat.object.suffix；为空时使用兜底后缀
     */
    fun operatorIconUrl(
        armedForceId: Long?,
        prefix: String = OPERATOR_ICON_PREFIX_FALLBACK,
        suffix: String = OPERATOR_ICON_SUFFIX_FALLBACK,
    ): String {
        val icon = armedForceId?.let { OPERATOR_ICONS_BY_ARMED_FORCE_ID[it.toInt()] } ?: return ""
        return "$prefix$icon.$suffix"
    }

    fun objectIconUrl(
        objectId: String?,
        prefix: String = OBJECT_ICON_PREFIX_FALLBACK,
        suffix: String = OBJECT_ICON_SUFFIX_FALLBACK,
    ): String {
        val id = objectId?.trim()?.takeIf { it.toLongOrNull() != null } ?: return ""
        return "$prefix$id.$suffix"
    }

    /**
     * 从 dfm/config.list 响应中解析 cdnFormat.object.prefix / suffix。
     * 返回 (prefix, suffix)，取不到时两者均为空（调用方应使用兜底）。
     */
    fun parseCdnFormat(body: String?): CdnFormat {
        if (body.isNullOrBlank()) return CdnFormat("", "")
        return runCatching {
            val config = payloadObject(body)?.optJSONObject("config") ?: return CdnFormat("", "")
            val cdnFormat = config.optJSONObject("cdnFormat") ?: return CdnFormat("", "")
            val objectCdn = cdnFormat.optJSONObject("object") ?: return CdnFormat("", "")
            CdnFormat(
                prefix = objectCdn.optString("prefix"),
                suffix = objectCdn.optString("suffix"),
            )
        }.getOrDefault(CdnFormat("", ""))
    }

    /** CDN 配置（object 图标前缀/后缀）。 */
    data class CdnFormat(val prefix: String, val suffix: String) {
        val isAvailable: Boolean get() = prefix.isNotBlank()
    }

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
                    trend = "",
                    imageUrl = item.optString("pic").ifBlank { item.optString("prePic") },
                    grade = item.firstLong("grade", "ItemQuality")?.toInt() ?: 0,
                )
            }
        }
    }

    fun parsePricePoints(body: String): List<PricePoint> {
        val data = payloadObject(body) ?: return emptyList()
        val mapPoints = data.optJSONObject("dataMap")?.let { dataMap ->
            dataMap.keys().asSequence().mapNotNull { key ->
                val item = dataMap.optJSONObject(key) ?: return@mapNotNull null
                val value = item.firstLong("avgPrice", "price", "objectPrice", "marketPrice", "value")
                    ?: return@mapNotNull null
                PricePoint(
                    label = key,
                    price = value.formatWanPrice(),
                    priceValue = value,
                )
            }.toList()
        }.orEmpty()
        if (mapPoints.isNotEmpty()) return mapPoints

        val list = data.firstArray("list", "priceList", "prices", "data")
            ?: data.optJSONObject("objectPriceHour")?.optJSONArray("list")
            ?: data.optJSONObject("objectPriceRecent")?.optJSONArray("list")
            ?: return emptyList()
        val result = mutableListOf<PricePoint>()
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val value = item.firstLong("avgPrice", "price", "objectPrice", "marketPrice", "value") ?: continue
            val label = item.firstString("hour", "time", "date", "dt", "label", "statDate", "dtstatdate", "createTime")
                .ifBlank { "记录${index + 1}" }
            result += PricePoint(
                label = label,
                price = value.formatWanPrice(),
                priceValue = value,
            )
        }
        return result
    }

    fun parseToolConfigs(body: String): List<ToolConfigItem> {
        val data = payloadObject(body) ?: return emptyList()
        val directList = data.firstArray("list", "configList", "whiteList", "items")
        val configList = data.optJSONObject("config")?.let { config ->
            buildList {
                config.optJSONObject("objectMapping")?.keys()?.forEach { key ->
                    config.optJSONObject("objectMapping")
                        ?.optJSONArray(key)
                        ?.let(::add)
                }
                config.optJSONObject("mapList")?.keys()?.forEach { key ->
                    config.optJSONObject("mapList")
                        ?.optJSONArray(key)
                        ?.let(::add)
                }
            }.flattenJsonArrays()
        }
        val list = directList ?: configList ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val name = item.firstString("name", "label", "title", "configName", "objectName", "mapName", "valueName")
            val id = item.firstString("id", "key", "value", "configKey", "objectID", "objectId", "mapId", "type")
            if (name.isBlank() && id.isBlank()) {
                null
            } else {
                ToolConfigItem(
                    id = id.ifBlank { name },
                    name = name.ifBlank { id },
                    type = item.firstString("type", "group", "category", "configType", "bizType"),
                )
            }
        }
    }

    fun parseManufacturingPlaces(body: String): List<MapSummary> {
        val list = payloadObject(body)?.optJSONArray("list") ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val name = item.optString("placeName")
                .ifBlank { item.optString("name") }
                .ifBlank { item.optString("place") }
                .toDisplayPlace()
            val key = item.optString("placeType")
                .ifBlank { item.optString("type") }
                .ifBlank { item.optString("place") }
                .ifBlank { name }
            if (name.isBlank()) {
                null
            } else {
                MapSummary(name = name, routeKey = key)
            }
        }
    }

    /**
     * 制作推荐。
     *
     * 与三角洲行动小程序一致（来自小程序 wxapkg 反编译）：
     * - 数据源：同一个 `dfm/place.list`（param={type:"place",hasPriceData:true}），无独立推荐接口。
     * - 小程序在前端按公式计算并排序：
     *     profit         = salePrice - costPrice - fee - bail
     *     profitPerHour  = floor(profit / period)
     * - 推荐排序键是「每小时利润」profitPerHour，而不是总利润 profit。
     * - 小程序按台位分组（tech/workbench/medical/protect 等），每个台位展示收益最高的一档。
     *
     * 因此这里：保留全部配方但按 profitPerHour 降序、按 placeType 各取 top1，组合成最终列表。
     */
    fun parseManufacturingRecommendations(body: String): List<ManufacturingRecommendation> {
        val list = payloadObject(body)?.optJSONArray("list") ?: return emptyList()
        val recommendations = mutableListOf<ManufacturingRecommendation>()
        for (index in 0 until list.length()) {
            val place = list.optJSONObject(index) ?: continue
            val placeType = place.firstString("place", "placeType", "type")
            val placeName = place.firstString("placeName", "place")
                .ifBlank { placeType }
                .toDisplayPlace()
            val unlock = place.optJSONObject("placeDetail")?.optJSONObject("unlock") ?: continue
            unlock.collectJsonArrays().forEach { recipes ->
                for (recipeIndex in 0 until recipes.length()) {
                    val recipe = recipes.optJSONObject(recipeIndex) ?: continue
                    val objectId = recipe.firstString("objectID", "objectId", "id")
                    val salePrice = recipe.firstLong("salePrice") ?: continue
                    val costPrice = recipe.firstLong("costPrice") ?: 0L
                    val fee = recipe.firstLong("fee") ?: 0L
                    val bail = recipe.firstLong("bail") ?: 0L
                    // 与小程序一致：净利润要减去保证金 bail
                    val profit = salePrice - costPrice - fee - bail
                    if (objectId.isBlank() || profit <= 0L) continue
                    val periodHours = recipe.firstLong("period")?.let { period ->
                        // period 为小时数（小程序里 +e.period 直接做除数），0 视为无法计算
                        if (period > 0L) period.toDouble() else 0.0
                    } ?: 0.0
                    val profitPerHour = if (periodHours > 0.0) {
                        (profit / periodHours).toLong()
                    } else {
                        0L
                    }
                    recommendations += ManufacturingRecommendation(
                        id = objectId,
                        name = objectId,
                        imageUrl = "",
                        placeName = placeName.ifBlank { "制造" },
                        placeType = placeType,
                        profit = profit.formatWanPrice(),
                        profitValue = profit,
                        salePrice = salePrice.formatWanPrice(),
                        costPrice = costPrice.formatWanPrice(),
                        fee = fee.formatWanPrice(),
                        bail = bail.formatWanPrice(),
                        period = recipe.firstString("period"),
                        perCount = recipe.firstLong("perCount")?.toString().orEmpty(),
                        profitPerHour = profitPerHour.formatWanPrice(),
                        profitPerHourValue = profitPerHour,
                    )
                }
            }
        }
        // 每个台位只保留每小时利润最高的一档（与小程序「每台位推荐一小时收益最高物品」一致），
        // 台位间再按每小时利润降序排列。
        val placePriority = listOf("tech", "workbench", "medical", "protect", "control", "fire", "collect")
        return recommendations
            .groupBy { it.placeType.ifBlank { it.placeName } }
            .values
            .map { placeRecipes -> placeRecipes.maxByOrNull { it.profitPerHourValue }!! }
            .sortedWith(
                compareByDescending<ManufacturingRecommendation> { it.profitPerHourValue }
                    .thenBy { placeType -> placePriority.indexOf(placeType.placeType).let { if (it < 0) placePriority.size else it } },
            )
    }

    fun parsePlayerProfile(body: String): PlayerProfile {
        val data = payloadObject(body) ?: return PlayerProfile.empty()
        val rawNickname = data.firstString(
            "vRoleName", "nickName", "nickname", "sRoleName", "roleName", "name", "userName",
        )
        val uin = data.firstString("uin", "openId", "openid")
        val rawAvatar = data.firstImageUrl(
            "vHeadUrl", "vHeadPic", "headUrl", "headPic", "avatar", "avatarUrl",
            "picUrl", "headIcon", "icon", "head_image", "headImg", "headImgUrl",
            "headimgurl", "headImgURL", "wxHeadImg", "wxHeadImgUrl", "wx_headimgurl",
        )
        return PlayerProfile(
            nickname = rawNickname.ifBlank { uin },
            areaName = data.firstString("areaName", "area", "serverName", "zoneName", "vAreaName", "region"),
            avatarUrl = rawAvatar,
            avatarFrameUrl = data.firstString("avatarFrame", "avatarFrameUrl", "frame", "framePic"),
            currentRankName = data.firstString("curRankName", "currentRankName", "rankName", "rank"),
            currentRankIconUrl = data.firstString("curRankPic", "currentRankIcon", "rankPic", "rankIcon"),
            highestRankName = data.firstString("maxRankName", "highestRankName", "historyMaxRankName", "maxRank"),
            highestRankIconUrl = data.firstString("maxRankPic", "highestRankIcon", "historyMaxRankPic", "maxRankIcon"),
            totalBringOutValue = data.firstLong(
                "totalBringOutValue",
                "bringOutTotalValue",
                "totalValue",
                "totalProfit",
            )?.formatWanPrice().orEmpty(),
            evacuationRate = data.firstString("evacuationRate", "evacRate", "escapeRate", "withdrawRate"),
            operatorKills = data.firstLong("killOperatorNum", "operatorKills", "killNum", "kills")?.toString()
                ?: data.firstString("killOperatorNum", "operatorKills", "killNum", "kills"),
            profitLossRatio = data.firstString("profitLossRatio", "earnLossRatio", "profitRatio", "kdProfit"),
        )
    }

    fun parseSolCareerProfile(body: String, base: PlayerProfile): PlayerProfile {
        val payload = payloadObject(body) ?: return base
        val detail = payload.optJSONObject("solDetail") ?: return base
        val totalEscape = detail.firstLong("totalEscape") ?: 0L
        val totalFight = detail.firstLong("totalFight") ?: 0L
        val levelScore = detail.firstLong("levelScore") ?: 0L
        val majorLevelMax = detail.firstLong("majorLevelMax", "majorLevel")?.toInt() ?: 0
        val currentRankIcon = solRankIconIndex(levelScore)?.let(::solRankIconUrl).orEmpty()
        val highestRankIcon = SOL_MAJOR_ICON_INDEX.getOrNull(majorLevelMax)?.let(::solRankIconUrl).orEmpty()
        val profitLossRaw = detail.firstLong("profitLossRatio")
        // solDetail 包含游戏角色级别的昵称/头像/大区（user.info 的 platform-level 字段通常为空）。
        // 优先从 solDetail 取，缺失时保留 base 值；最终兜底到 payload 顶层字段。
        val solNickname = detail.firstString(
            "nickName", "nickname", "roleName", "sRoleName", "vRoleName", "name", "userName",
        )
        val solAvatar = detail.firstImageUrl(
            "picUrl", "headUrl", "vHeadUrl", "vHeadPic", "avatar", "avatarUrl", "headPic", "headIcon",
            "headimgurl", "headImgUrl", "wxHeadImg", "wxHeadImgUrl", "wx_headimgurl",
        )
        val solArea = detail.firstString("areaName", "area", "region", "serverName", "zoneName")
        val solFrame = detail.firstString("avatarFrame", "avatarFrameUrl", "frame", "framePic")
        val fallbackNickname = payload.firstString(
            "nickName", "nickname", "roleName", "sRoleName", "vRoleName", "uin",
        )
        return base.copy(
            nickname = solNickname
                .ifBlank { base.nickname }
                .ifBlank { fallbackNickname },
            avatarUrl = solAvatar.ifBlank { base.avatarUrl },
            areaName = solArea
                .ifBlank { base.areaName },
            avatarFrameUrl = solFrame
                .ifBlank { base.avatarFrameUrl },
            currentRankName = solRankName(levelScore),
            currentRankIconUrl = currentRankIcon,
            highestRankName = SOL_MAJOR_RANK_NAMES.getOrNull(majorLevelMax).orEmpty(),
            highestRankIconUrl = highestRankIcon,
            totalBringOutValue = detail.firstLong("totalGainedPrice", "totalBringOutValue")
                ?.formatWanPrice()
                .orEmpty(),
            evacuationRate = percent(totalEscape, totalFight),
            operatorKills = detail.firstLong("totalKill")?.toString().orEmpty(),
            profitLossRatio = profitLossRaw?.let { (it / 100L).formatWanPrice() }.orEmpty(),
        )
    }

    /**
     * 解析 QIRBwm 流程返回的角色信息（昵称、头像、生涯统计）。
     * 响应格式与 user.info/solResource 不同：jData 直接包含 userData 和 careerData，
     * 且 userData.picurl / userData.charac_name 均为 URL 编码字符串。
     */
    fun parseRoleInfo(body: String, base: PlayerProfile): PlayerProfile {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return base
        if (root.optInt("ret", -1) != 0 || root.optInt("iRet", -1) != 0) return base
        val jData = root.optJSONObject("jData") ?: return base
        val userData = jData.optJSONObject("userData") ?: return base

        val characName = userData.firstString("charac_name", "characName", "userName", "nickname")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .orEmpty()

        val picurl = userData.firstString("picurl", "picUrl", "avatar", "headUrl")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.let { decoded ->
                decoded.toHttpsUrl()
                    .takeIf { it.isRemoteImageUrl() }
                    ?: objectIconUrl(decoded)
            }
            .orEmpty()

        val career = jData.optJSONObject("careerData")
        val escapeRate = career?.firstString("solescaperatio", "solEscapeRatio").orEmpty()
        val totalKill = career?.firstString("soltotalkill", "solTotalKill").orEmpty()
        val totalProfit = career?.firstLong("totalprice", "totalPrice")
            ?.formatWanPrice().orEmpty()

        return base.copy(
            nickname = characName.ifBlank { base.nickname },
            avatarUrl = picurl.ifBlank { base.avatarUrl },
            evacuationRate = escapeRate.ifBlank { base.evacuationRate },
            operatorKills = totalKill.ifBlank { base.operatorKills },
            totalBringOutValue = base.totalBringOutValue.ifBlank { totalProfit },
        )
    }

    fun parseYesterdayIncome(body: String): IncomeSummary {
        val data = payloadObject(body) ?: return IncomeSummary.empty()
        val solDetail = data.optJSONObject("solDetail")
        val value = solDetail?.firstLong("recentGain")
            ?: data.firstLong("yesterdayIncome", "yesterdayProfit", "lastDayIncome", "dailyIncome")
        return IncomeSummary(
            amount = value?.formatWanPrice().orEmpty(),
            rawValue = value,
        )
    }

    fun parseCollections(body: String): List<CollectionItem> {
        val data = payloadObject(body) ?: return emptyList()
        val list = data.optJSONObject("solDetail")
            ?.optJSONObject("userCollectionTop")
            ?.optJSONArray("list")
            ?: data.firstArray("collectionList", "bringOutCollection", "collections", "list")
            ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val id = item.firstString("objectID", "objectId", "itemId", "id")
            val name = item.firstString("objectName", "name", "itemName").ifBlank { id }
            if (id.isBlank() && name.isBlank()) {
                null
            } else {
                val count = item.firstLong("count", "num", "number", "quantity")?.toInt()
                CollectionItem(
                    id = id.ifBlank { name },
                    name = name,
                    imageUrl = item.firstString("pic", "imageUrl", "prePic", "icon"),
                    value = item.firstLong("value", "price", "avgPrice", "objectPrice")?.formatWanPrice().orEmpty(),
                    mapName = item.firstString("mapName", "placeName", "map"),
                    count = (count ?: 1).coerceAtLeast(1),
                    grade = item.firstLong("grade", "ItemQuality")?.toInt() ?: 0,
                )
            }
        }
    }

    fun parseRecentMatches(body: String): List<MatchRecord> {
        val directArray = payloadArray(body)
        val list = directArray ?: payloadObject(body)?.firstArray("recentMatches", "battleList", "matchList", "list")
            ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val id = item.firstString("RoomId", "battleId", "matchId", "id", "recordId")
            val mapId = item.firstString("MapId", "MapID", "mapId")
            val mapName = item.firstString("mapName", "placeName", "map")
                .ifBlank { mapId.toDisplayMapName() }
            if (id.isBlank() && mapName.isBlank()) {
                null
            } else {
                // 与三角洲小程序一致：净收益（盈亏）= flowCalGainedPrice（已带符号，负数=亏损）。
                // 只读这一个字段，不再 fallback 到其它价格字段，避免把"带出/累计"误当成净收益。
                val netIncomeValue = item.firstLong("flowCalGainedPrice")
                // 带出价值（小程序通过 getSolDatails 取 Gainedprice；列表里若已带则直接用）。
                val broughtOutValue = item.firstLong("Gainedprice", "broughtOutValue", "bringOutValue")
                val killCount = item.firstLong("KillCount") ?: item.firstLong("killOperatorNum", "operatorKills", "kills")
                val aiPlayerKillCount = item.firstLong("KillPlayerAICount") ?: 0L
                // 干员：战绩接口只返回 ArmedForceId，小程序前端用 assetsId.sqlImg/sqlName 查表。
                val armedForceId = item.firstLong("ArmedForceId")
                MatchRecord(
                    id = id.ifBlank { "${mapName}_${item.firstString("battleTime", "time", "dtEventTime")}" },
                    mapName = mapName,
                    modeName = item.firstString("modeName", "mode", "typeName").ifBlank { "烽火地带" },
                    // 撤离结果：BattleResult 接口始终为空字符串，以 EscapeFailReason 数值为准（1=成功，其余=失败）。
                    result = escapeResult(item.firstLong("EscapeFailReason", "escapeFailReason", "failReason", "escapeResult", "EscapeResult"))
                        .ifBlank { item.firstString("BattleResult", "battleResult", "result", "resultName") },
                    netIncome = netIncomeValue?.formatCommaNumber().orEmpty(),
                    netIncomeValue = netIncomeValue,
                    broughtOutValue = broughtOutValue,
                    operatorKills = killCount?.plus(aiPlayerKillCount)?.toString()
                        ?: item.firstString("killOperatorNum", "operatorKills", "kills"),
                    duration = item.firstLong("DurationS")?.formatDuration()
                        ?: item.firstString("duration", "battleDuration", "useTime", "gametime"),
                    battleTime = item.firstString("battleTime", "time", "date", "dtEventTime", "createTime"),
                    operatorId = armedForceId?.toString().orEmpty(),
                    // 优先用接口给的干员名/图，缺失时用 ArmedForceId 查 assetsId 表（与小程序一致）。
                    operatorName = item.firstString("operatorName", "heroName", "roleName")
                        .ifBlank { operatorName(armedForceId) },
                    // 干员头像使用专用 CDN，不使用物资图片的 cdnFormat.object.prefix。
                    operatorImageUrl = item.firstString("operatorIcon", "heroIcon", "operatorPic", "roleIcon")
                        .ifBlank { operatorIconUrl(armedForceId) },
                )
            }
        }
    }

    fun parseRedArchive(body: String): List<RedArchiveRecord> {
        val list = payloadObject(body)?.firstArray("redRecords", "redList", "collectionList", "list")
            ?: return emptyList()
        return list.mapObjectsNotNullTyped { item ->
            val itemId = item.firstString("itemId", "objectID", "objectId", "id")
            val time = item.firstString("foundTime", "time", "date", "createTime", "dtEventTime")
            val name = item.firstString("objectName", "name", "itemName").ifBlank { itemId }
            if (itemId.isBlank() && name.isBlank()) {
                null
            } else {
                RedArchiveRecord(
                    id = item.firstString("recordId").ifBlank { "${itemId}_$time" },
                    name = name,
                    imageUrl = item.firstString("pic", "imageUrl", "prePic", "icon"),
                    value = item.firstLong("value", "price", "avgPrice", "objectPrice")?.formatWanPrice().orEmpty(),
                    mapName = item.firstString("mapName", "placeName", "map").ifBlank {
                        item.firstString("mapid", "mapId").toDisplayMapName()
                    },
                    foundTime = time,
                    grade = item.firstLong("grade", "ItemQuality")?.toInt() ?: 0,
                )
            }
        }
            // 任务4：大红藏馆按时间从新到旧排序。
            .sortedByDescending { it.foundTime.toSortableTimestamp() }
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

    private fun String.toDisplayPlace(): String = when (this) {
        "tech" -> "技术中心"
        "workbench" -> "工作台"
        "control" -> "中控"
        "fire" -> "靶场"
        "medical" -> "制药台"
        "collect" -> "藏馆"
        "diving" -> "潜水中心"
        "training" -> "训练中心"
        else -> this
    }

    private fun payload(body: String): JSONObject? = payloadObject(body)

    private fun payloadObject(body: String): JSONObject? =
        payloadValue(body) as? JSONObject

    private fun payloadArray(body: String): JSONArray? =
        payloadValue(body) as? JSONArray

    private fun payloadValue(body: String): Any? {
        val root = JSONObject(body)
        val ret = root.optInt("ret", 0)
        val iRet = root.optInt("iRet", 0)
        if (ret != 0 || iRet != 0) return null

        val data = root.optJSONObject("jData")?.opt("data") ?: return null
        return when (data) {
            is JSONArray -> data
            is JSONObject -> {
                if (data.has("code") && data.optInt("code", 0) != 0) return null
                if (data.has("iRet") && data.optInt("iRet", 0) != 0) return null
                when (val inner = data.opt("data")) {
                    is JSONObject -> inner
                    is JSONArray -> inner
                    else -> data
                }
            }
            else -> null
        }
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
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.replace(",", "").trim().toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.firstArray(vararg keys: String): JSONArray? {
        keys.forEach { key ->
            optJSONArray(key)?.let { return it }
        }
        return null
    }

    private fun JSONObject.firstString(vararg keys: String): String {
        keys.forEach { key ->
            val value = if (has(key) && !isNull(key)) opt(key)?.toString().orEmpty() else ""
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun JSONObject.firstImageUrl(vararg keys: String): String {
        keys.forEach { key ->
            val value = if (has(key) && !isNull(key)) opt(key)?.toString().orEmpty() else ""
            val url = value.toHttpsUrl()
            if (url.isRemoteImageUrl()) return url
        }
        return ""
    }

    private fun JSONObject.firstLong(vararg keys: String): Long? {
        keys.forEach { key ->
            optLongOrNull(key)?.let { return it }
        }
        return null
    }

    private fun List<JSONArray>.flattenJsonArrays(): JSONArray {
        val result = JSONArray()
        forEach { array ->
            for (index in 0 until array.length()) {
                result.put(array.opt(index))
            }
        }
        return result
    }

    private fun JSONObject.collectJsonArrays(): List<JSONArray> {
        val result = mutableListOf<JSONArray>()
        keys().forEach { key ->
            when (val value = opt(key)) {
                is JSONArray -> result += value
                is JSONObject -> result += value.collectJsonArrays()
            }
        }
        return result
    }

    private fun solRankName(levelScore: Long): String {
        if (levelScore <= 1_000L) return "无段位"
        return SOL_RANK_THRESHOLDS.firstOrNull { levelScore <= it.threshold }?.name ?: "三角洲巅峰"
    }

    private fun solRankIconIndex(levelScore: Long): String? =
        SOL_RANK_THRESHOLDS.firstOrNull { levelScore <= it.threshold }?.iconIndex
            ?: if (levelScore >= 6_000L) "25" else "0"

    private fun solRankIconUrl(iconIndex: String): String =
        "https://game.gtimg.cn/images/dfm/cp/a20240807community/iteration/dw_fh_icon$iconIndex.png"

    private fun percent(part: Long, total: Long): String {
        if (part <= 0L || total <= 0L) return ""
        val value = part * 100.0 / total
        return "${formatOneDecimal(value)}%"
    }

    private fun escapeResult(reason: Long?): String = when (reason) {
        null -> ""
        1L -> "撤离成功"
        else -> "撤离失败"
    }

    private fun String.toDisplayMapName(): String =
        MAP_NAMES[this].orEmpty()

    private fun Long.formatDuration(): String {
        val minutes = this / 60
        val seconds = this % 60
        return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
    }

    private fun Long.formatWanPrice(): String {
        if (this == 0L) return "0"
        val sign = if (this < 0) "-" else ""
        val value = kotlin.math.abs(this)
        return when {
            value >= 100_000_000L -> "${sign}${formatOneDecimal(value / 100_000_000.0)}亿"
            value >= 10_000L -> "${sign}${formatOneDecimal(value / 10_000.0)}万"
            else -> "$sign$value"
        }
    }

    /**
     * 与三角洲小程序的 addComma / formatNumberWithCommas 一致：输出原始整数千分位，
     * 负数前加 "-"，不转"万"。例如 528751 -> "528,751"；-3178557 -> "-3,178,557"。
     */
    private fun Long.formatCommaNumber(): String {
        if (this == 0L) return "0"
        val sign = if (this < 0) "-" else ""
        val value = kotlin.math.abs(this)
        return sign + value.toString().replace(Regex("(?=(?:\\d{3})+$)"), ",").removePrefix(",")
    }

    /**
     * 把大红藏馆的时间字符串转换为可排序的 Long（越大越新）。
     * 支持 "yyyy-MM-dd HH:mm:ss"、秒级时间戳、以及各种回退键名；无法解析时回退 0。
     */
    private fun String.toSortableTimestamp(): Long {
        if (isBlank()) return 0L
        toLongOrNull()?.let { return it }
        // yyyy-MM-dd HH:mm:ss -> yyyyMMddHHmmss 数值比较
        val digits = filter { it.isDigit() }
        return if (digits.length >= 8) {
            digits.take(14).padEnd(14, '0').toLongOrNull() ?: 0L
        } else {
            0L
        }
    }

    private fun String.toHttpsUrl(): String {
        val value = trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) -> "https://${value.drop(7)}"
            else -> value
        }
    }

    private fun String.isRemoteImageUrl(): Boolean =
        startsWith("https://", ignoreCase = true)

    private fun formatOneDecimal(value: Double): String =
        String.format(java.util.Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')

    private data class SolRank(
        val threshold: Long,
        val name: String,
        val iconIndex: String,
    )

    private val SOL_RANK_THRESHOLDS = listOf(
        SolRank(1_149, "青铜Ⅲ", "3"),
        SolRank(1_299, "青铜Ⅱ", "2"),
        SolRank(1_449, "青铜Ⅰ", "1"),
        SolRank(1_599, "白银Ⅲ", "6"),
        SolRank(1_749, "白银Ⅱ", "5"),
        SolRank(1_899, "白银I", "4"),
        SolRank(2_099, "黄金Ⅳ", "10"),
        SolRank(2_299, "黄金Ⅲ", "9"),
        SolRank(2_499, "黄金Ⅱ", "8"),
        SolRank(2_699, "黄金Ⅰ", "7"),
        SolRank(2_899, "铂金Ⅳ", "14"),
        SolRank(3_099, "铂金Ⅲ", "13"),
        SolRank(3_299, "铂金Ⅱ", "12"),
        SolRank(3_499, "铂金Ⅰ", "11"),
        SolRank(3_749, "钻石V", "19"),
        SolRank(3_999, "钻石Ⅳ", "18"),
        SolRank(4_249, "钻石Ⅲ", "17"),
        SolRank(4_499, "钻石Ⅱ", "16"),
        SolRank(4_749, "钻石Ⅰ", "15"),
        SolRank(4_999, "黑鹰V", "24"),
        SolRank(5_249, "黑鹰Ⅳ", "23"),
        SolRank(5_499, "黑鹰Ⅲ", "22"),
        SolRank(5_749, "黑鹰Ⅱ", "21"),
        SolRank(5_999, "黑鹰Ⅰ", "20"),
        SolRank(6_000, "三角洲巅峰", "25"),
    )

    private val SOL_MAJOR_RANK_NAMES = listOf("无段位", "青铜", "白银", "黄金", "铂金", "钻石", "黑鹰", "三角洲巅峰")
    private val SOL_MAJOR_ICON_INDEX = listOf("3", "1", "4", "7", "11", "15", "20", "25")

    private val MAP_NAMES = mapOf(
        "1901" to "长弓溪谷-常规",
        "1902" to "长弓溪谷-机密",
        "1911" to "长弓溪谷-常规",
        "1912" to "长弓溪谷-机密",
        "1999" to "长弓溪谷教学关",
        "2201" to "零号大坝-常规",
        "2202" to "零号大坝-机密",
        "2211" to "零号大坝-常规",
        "2212" to "零号大坝-机密",
        "2231" to "零号大坝-前夜",
        "2232" to "零号大坝-永夜",
        "2233" to "零号大坝-终夜",
        "2242" to "零号大坝-水淹",
        "3901" to "航天基地-机密",
        "3902" to "航天基地-绝密",
        "8101" to "巴克什-常规",
        "8102" to "巴克什-机密",
        "8103" to "巴克什-绝密",
        "8802" to "潮汐监狱-适应",
        "8803" to "潮汐监狱-绝密",
    )
}
