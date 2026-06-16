package com.local.dfcraftmonitor.data.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class LocalBackendCatalogTest {

    @Test
    fun catalogContainsPublicAndAuthenticatedReadRoutes() {
        val routes = LocalBackendCatalog.routes

        assertRoute(routes, "GET /api/public/home", BackendRouteCategory.PUBLIC_READ)
        assertRoute(routes, "GET /api/public/objects", BackendRouteCategory.PUBLIC_READ)
        assertRoute(routes, "GET /api/public/object/{objectId}/prices", BackendRouteCategory.PUBLIC_READ)
        assertRoute(routes, "GET /api/public/places", BackendRouteCategory.PUBLIC_READ)
        assertRoute(routes, "GET /api/player/crafting", BackendRouteCategory.AUTH_READ)
        assertRoute(routes, "GET /api/player/profile", BackendRouteCategory.AUTH_READ)
        assertRoute(routes, "GET /api/player/resources", BackendRouteCategory.AUTH_READ)
    }

    @Test
    fun writeMethodsAreCatalogedButDisabled() {
        val writeRoute = LocalBackendCatalog.routes.single { it.id == "disabled-write-actions" }

        assertEquals(BackendRouteCategory.AUTH_WRITE, writeRoute.category)
        assertFalse(writeRoute.enabled)
        assertTrue(writeRoute.remoteMethods.contains("thread.like"))
        assertTrue(writeRoute.remoteMethods.contains("third.content.like"))
        assertTrue(writeRoute.remoteMethods.contains("task/rewardTask"))
        assertTrue(writeRoute.localRoute == null)
    }

    @Test
    fun craftingRoutePointsToCentralizedAmsEndpoint() {
        val route = LocalBackendCatalog.routes.single { it.id == "player-crafting" }

        assertEquals("amsIde", route.remoteEndpointId)
        assertEquals(AmsEndpoint.CRAFTING_STATUS.url, route.remoteUrl)
        assertEquals("365589", route.query["iChartId"])
        assertEquals("bQaMCQ", route.query["sIdeToken"])
    }

    @Test
    fun defaultBackendProvidesLocalDashboardDataForUi() {
        val backend = DefaultLocalBackend(FakeAmsRemoteClient)

        assertTrue(backend.getToolCategories().containsAll(listOf("枪械", "配件", "子弹", "防具", "道具", "载具", "地点")))
        assertTrue(backend.getToolObjects().all { it.imageUrl.startsWith("https://") })
        assertEquals(3, backend.getDaySecrets().size)
        assertFalse(backend.getToolCategories().contains("攻略"))
    }

    @Test
    fun defaultBackendUsesRemoteDashboardDataWhenCredentialExists() = runTest {
        val remote = DashboardRemoteClient()
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create("openid", "qc", "appid", "token")

        val dashboard = backend.getDashboard(credential).getOrThrow()

        assertTrue(remote.calls.any { it.method == "dfm/center.day.secret" })
        assertTrue(remote.calls.any { it.method == "dfm/object.list" })
        assertEquals(listOf(DaySecret("零号大坝", "7391")), dashboard.daySecrets)
        assertEquals("测试步枪", dashboard.toolObjects.first().name)
        assertEquals("枪械 / 步枪", dashboard.toolObjects.first().category)
    }

    @Test
    fun dashboardKeepsPartialRemoteDataWhenOneFlowFails() = runTest {
        val remote = DashboardRemoteClient(failFirstObjectCall = true)
        val backend = DefaultLocalBackend(remote)
        val credential = com.local.dfcraftmonitor.data.model.AmsCredential.create("openid", "qc", "appid", "token")

        val dashboard = backend.getDashboard(credential).getOrThrow()

        assertEquals(listOf(DaySecret("零号大坝", "7391")), dashboard.daySecrets)
        assertEquals("测试步枪", dashboard.toolObjects.first().name)
    }

    private fun assertRoute(
        routes: List<BackendRoute>,
        localRoute: String,
        category: BackendRouteCategory,
    ) {
        val route = routes.firstOrNull { it.localRoute == localRoute }
        assertNotNull("Missing route $localRoute", route)
        assertEquals(category, route!!.category)
        assertTrue("Route $localRoute should be enabled", route.enabled)
    }

    private object FakeAmsRemoteClient : AmsRemoteClient {
        override suspend fun fetchCraftingStatus(credential: com.local.dfcraftmonitor.data.model.AmsCredential): String =
            error("not used")

        override suspend fun fetchFlow(
            credential: com.local.dfcraftmonitor.data.model.AmsCredential,
            call: AmsFlowCall,
        ): String = error("not used")
    }

    private class DashboardRemoteClient(
        private val failFirstObjectCall: Boolean = false,
    ) : AmsRemoteClient {
        val calls = mutableListOf<AmsFlowCall>()

        override suspend fun fetchCraftingStatus(credential: com.local.dfcraftmonitor.data.model.AmsCredential): String =
            error("not used")

        override suspend fun fetchFlow(
            credential: com.local.dfcraftmonitor.data.model.AmsCredential,
            call: AmsFlowCall,
        ): String {
            calls += call
            if (failFirstObjectCall && call.method == "dfm/object.list" && calls.count { it.method == "dfm/object.list" } == 1) {
                error("temporary upstream failure")
            }
            return when (call.method) {
                "dfm/center.day.secret" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[{"mapName":"零号大坝","secret":"7391"}]}}}}
                """.trimIndent()
                "dfm/object.list" -> """
                    {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[{"objectID":18010000001,"objectName":"测试步枪","primaryClass":"gun","secondClassCN":"步枪","avgPrice":120000,"pic":"https://example.test/object.png"}]}}}}
                """.trimIndent()
                else -> """{"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{}}}}"""
            }
        }
    }
}
