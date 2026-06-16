package com.local.dfcraftmonitor.data.repository

import com.local.dfcraftmonitor.data.backend.LocalBackend
import com.local.dfcraftmonitor.data.backend.DaySecret
import com.local.dfcraftmonitor.data.backend.LocalDashboardData
import com.local.dfcraftmonitor.data.backend.MapSummary
import com.local.dfcraftmonitor.data.backend.ToolObjectSummary
import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CraftingRepositoryBackendTest {

    @Test
    fun repositoryFetchesCraftingThroughLocalBackend() = runTest {
        val expected = CraftingSnapshot.create(
            serverNowEpochSeconds = 100,
            fetchedAtEpochMillis = 200,
            stations = listOf(
                CraftingStation(
                    type = StationType.TECHNOLOGY_CENTER,
                    placeName = "技术中心",
                    status = "1",
                    itemId = 42,
                    itemName = "测试物品",
                    iconUrl = null,
                    avgPrice = null,
                    remainingSeconds = 10,
                    finishAtEpochSeconds = 110,
                ),
            ),
        )
        val backend = FakeLocalBackend(Result.success(expected))
        val repository = CraftingRepository(backend)
        val credential = AmsCredential.create("openid", "qc", "appid", "token")

        val result = repository.fetchCrafting(credential)

        assertEquals(expected, result.getOrThrow())
        assertEquals(credential, backend.lastCredential)
    }

    private class FakeLocalBackend(
        private val craftingResult: Result<CraftingSnapshot>,
    ) : LocalBackend {
        var lastCredential: AmsCredential? = null

        override suspend fun getCrafting(credential: AmsCredential): Result<CraftingSnapshot> {
            lastCredential = credential
            return craftingResult
        }

        override suspend fun getDashboard(credential: AmsCredential?): Result<LocalDashboardData> =
            Result.success(fallbackDashboard)

        override fun getFallbackDashboard(): LocalDashboardData = fallbackDashboard

        override fun getToolCategories(): List<String> = emptyList()

        override fun getToolObjects(): List<ToolObjectSummary> = emptyList()

        override fun getDaySecrets(): List<DaySecret> = emptyList()

        override fun getMaps(): List<MapSummary> = emptyList()

        override fun getHomeBannerImageUrl(): String = "https://example.test/banner.png"

        override fun getProfileImageUrl(): String = "https://example.test/profile.png"

        private val fallbackDashboard = LocalDashboardData(
            toolCategories = emptyList(),
            toolObjects = emptyList(),
            daySecrets = emptyList(),
            maps = emptyList(),
            homeBannerImageUrl = "https://example.test/banner.png",
            profileImageUrl = "https://example.test/profile.png",
        )
    }
}
