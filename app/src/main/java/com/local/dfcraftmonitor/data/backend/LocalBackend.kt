package com.local.dfcraftmonitor.data.backend

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot

interface LocalBackend {
    suspend fun getCrafting(credential: AmsCredential): Result<CraftingSnapshot>

    suspend fun getDashboard(credential: AmsCredential?): Result<LocalDashboardData>

    suspend fun fetchSolCareer(
        credential: AmsCredential,
        seasonId: Int,
        isAllSeason: Boolean,
    ): Result<PlayerProfile>
}
