package com.local.dfcraftmonitor.data.backend

import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.remote.AmsConstants
import com.local.dfcraftmonitor.data.remote.AmsHeadersInterceptor
import com.local.dfcraftmonitor.data.remote.CraftingApi
import javax.inject.Inject

interface AmsRemoteClient {
    suspend fun fetchCraftingStatus(credential: AmsCredential): String

    suspend fun fetchFlow(credential: AmsCredential, call: AmsFlowCall): String

    suspend fun fetchForm(credential: AmsCredential, call: AmsFormCall): String
}

class RetrofitAmsRemoteClient @Inject constructor(
    private val craftingApi: CraftingApi,
    private val headersInterceptor: AmsHeadersInterceptor,
) : AmsRemoteClient {

    override suspend fun fetchCraftingStatus(credential: AmsCredential): String {
        headersInterceptor.cookie = credential.cookieHeader()
        return craftingApi.postForm(
            url = AmsConstants.CRAFTING_BASE_URL,
            fields = credentialFields(credential) + mapOf(
                "iChartId" to AmsConstants.CRAFTING_CHART_ID,
                "iSubChartId" to AmsConstants.CRAFTING_CHART_ID,
                "sIdeToken" to AmsConstants.CRAFTING_IDE_TOKEN,
                "source" to "2",
            ),
        ).string()
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
        return craftingApi.postForm(
            url = url,
            fields = credentialFields(credential) + mapOf(
                "method" to call.method,
                "source" to "2",
                "param" to call.paramJson,
            ),
        ).string()
    }

    override suspend fun fetchForm(credential: AmsCredential, call: AmsFormCall): String {
        headersInterceptor.cookie = credential.cookieHeader()
        val url = buildString {
            append(AmsConstants.CRAFTING_BASE_URL)
            append("?iChartId=").append(call.chartId)
            append("&iSubChartId=").append(call.chartId)
            append("&sIdeToken=").append(call.sIdeToken)
            append("&source=2")
        }
        return craftingApi.postForm(
            url = url,
            fields = call.fields + credentialFields(credential),
        ).string()
    }

    private fun credentialFields(credential: AmsCredential): Map<String, String> =
        mapOf(
            "openid" to credential.openid,
            "acctype" to credential.acctype,
            "appid" to credential.appid,
            "access_token" to credential.accessToken,
        )
}
