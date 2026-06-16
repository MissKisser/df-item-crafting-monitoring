package com.local.dfcraftmonitor.data.backend

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot

interface LocalBackend {
    suspend fun getCrafting(credential: AmsCredential): Result<CraftingSnapshot>

    suspend fun getDashboard(credential: AmsCredential?): Result<LocalDashboardData>

    fun getFallbackDashboard(): LocalDashboardData

    fun getToolCategories(): List<String>

    fun getToolObjects(): List<ToolObjectSummary>

    fun getDaySecrets(): List<DaySecret>

    fun getMaps(): List<MapSummary>

    fun getHomeBannerImageUrl(): String

    fun getProfileImageUrl(): String
}
