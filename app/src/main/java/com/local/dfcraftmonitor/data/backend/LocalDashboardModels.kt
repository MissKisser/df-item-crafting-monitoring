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
    val pricePoints: List<PricePoint> = emptyList(),
    val grade: Int = 0,
)

data class PricePoint(
    val label: String,
    val price: String,
    val priceValue: Long? = null,
)

data class ToolConfigItem(
    val id: String,
    val name: String,
    val type: String,
)

data class ManufacturingRecommendation(
    val id: String,
    val name: String,
    val imageUrl: String,
    val placeName: String,
    val placeType: String,
    val profit: String,
    val profitValue: Long,
    val salePrice: String,
    val costPrice: String,
    val fee: String,
    val bail: String,
    val period: String,
    val perCount: String,
    val profitPerHour: String,
    val profitPerHourValue: Long,
    val grade: Int = 0,
)

data class PlayerProfile(
    val nickname: String,
    val areaName: String,
    val avatarUrl: String,
    val avatarFrameUrl: String,
    val currentRankName: String,
    val currentRankIconUrl: String,
    val highestRankName: String,
    val highestRankIconUrl: String,
    val totalBringOutValue: String,
    val evacuationRate: String,
    val operatorKills: String,
    val profitLossRatio: String,
) {
    companion object {
        fun empty(): PlayerProfile = PlayerProfile(
            nickname = "",
            areaName = "",
            avatarUrl = "",
            avatarFrameUrl = "",
            currentRankName = "",
            currentRankIconUrl = "",
            highestRankName = "",
            highestRankIconUrl = "",
            totalBringOutValue = "",
            evacuationRate = "",
            operatorKills = "",
            profitLossRatio = "",
        )
    }
}

data class IncomeSummary(
    val amount: String,
    val rawValue: Long? = null,
    /**
     * 收益对应日期（yyyy-MM-dd 或 MM-dd），来自 solDetail 原始字段（如 statDate / dtStatDate / date）。
     * 为空时表示"无数据 / 未提供"——UI 层应回退到"近日收益"。
     */
    val date: String? = null,
) {
    companion object {
        fun empty(): IncomeSummary = IncomeSummary("")
    }
}

data class CollectionItem(
    val id: String,
    val name: String,
    val imageUrl: String,
    val value: String,
    val mapName: String,
    val count: Int = 1,
    val grade: Int = 0,
)

data class MatchRecord(
    val id: String,
    val mapName: String,
    val modeName: String,
    val result: String,
    val netIncome: String,
    val netIncomeValue: Long? = null,
    val broughtOutValue: Long? = null,
    val operatorKills: String,
    val duration: String,
    val battleTime: String,
    val operatorId: String = "",
    val operatorImageUrl: String = "",
    val operatorName: String = "",
)

data class RedArchiveRecord(
    val id: String,
    val name: String,
    val imageUrl: String,
    val value: String,
    val mapName: String,
    val foundTime: String,
    val grade: Int = 0,
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
    val profile: PlayerProfile,
    val yesterdayIncome: IncomeSummary,
    val collections: List<CollectionItem>,
    val recentMatches: List<MatchRecord>,
    val redArchive: List<RedArchiveRecord>,
    val toolConfigs: List<ToolConfigItem>,
    val manufacturingRecommendations: List<ManufacturingRecommendation>,
) {
    companion object {
        fun empty(): LocalDashboardData = LocalDashboardData(
            toolCategories = emptyList(),
            toolObjects = emptyList(),
            daySecrets = emptyList(),
            maps = emptyList(),
            homeBannerImageUrl = "",
            profileImageUrl = "",
            profile = PlayerProfile.empty(),
            yesterdayIncome = IncomeSummary.empty(),
            collections = emptyList(),
            recentMatches = emptyList(),
            redArchive = emptyList(),
            toolConfigs = emptyList(),
            manufacturingRecommendations = emptyList(),
        )
    }
}

data class AmsFlowCall(
    val chartId: String,
    val sIdeToken: String,
    val method: String,
    val paramJson: String = "{}",
)

data class AmsFormCall(
    val chartId: String,
    val sIdeToken: String,
    val fields: Map<String, String>,
)
