package com.local.dfcraftmonitor.data.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class DefaultLocalBackendTest {

    @Test
    fun defaultBackendReturnsEmptyDashboardWithoutCredential() = runTest {
        val backend = DefaultLocalBackend(FakeAmsRemoteClient)

        val dashboard = backend.getDashboard(null).getOrThrow()

        assertTrue(dashboard.toolCategories.isEmpty())
        assertTrue(dashboard.toolObjects.isEmpty())
        assertTrue(dashboard.daySecrets.isEmpty())
        assertTrue(dashboard.maps.isEmpty())
        assertEquals("", dashboard.homeBannerImageUrl)
        assertEquals("", dashboard.profileImageUrl)
        assertTrue(dashboard.toolConfigs.isEmpty())
        assertTrue(dashboard.manufacturingRecommendations.isEmpty())
    }

    @Test
    fun defaultBackendUsesRemoteDashboardDataWhenCredentialExists() = runTest {
        val remote = DashboardRemoteClient()
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create("openid", "qc", "appid", "token")

        val dashboard = backend.getDashboard(credential).getOrThrow()

        assertTrue(remote.calls.any { it.method == "dfm/center.day.secret" })
        assertTrue(remote.calls.any { it.method == "dfm/place.list" })
        assertTrue(remote.calls.any { it.method == "user.info" && it.paramJson == "" })
        assertTrue(
            remote.calls.any {
                it.method == "dfm/center.person.resource" &&
                    it.paramJson.contains("\"resourceType\":\"sol\"") &&
                    it.paramJson.contains("\"isAllSeason\":false")
            },
        )
        assertTrue(
            remote.calls.any {
                it.method == "dfm/center.recent.detail" &&
                    it.paramJson.contains("\"resourceType\":\"sol\"")
            },
        )
        assertTrue(remote.calls.any { it.method == "dfm/center.recent.redUnlockRecord" })
        assertTrue(remote.calls.none { it.method == "fortune.user" })
        assertTrue(remote.calls.none { it.method == "dfm/center.recent.sales" })
        assertTrue(remote.formCalls.any { it.sIdeToken == "PHq59Y" && it.fields["type"] == "4" && it.fields["page"] == "1" })
        assertEquals(listOf(MapSummary("技术中心", "tech")), dashboard.maps)
        assertEquals("干员K", dashboard.profile.nickname)
        assertEquals("三角洲巅峰", dashboard.profile.currentRankName)
        assertEquals("50%", dashboard.profile.evacuationRate)
        assertEquals("88万", dashboard.yesterdayIncome.amount)
        assertEquals("非洲之心", dashboard.collections.single().name)
        assertEquals("b1", dashboard.recentMatches.single().id)
        assertEquals("曼德尔砖", dashboard.redArchive.single().name)
        assertEquals(listOf(DaySecret("零号大坝", "7391")), dashboard.daySecrets)
        assertEquals("测试制造枪", dashboard.manufacturingRecommendations.first().name)
        assertEquals("技术中心", dashboard.manufacturingRecommendations.first().placeName)
        assertEquals("7.1万", dashboard.manufacturingRecommendations.first().profit)
        assertEquals("18.4万", dashboard.manufacturingRecommendations.first().salePrice)
    }

    @Test
    fun dashboardUsesWechatPlatformParametersForWechatCredential() = runTest {
        val remote = DashboardRemoteClient()
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create(
            openid = "openid",
            acctype = "wx",
            appid = "wx1cd4fbe9335888fe",
            accessToken = "token",
        )

        val dashboard = backend.getDashboard(credential).getOrThrow()

        assertEquals("微信区", dashboard.profile.areaName)
        assertTrue(
            "role/profile flow should carry the selected platform area",
            remote.calls.any { it.paramJson.contains("\"sArea\":\"3\"") || it.paramJson.contains("\"sArea\":3") },
        )
        assertTrue(
            "recent match form should carry WeChat platform identity",
            remote.formCalls.any { call ->
                call.fields["sArea"] == "3" ||
                    call.fields["area"] == "3" ||
                    call.fields["appid"] == "wx1cd4fbe9335888fe" ||
                    call.fields["item"].orEmpty().contains("3")
            },
        )
    }

    @Test
    fun solCareerUsesWechatPlatformParametersForWechatCredential() = runTest {
        val remote = DashboardRemoteClient()
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create(
            openid = "openid",
            acctype = "wx",
            appid = "wx1cd4fbe9335888fe",
            accessToken = "token",
        )

        backend.fetchSolCareer(credential, seasonId = 9, isAllSeason = false).getOrThrow()

        val call = remote.calls.last { it.method == "dfm/center.person.resource" }
        assertTrue(call.paramJson.contains("\"sArea\":\"3\"") || call.paramJson.contains("\"sArea\":3"))
        assertTrue(call.paramJson.contains("\"appid\":\"wx1cd4fbe9335888fe\""))
        assertTrue(call.paramJson.contains("\"channel\":\"weixin\""))
    }

    @Test
    fun dashboardKeepsPartialRemoteDataWhenOneFlowFails() = runTest {
        val remote = DashboardRemoteClient(failFirstObjectCall = true)
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create("openid", "qc", "appid", "token")

        val dashboard = backend.getDashboard(credential).getOrThrow()

        assertEquals(listOf(DaySecret("零号大坝", "7391")), dashboard.daySecrets)
    }

    @Test
    fun dashboardDoesNotBackfillLocalDataWhenRemoteFlowsAreEmpty() = runTest {
        val remote = DashboardRemoteClient(returnEmptyLists = true)
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create("openid", "qc", "appid", "token")

        val dashboard = backend.getDashboard(credential).getOrThrow()

        assertTrue(remote.calls.any { it.method == "dfm/center.day.secret" })
        assertTrue(dashboard.daySecrets.isEmpty())
        assertTrue(dashboard.maps.isEmpty())
        assertTrue(dashboard.manufacturingRecommendations.isEmpty())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun dashboardDoesNotLetDetailedPriceCallsBlockProfileAndRecentData() = runTest {
        val remote = DashboardRemoteClient(priceDelayMillis = 1_000)
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create("openid", "qc", "appid", "token")

        val dashboard = backend.getDashboard(credential).getOrThrow()

        assertEquals("干员K", dashboard.profile.nickname)
        assertEquals("88万", dashboard.yesterdayIncome.amount)
        assertTrue("dashboard should fetch independent sections concurrently", currentTime < 2_000)
    }

    private object FakeAmsRemoteClient : AmsRemoteClient {
        override suspend fun fetchCraftingStatus(credential: com.local.dfcraftmonitor.data.model.AmsCredential): String =
            error("not used")

        override suspend fun fetchFlow(
            credential: com.local.dfcraftmonitor.data.model.AmsCredential,
            call: AmsFlowCall,
        ): String = error("not used")

        override suspend fun fetchForm(
            credential: com.local.dfcraftmonitor.data.model.AmsCredential,
            call: AmsFormCall,
        ): String = error("not used")
    }

    private class DashboardRemoteClient(
        private val failFirstObjectCall: Boolean = false,
        private val returnEmptyLists: Boolean = false,
        private val priceDelayMillis: Long = 0,
    ) : AmsRemoteClient {
        val calls = mutableListOf<AmsFlowCall>()
        val formCalls = mutableListOf<AmsFormCall>()

        override suspend fun fetchCraftingStatus(credential: com.local.dfcraftmonitor.data.model.AmsCredential): String =
            error("not used")

        override suspend fun fetchFlow(
            credential: com.local.dfcraftmonitor.data.model.AmsCredential,
            call: AmsFlowCall,
        ): String {
            calls += call
            if (priceDelayMillis > 0 && call.method.startsWith("dfm/object.price")) {
                delay(priceDelayMillis)
            }
            if (failFirstObjectCall && call.method == "dfm/object.list" && calls.count { it.method == "dfm/object.list" } == 1) {
                error("temporary upstream failure")
            }
            if (returnEmptyLists) {
                return """{"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[]}}}}"""
            }
            return when (call.method) {
                "dfm/center.day.secret" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[{"mapName":"零号大坝","secret":"7391"}]}}}}
                """.trimIndent()
                "dfm/object.list" -> {
                    if (call.paramJson.contains("\"objectID\":\"\"")) {
                        """
                        {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[{"objectID":18010000001,"objectName":"测试步枪","primaryClass":"gun","secondClassCN":"步枪","avgPrice":120000,"pic":"https://example.test/object.png"}]}}}}
                        """.trimIndent()
                    } else {
                        """
                        {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[
                          {"objectID":15080050003,"objectName":"非洲之心","primaryClass":"props","secondClassCN":"收藏品","avgPrice":520000,"pic":"https://example.test/red.png"},
                          {"objectID":15090000001,"objectName":"曼德尔砖","primaryClass":"props","secondClassCN":"收藏品","avgPrice":780000,"pic":"https://example.test/m.png"},
                          {"objectID":11010005010,"objectName":"测试制造枪","primaryClass":"gun","secondClassCN":"步枪","avgPrice":183809,"pic":"https://example.test/make.png"}
                        ]}}}}
                        """.trimIndent()
                    }
                }
                "dfm/place.list" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[
                      {"placeName":"技术中心","placeType":"tech","placeDetail":{"unlock":{"props":[
                        {"salePrice":183809,"costPrice":91374,"fee":21450,"objectID":11010005010,"perCount":1,"period":"14"},
                        {"salePrice":125332,"costPrice":188007,"fee":14149,"objectID":13160000016,"perCount":4,"period":"4.5"}
                      ]}}}
                    ]}}}}
                """.trimIndent()
                "dfm/object.price.hour" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[{"hour":"09:00","avgPrice":100000},{"hour":"10:00","avgPrice":120000}]}}}}
                """.trimIndent()
                "user.info" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"nickName":"干员K","areaName":"QQ区","avatar":"https://example.test/avatar.png","avatarFrame":"https://example.test/frame.png"}}}}}
                """.trimIndent()
                "dfm/center.person.resource" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"solDetail":{
                      "levelScore":"6008",
                      "majorLevelMax":"7",
                      "totalEscape":"5",
                      "totalFight":"10",
                      "totalGainedPrice":"12345678",
                      "totalKill":"321",
                      "profitLossRatio":"145",
                      "redCollectionDetail":[{"objectID":15090000001,"count":1,"price":780000}]
                    }}}}}
                """.trimIndent()
                "dfm/center.recent.detail" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"solDetail":{
                      "recentGain":880000,
                      "recentGainDate":"2026-06-16",
                      "userCollectionTop":{"list":[{"objectID":15080050003,"count":1,"price":520000}]}
                    }}}}}
                """.trimIndent()
                "dfm/center.recent.redUnlockRecord" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"total":1,"list":[{"time":"昨天","itemId":"15090000001","mapid":"1902","num":1,"des":"出红"}]}}}}
                """.trimIndent()
                "dfm/config.list" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[{"id":"gun","name":"枪械","type":"category"}]}}}}
                """.trimIndent()
                "dfm/object.price.latest", "dfm/object.price.recent", "dfm/config.white.list" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[]}}}}
                """.trimIndent()
                else -> """{"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{}}}}"""
            }
        }

        override suspend fun fetchForm(
            credential: com.local.dfcraftmonitor.data.model.AmsCredential,
            call: AmsFormCall,
        ): String {
            formCalls += call
            if (returnEmptyLists) {
                return """{"ret":0,"iRet":0,"jData":{"data":[]}}"""
            }
            return """
                {"ret":0,"iRet":0,"jData":{"data":[
                  {
                    "MapId":"2201",
                    "dtEventTime":"今天 12:00",
                    "DurationS":1122,
                    "KillCount":3,
                    "KillPlayerAICount":1,
                    "flowCalGainedPrice":"320000",
                    "RoomId":"b1",
                    "BattleResult":"撤离成功"
                  }
                ]}}
            """.trimIndent()
        }
    }
}
