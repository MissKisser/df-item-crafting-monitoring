package com.local.dfcraftmonitor.data.backend

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLocalBackend @Inject constructor(
    private val remoteClient: AmsRemoteClient,
) : LocalBackend {

    private fun Int.ifZero(fallback: () -> Int): Int = if (this == 0) fallback() else this

    override suspend fun getCrafting(credential: AmsCredential): Result<CraftingSnapshot> = runCatching {
        require(credential.isComplete()) { "AMS credential incomplete" }
        val body = remoteClient.fetchCraftingStatus(credential)
        AmsCraftingParser.parse(body)
    }

    override suspend fun getDashboard(credential: AmsCredential?): Result<LocalDashboardData> {
        if (credential == null || !credential.isComplete()) return Result.success(LocalDashboardData.empty())

        return runCatching {
            coroutineScope {
                // 第一波：所有独立调用并行启动
                val daySecretsDeferred = async { fetchDaySecrets(credential) }
                val placeBodyDeferred = async { fetchFlowBody(credential, PLACE_LIST_CALL) }
                val userInfoBodyDeferred = async { fetchFlowBody(credential, USER_INFO_CALL) }
                val solResourceBodyDeferred = async { fetchFlowBody(credential, SOL_RESOURCE_CALL) }
                val solRecentBodyDeferred = async { fetchFlowBody(credential, SOL_RECENT_DETAIL_CALL) }
                val redArchiveBodyDeferred = async { fetchFlowBody(credential, RED_UNLOCK_RECORD_CALL) }
                val roleInfoBodyDeferred = async { fetchFlowBody(credential, ROLE_INFO_CALL) }
                val recentMatchesDeferred = async { fetchRecentMatches(credential) }

                // 第二波：enrichment 在各自依赖就绪后立即启动
                val collectionsDeferred = async {
                    solRecentBodyDeferred.await()
                        ?.let(AmsDashboardParser::parseCollections)
                        .orEmpty()
                        .let { enrichCollections(credential, it) }
                }
                val redArchiveDeferred = async {
                    redArchiveBodyDeferred.await()
                        ?.let(AmsDashboardParser::parseRedArchive)
                        .orEmpty()
                        .let { enrichRedArchive(credential, it) }
                }
                val manufacturingRecommendationsDeferred = async {
                    placeBodyDeferred.await()
                        ?.let(AmsDashboardParser::parseManufacturingRecommendations)
                        .orEmpty()
                        .let { enrichManufacturingRecommendations(credential, it) }
                }

                // profile 解析链：各依赖就绪后立即推进
                val profileDeferred = async {
                    val baseProfile = userInfoBodyDeferred.await()
                        ?.let(AmsDashboardParser::parsePlayerProfile)
                        ?: PlayerProfile.empty()
                    val solProfile = solResourceBodyDeferred.await()
                        ?.let { AmsDashboardParser.parseSolCareerProfile(it, baseProfile) }
                        ?: baseProfile
                    val profile = roleInfoBodyDeferred.await()
                        ?.let { AmsDashboardParser.parseRoleInfo(it, solProfile) }
                        ?: solProfile
                    profile.copy(
                        areaName = credential.platform.areaName,
                    )
                }

                val placeBody = placeBodyDeferred.await()
                val solRecentBody = solRecentBodyDeferred.await()
                val profile = profileDeferred.await()

                LocalDashboardData.empty().copy(
                    daySecrets = daySecretsDeferred.await(),
                    maps = placeBody?.let(AmsDashboardParser::parseManufacturingPlaces).orEmpty(),
                    profile = profile,
                    profileImageUrl = profile.avatarUrl,
                    yesterdayIncome = solRecentBody?.let(AmsDashboardParser::parseYesterdayIncome) ?: IncomeSummary.empty(),
                    collections = collectionsDeferred.await(),
                    recentMatches = recentMatchesDeferred.await(),
                    redArchive = redArchiveDeferred.await(),
                    manufacturingRecommendations = manufacturingRecommendationsDeferred.await(),
                )
            }
        }
    }

    override suspend fun fetchSolCareer(
        credential: AmsCredential,
        seasonId: Int,
        isAllSeason: Boolean,
    ): Result<PlayerProfile> = runCatching {
        val seasonList = if (isAllSeason) (1..seasonId).toList() else listOf(seasonId)
        val param = """{"resourceType":"sol","seasonID":${seasonList},"isAllSeason":$isAllSeason}"""
        val call = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "dfm/center.person.resource",
            paramJson = param,
        )
        val body = remoteClient.fetchFlow(credential, call.forPlatform(credential))
        AmsDashboardParser.parseSolCareerProfile(body, PlayerProfile.empty())
    }

    private suspend fun fetchDaySecrets(credential: AmsCredential): List<DaySecret> =
        runCatching {
            AmsDashboardParser.parseDaySecrets(remoteClient.fetchFlow(credential, DAY_SECRET_CALL.forPlatform(credential)))
        }.getOrDefault(emptyList())

    private suspend fun fetchToolObjects(credential: AmsCredential): List<ToolObjectSummary> = coroutineScope {
        val objects = TOOL_OBJECT_CALLS
            .map { call ->
                async {
                    runCatching {
                        AmsDashboardParser.parseToolObjects(remoteClient.fetchFlow(credential, call.forPlatform(credential)))
                    }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.id }
            .filter { it.name.isNotBlank() && it.imageUrl.isNotBlank() }
            .take(MAX_TOOL_OBJECTS)
        objects
            .map { item -> async { enrichWithPricePoints(credential, item) } }
            .awaitAll()
    }

    private suspend fun fetchToolConfig(credential: AmsCredential): List<ToolConfigItem> = coroutineScope {
        CONFIG_CALLS
            .map { call ->
                async {
                    runCatching {
                        AmsDashboardParser.parseToolConfigs(remoteClient.fetchFlow(credential, call.forPlatform(credential)))
                    }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { "${it.type}:${it.id}:${it.name}" }
    }

    private suspend fun fetchRecentMatches(credential: AmsCredential): List<MatchRecord> = coroutineScope {
        RECENT_MATCH_CALLS
            .map { call ->
                async {
                    runCatching {
                        AmsDashboardParser.parseRecentMatches(remoteClient.fetchForm(credential, call.forPlatform(credential)))
                    }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.id }
            .take(MAX_RECENT_MATCHES)
    }

    private suspend fun fetchFlowBody(
        credential: AmsCredential,
        call: AmsFlowCall,
    ): String? =
        runCatching { remoteClient.fetchFlow(credential, call.forPlatform(credential)) }.getOrNull()

    private suspend fun enrichWithPricePoints(
        credential: AmsCredential,
        item: ToolObjectSummary,
    ): ToolObjectSummary {
        val pricePoints = fetchPricePoints(credential, item.id)
        return item.copy(
            price = pricePoints.lastOrNull()?.price ?: item.price,
            trend = priceTrend(pricePoints),
            pricePoints = pricePoints,
        )
    }

    private suspend fun fetchPricePoints(
        credential: AmsCredential,
        objectId: String,
    ): List<PricePoint> = coroutineScope {
        val numericId = objectId.toLongOrNull()
        if (numericId == null) {
            emptyList()
        } else {
            PRICE_METHODS
                .map { (method, buildParamJson) ->
                    async {
                        runCatching {
                            AmsDashboardParser.parsePricePoints(
                                remoteClient.fetchFlow(
                                    credential,
                                    AmsFlowCall(
                                        chartId = KFXJWH_CHART_ID,
                                        sIdeToken = KFXJWH_TOKEN,
                                        method = method,
                                        paramJson = buildParamJson(numericId),
                                    ).forPlatform(credential),
                                ),
                            )
                        }.getOrDefault(emptyList())
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { "${it.label}:${it.priceValue}" }
        }
    }

    private suspend fun enrichCollections(
        credential: AmsCredential,
        collections: List<CollectionItem>,
    ): List<CollectionItem> {
        if (collections.isEmpty()) return emptyList()
        val objects = fetchObjectsByIds(credential, collections.map { it.id })
        return collections.map { item ->
            val objectInfo = objects[item.id]
            item.copy(
                name = objectInfo?.name ?: item.name,
                imageUrl = objectInfo?.imageUrl ?: item.imageUrl,
                value = item.value.ifBlank { objectInfo?.price.orEmpty() },
                grade = item.grade.ifZero { objectInfo?.grade ?: 0 },
            )
        }
    }

    private suspend fun enrichRedArchive(
        credential: AmsCredential,
        records: List<RedArchiveRecord>,
    ): List<RedArchiveRecord> {
        if (records.isEmpty()) return emptyList()
        val objects = fetchObjectsByIds(credential, records.map { it.name })
        return records.map { record ->
            val objectInfo = objects[record.name]
            record.copy(
                name = objectInfo?.name ?: record.name,
                imageUrl = objectInfo?.imageUrl ?: record.imageUrl,
                value = record.value.ifBlank { objectInfo?.price.orEmpty() },
                grade = record.grade.ifZero { objectInfo?.grade ?: 0 },
            )
        }
    }

    private suspend fun enrichManufacturingRecommendations(
        credential: AmsCredential,
        recommendations: List<ManufacturingRecommendation>,
    ): List<ManufacturingRecommendation> {
        if (recommendations.isEmpty()) return emptyList()
        val objects = fetchObjectsByIds(credential, recommendations.map { it.id })
        return recommendations.map { item ->
            val objectInfo = objects[item.id]
            item.copy(
                name = objectInfo?.name ?: item.name,
                imageUrl = objectInfo?.imageUrl ?: item.imageUrl,
                grade = objectInfo?.grade ?: item.grade,
            )
        }
    }

    private suspend fun fetchObjectsByIds(
        credential: AmsCredential,
        ids: List<String>,
    ): Map<String, ToolObjectSummary> {
        val joinedIds = ids
            .mapNotNull { it.toLongOrNull()?.toString() }
            .distinct()
            .joinToString(",")
        if (joinedIds.isBlank()) return emptyMap()
        return runCatching {
            AmsDashboardParser.parseToolObjects(
                remoteClient.fetchFlow(
                    credential,
                    AmsFlowCall(
                        chartId = KFXJWH_CHART_ID,
                        sIdeToken = KFXJWH_TOKEN,
                        method = "dfm/object.list",
                        paramJson = """{"objectID":"$joinedIds"}""",
                    ).forPlatform(credential),
                ),
            ).associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    private fun AmsFlowCall.forPlatform(credential: AmsCredential): AmsFlowCall {
        val platformParam = paramJson.withPlatform(credential)
        return if (platformParam == paramJson) this else copy(paramJson = platformParam)
    }

    private fun AmsFormCall.forPlatform(credential: AmsCredential): AmsFormCall {
        val platform = credential.platform
        val appid = credential.appid.ifBlank { platform.gameAppId }
        return copy(
            fields = fields + mapOf(
                "sArea" to platform.sArea,
                "area" to platform.sArea,
                "appid" to appid,
                "channel" to platform.channelKey,
            ),
        )
    }

    private fun String.withPlatform(credential: AmsCredential): String {
        val trimmed = trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{") || !trimmed.endsWith("}")) return this
        val platform = credential.platform
        val appid = credential.appid.ifBlank { platform.gameAppId }
        val platformJson = buildString {
            append("\"sArea\":\"").append(platform.sArea).append('"')
            append(",\"appid\":\"").append(appid).append('"')
            append(",\"channel\":\"").append(platform.channelKey).append('"')
        }
        return if (trimmed == "{}") {
            "{$platformJson}"
        } else {
            "${trimmed.dropLast(1)},$platformJson}"
        }
    }

    private fun deriveToolCategories(
        objects: List<ToolObjectSummary>,
        configs: List<ToolConfigItem>,
    ): List<String> =
        (
            objects
            .mapNotNull { item ->
                item.category.substringBefore(" / ").trim().ifBlank { null }
            } + configs
                .filter { item ->
                    item.type.contains("category", ignoreCase = true) ||
                        item.type.contains("class", ignoreCase = true)
                }
                .mapNotNull { it.name.trim().ifBlank { null } }
            )
            .distinct()

    private fun priceTrend(points: List<PricePoint>): String {
        val first = points.firstOrNull()?.priceValue ?: return ""
        val last = points.lastOrNull()?.priceValue ?: return ""
        if (first <= 0L || points.size < 2) return ""
        val percent = (last - first) * 100.0 / first
        if (abs(percent) < 0.05) return "持平"
        val formatted = if (abs(percent) >= 10.0) {
            abs(percent).toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", abs(percent)).trimEnd('0').trimEnd('.')
        }
        val sign = if (percent > 0) "+" else "-"
        return "$sign$formatted%"
    }

    private companion object {
        private const val NO_OAPI_CHART_ID = "316969"
        private const val NO_OAPI_TOKEN = "NoOapI"
        private const val KFXJWH_CHART_ID = "316968"
        private const val KFXJWH_TOKEN = "KfXJwH"
        private const val PHQ59Y_CHART_ID = "450526"
        private const val PHQ59Y_TOKEN = "PHq59Y"
        private const val QIRBWM_CHART_ID = "317814"
        private const val QIRBWM_TOKEN = "QIRBwm"
        private const val MAX_TOOL_OBJECTS = 32
        private const val MAX_RECENT_MATCHES = 80

        private val DAY_SECRET_CALL = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "dfm/center.day.secret",
        )

        private val PLACE_LIST_CALL = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "dfm/place.list",
            paramJson = """{"type":"place","hasPriceData":true}""",
        )

        private val USER_INFO_CALL = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "user.info",
            paramJson = "",
        )

        private val SOL_RESOURCE_CALL = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "dfm/center.person.resource",
            paramJson = """{"resourceType":"sol","seasonID":[9],"isAllSeason":false}""",
        )

        private val SOL_RECENT_DETAIL_CALL = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "dfm/center.recent.detail",
            paramJson = """{"resourceType":"sol"}""",
        )

        private val RED_UNLOCK_RECORD_CALL = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "dfm/center.recent.redUnlockRecord",
        )

        private val ROLE_INFO_CALL = AmsFlowCall(
            chartId = QIRBWM_CHART_ID,
            sIdeToken = QIRBWM_TOKEN,
            method = "",
            paramJson = "",
        )

        private val CONFIG_CALLS = listOf(
            AmsFlowCall(
                chartId = KFXJWH_CHART_ID,
                sIdeToken = KFXJWH_TOKEN,
                method = "dfm/config.list",
                paramJson = """{"configType":"all"}""",
            ),
            AmsFlowCall(
                chartId = KFXJWH_CHART_ID,
                sIdeToken = KFXJWH_TOKEN,
                method = "dfm/config.white.list",
            ),
        )

        private val PRICE_METHODS = listOf(
            "dfm/object.price.latest" to { objectId: Long -> """{"objectID":[$objectId]}""" },
            "dfm/object.price.recent" to { objectId: Long -> """{"objectID":$objectId}""" },
            "dfm/object.price.hour" to { objectId: Long -> """{"objectID":$objectId}""" },
        )

        private val RECENT_MATCH_CALLS = listOf(
            AmsFormCall(
                chartId = PHQ59Y_CHART_ID,
                sIdeToken = PHQ59Y_TOKEN,
                fields = mapOf(
                    "type" to "4",
                    "item" to "0,0,0,2201,0,0,0,75",
                    "page" to "1",
                ),
            ),
            AmsFormCall(
                chartId = PHQ59Y_CHART_ID,
                sIdeToken = PHQ59Y_TOKEN,
                fields = mapOf(
                    "type" to "5",
                    "item" to "0,0,0,2201,0,0,0,75",
                    "page" to "1",
                ),
            ),
        )

        private val TOOL_OBJECT_CALLS = listOf(
            AmsFlowCall(
                chartId = NO_OAPI_CHART_ID,
                sIdeToken = NO_OAPI_TOKEN,
                method = "dfm/object.list",
                paramJson = """{"primary":"gun","second":"gunRifle","objectID":""}""",
            ),
            AmsFlowCall(
                chartId = NO_OAPI_CHART_ID,
                sIdeToken = NO_OAPI_TOKEN,
                method = "dfm/object.list",
                paramJson = """{"primary":"acc","second":"accStock","objectID":""}""",
            ),
            AmsFlowCall(
                chartId = NO_OAPI_CHART_ID,
                sIdeToken = NO_OAPI_TOKEN,
                method = "dfm/object.list",
                paramJson = """{"primary":"ammo","second":"ammo5.45x39","objectID":""}""",
            ),
            AmsFlowCall(
                chartId = NO_OAPI_CHART_ID,
                sIdeToken = NO_OAPI_TOKEN,
                method = "dfm/object.list",
                paramJson = """{"primary":"protect","second":"helmet","objectID":""}""",
            ),
            AmsFlowCall(
                chartId = NO_OAPI_CHART_ID,
                sIdeToken = NO_OAPI_TOKEN,
                method = "dfm/object.list",
                paramJson = """{"primary":"props","second":"collection","objectID":""}""",
            ),
            AmsFlowCall(
                chartId = KFXJWH_CHART_ID,
                sIdeToken = KFXJWH_TOKEN,
                method = "dfm/object.list",
                paramJson = """{"primary":"vehicle","second":"define","objectID":""}""",
            ),
        )
    }
}
