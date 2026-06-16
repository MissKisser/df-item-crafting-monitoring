package com.local.dfcraftmonitor.data.remote

import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * AMS 特勤处接口的 Retrofit 封装。GET 形式（与 OkHttp 拦截器配合）：
 * - 桌面 UA / Referer / Origin / X-Requested-With / Cookie 由 [AmsHeadersInterceptor] 统一注入
 * - g_tk 是备用签名参数（spike 路径3 验证有效；AMS 实际不强制要求时多带无害）
 */
interface CraftingApi {

    /**
     * GET 特勤处接口，g_tk 拼到 query。
     * 用 @Url 是因为 endpoint 已经有完整 query string 且需要动态拼 g_tk。
     */
    @GET
    suspend fun getCrafting(
        @Url url: String,
        @Query("g_tk") gTk: Int = 0,
    ): ResponseBody

    @FormUrlEncoded
    @POST
    suspend fun postFlow(
        @Url url: String,
        @Field("method") method: String,
        @Field("source") source: String = "2",
        @Field("param") param: String = "{}",
    ): ResponseBody
}
