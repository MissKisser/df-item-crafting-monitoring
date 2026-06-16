package com.local.dfcraftmonitor.data.backend

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLocalBackend @Inject constructor(
    private val remoteClient: AmsRemoteClient,
) : LocalBackend {

    override suspend fun getCrafting(credential: AmsCredential): Result<CraftingSnapshot> = runCatching {
        require(credential.isComplete()) { "AMS credential incomplete" }
        val body = remoteClient.fetchCraftingStatus(credential)
        AmsCraftingParser.parse(body)
    }

    override suspend fun getDashboard(credential: AmsCredential?): Result<LocalDashboardData> {
        val fallback = getFallbackDashboard()
        if (credential == null || !credential.isComplete()) return Result.success(fallback)

        return runCatching {
            val daySecrets = fetchDaySecrets(credential).ifEmpty { fallback.daySecrets }
            val toolObjects = fetchToolObjects(credential).ifEmpty { fallback.toolObjects }
            fallback.copy(
                daySecrets = daySecrets,
                toolObjects = toolObjects,
            )
        }.recover { fallback }
    }

    override fun getFallbackDashboard(): LocalDashboardData = LocalDashboardData(
        toolCategories = getToolCategories(),
        toolObjects = getToolObjects(),
        daySecrets = getDaySecrets(),
        maps = getMaps(),
        homeBannerImageUrl = getHomeBannerImageUrl(),
        profileImageUrl = getProfileImageUrl(),
    )

    override fun getToolCategories(): List<String> =
        listOf("枪械", "配件", "子弹", "防具", "道具", "载具", "地点")

    override fun getToolObjects(): List<ToolObjectSummary> = listOf(
        ToolObjectSummary(
            id = "13040000185",
            name = "骨架狙击枪托",
            category = "配件 / 枪托",
            price = "45.6万",
            trend = "+3.2%",
            imageUrl = "https://playerhub.df.qq.com/playerhub/60004/object/13040000185.png",
        ),
        ToolObjectSummary(
            id = "11010005002",
            name = "H09 防暴头盔",
            category = "防具 / 头盔",
            price = "8.2万",
            trend = "-1.4%",
            imageUrl = "https://playerhub.df.qq.com/playerhub/60004/object/11010005002.png",
        ),
        ToolObjectSummary(
            id = "14060000002",
            name = "精密护甲维修包",
            category = "道具 / 维修",
            price = "3.1万",
            trend = "+0.8%",
            imageUrl = "https://playerhub.df.qq.com/playerhub/60004/object/14060000002.png",
        ),
        ToolObjectSummary(
            id = "37120500001",
            name = "5.45x39mm BS",
            category = "子弹 / 突击步枪",
            price = "1.9万",
            trend = "+5.6%",
            imageUrl = "https://playerhub.df.qq.com/playerhub/60004/object/37120500001.png",
        ),
    )

    override fun getDaySecrets(): List<DaySecret> = listOf(
        DaySecret("零号大坝", "7391"),
        DaySecret("长弓溪谷", "0426"),
        DaySecret("航天基地", "1850"),
    )

    override fun getMaps(): List<MapSummary> = listOf(
        MapSummary("零号大坝", "map-lhdb"),
        MapSummary("长弓溪谷", "map-cgxg"),
        MapSummary("航天基地", "map-htjd"),
    )

    override fun getHomeBannerImageUrl(): String =
        "https://game.gtimg.cn/images/dfm/cp/a20240807community/home/banner_0.png"

    override fun getProfileImageUrl(): String =
        "https://game.gtimg.cn/images/dfm/cp/a20240807community/iteration/profile-top-img.png"

    private suspend fun fetchDaySecrets(credential: AmsCredential): List<DaySecret> =
        runCatching {
            AmsDashboardParser.parseDaySecrets(remoteClient.fetchFlow(credential, DAY_SECRET_CALL))
        }.getOrDefault(emptyList())

    private suspend fun fetchToolObjects(credential: AmsCredential): List<ToolObjectSummary> =
        TOOL_OBJECT_CALLS
            .flatMap { call ->
                runCatching {
                    AmsDashboardParser.parseToolObjects(remoteClient.fetchFlow(credential, call))
                }.getOrDefault(emptyList())
            }
            .distinctBy { it.id }
            .filter { it.name.isNotBlank() && it.imageUrl.isNotBlank() }
            .take(MAX_TOOL_OBJECTS)

    private companion object {
        private const val NO_OAPI_CHART_ID = "316969"
        private const val NO_OAPI_TOKEN = "NoOapI"
        private const val KFXJWH_CHART_ID = "316968"
        private const val KFXJWH_TOKEN = "KfXJwH"
        private const val MAX_TOOL_OBJECTS = 32

        private val DAY_SECRET_CALL = AmsFlowCall(
            chartId = NO_OAPI_CHART_ID,
            sIdeToken = NO_OAPI_TOKEN,
            method = "dfm/center.day.secret",
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
