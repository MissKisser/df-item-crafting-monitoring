package com.local.dfcraftmonitor.data.remote

/**
 * AMS 接口相关常量。从 spike 期 AmsApiClient 迁移而来，iChartId/sIdeToken 是
 * 腾讯接口契约，不允许改。
 */
object AmsConstants {
    /**
     * 桌面 Chrome UA，避免被 AMS 风控判成 WebView。
     * 登录 WebView 与请求拦截器共享此 UA。
     */
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * 特勤处制造数据接口。spike 路径1（POST 空 body + qc Cookie）和路径3
     * （GET + g_tk）都对它实测成功。M3 主线带 g_tk 作为容错（多带无害）。
     */
    const val CRAFTING_BASE_URL =
        "https://comm.ams.game.qq.com/ide/"

    const val CRAFTING_QUERY_PARAMS =
        "iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2"
}
