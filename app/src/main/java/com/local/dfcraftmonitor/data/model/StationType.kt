package com.local.dfcraftmonitor.data.model

/**
 * 特勤处工位类型。fromPlaceType 把 AMS 接口返回的 placeType 字符串（"tech"/"workbench"/...）
 * 映射到枚举值——这是腾讯接口契约，不允许改。
 */
enum class StationType {
    TECHNOLOGY_CENTER,
    WORKBENCH,
    PHARMACY,
    ARMORY,
    UNKNOWN;

    companion object {
        fun fromPlaceType(placeType: String?): StationType = when (placeType) {
            "tech" -> TECHNOLOGY_CENTER
            "workbench" -> WORKBENCH
            "pharmacy" -> PHARMACY
            "armory" -> ARMORY
            else -> UNKNOWN
        }
    }
}
