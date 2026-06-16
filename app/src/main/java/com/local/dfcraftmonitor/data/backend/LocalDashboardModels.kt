package com.local.dfcraftmonitor.data.backend

data class DaySecret(
    val mapName: String,
    val secret: String,
)

data class ToolObjectSummary(
    val id: String,
    val name: String,
    val category: String,
    val price: String,
    val trend: String,
    val imageUrl: String,
)

data class MapSummary(
    val name: String,
    val routeKey: String,
)

data class LocalDashboardData(
    val toolCategories: List<String>,
    val toolObjects: List<ToolObjectSummary>,
    val daySecrets: List<DaySecret>,
    val maps: List<MapSummary>,
    val homeBannerImageUrl: String,
    val profileImageUrl: String,
)

data class AmsFlowCall(
    val chartId: String,
    val sIdeToken: String,
    val method: String,
    val paramJson: String = "{}",
)
