package com.local.dfcraftmonitor.data.backend

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.remote.AmsConstants
import com.local.dfcraftmonitor.data.remote.AmsHeadersInterceptor
import com.local.dfcraftmonitor.data.remote.CraftingApi
import javax.inject.Inject

interface AmsRemoteClient {
    suspend fun fetchCraftingStatus(credential: AmsCredential): String

    suspend fun fetchFlow(credential: AmsCredential, call: AmsFlowCall): String
}

class RetrofitAmsRemoteClient @Inject constructor(
    private val craftingApi: CraftingApi,
    private val headersInterceptor: AmsHeadersInterceptor,
) : AmsRemoteClient {

    override suspend fun fetchCraftingStatus(credential: AmsCredential): String {
        headersInterceptor.cookie = credential.cookieHeader()
        val route = LocalBackendCatalog.routes.single { it.id == "player-crafting" }
        val url = "${route.remoteUrl}?${AmsConstants.CRAFTING_QUERY_PARAMS}"
        return craftingApi.getCrafting(url, gTk = 0).string()
    }

    override suspend fun fetchFlow(credential: AmsCredential, call: AmsFlowCall): String {
        headersInterceptor.cookie = credential.cookieHeader()
        val url = buildString {
            append(AmsConstants.CRAFTING_BASE_URL)
            append("?iChartId=").append(call.chartId)
            append("&iSubChartId=").append(call.chartId)
            append("&sIdeToken=").append(call.sIdeToken)
            append("&source=2")
        }
        return craftingApi.postFlow(
            url = url,
            method = call.method,
            source = "2",
            param = call.paramJson,
        ).string()
    }
}
