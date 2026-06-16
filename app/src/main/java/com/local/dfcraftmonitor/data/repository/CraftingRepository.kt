package com.local.dfcraftmonitor.data.repository

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.remote.AmsConstants
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import com.local.dfcraftmonitor.data.remote.AmsHeadersInterceptor
import com.local.dfcraftmonitor.data.remote.CraftingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 特勤处仓库。封装"注入 Cookie → 调 AMS API → 解析响应"链路，对外暴露
 * suspend 函数 + Result，让 UI 层无需关心网络细节。
 *
 * M3 阶段只做单账号内存会话；M4 接入 Room 后这里加本地缓存。
 */
@Singleton
class CraftingRepository @Inject constructor(
    private val craftingApi: CraftingApi,
    private val headersInterceptor: AmsHeadersInterceptor,
) {

    /**
     * 用 [credential] 拉特勤处当前快照。
     * 流程：临时把 credential 的 qc Cookie 注入拦截器 → GET → 解析 jData 树。
     */
    suspend fun fetchCrafting(credential: AmsCredential): Result<CraftingSnapshot> = runCatching {
        headersInterceptor.cookie = credential.cookieHeader()
        val url = "${AmsConstants.CRAFTING_BASE_URL}?${AmsConstants.CRAFTING_QUERY_PARAMS}"
        val body = craftingApi.getCrafting(url, gTk = 0).string()
        AmsCraftingParser.parse(body)
    }
}
