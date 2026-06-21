package com.local.dfcraftmonitor.data.repository

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.backend.LocalBackend
import com.local.dfcraftmonitor.data.backend.LocalDashboardData
import com.local.dfcraftmonitor.data.backend.PlayerProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 特勤处仓库。对 UI / Worker 暴露领域级制造快照，实际远程请求统一交给本地后端边界。
 *
 * V2 起，UI、Worker、Widget 都不直接拼 AMS URL，也不接触 headers/cookie 注入细节。
 */
@Singleton
class CraftingRepository @Inject constructor(
    private val localBackend: LocalBackend,
) {

    suspend fun fetchCrafting(credential: AmsCredential): Result<CraftingSnapshot> =
        localBackend.getCrafting(credential)

    suspend fun fetchDashboard(credential: AmsCredential?): Result<LocalDashboardData> =
        localBackend.getDashboard(credential)

    suspend fun fetchSolCareer(
        credential: AmsCredential,
        seasonId: Int,
        isAllSeason: Boolean,
    ): Result<PlayerProfile> =
        localBackend.fetchSolCareer(credential, seasonId, isAllSeason)
}
