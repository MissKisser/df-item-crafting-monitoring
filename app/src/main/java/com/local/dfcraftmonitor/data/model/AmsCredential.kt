package com.local.dfcraftmonitor.data.model

/**
 * AMS 四元组凭证：openid + acctype + appid + access_token。
 *
 * cookieHeader() 拼出的串是 AMS 接口认可的 Cookie 形态（M2 实测成功）。
 * M3 阶段只放内存里（M3-7 装配；M4 才加密落盘）。
 *
 * 字段不可变（data class val），符合"凭证不该被改写"语义。
 */
data class AmsCredential(
    val openid: String,
    val acctype: String,
    val appid: String,
    val accessToken: String,
) {
    val platform: AmsAccountPlatform
        get() = AmsAccountPlatform.fromAcctype(acctype)

    fun isComplete(): Boolean =
        openid.isNotBlank() && acctype.isNotBlank() && appid.isNotBlank() && accessToken.isNotBlank()

    /** 拼 AMS 接口认可的 Cookie 串。 */
    fun cookieHeader(): String = buildString {
        append("openid=").append(openid)
        append("; acctype=").append(acctype)
        append("; appid=").append(appid)
        append("; access_token=").append(accessToken)
    }

    companion object {
        /** 腾讯场景的便捷工厂，acctype 默认 "qc"。 */
        fun qq(openid: String, appid: String, accessToken: String): AmsCredential =
            AmsCredential(openid, "qc", appid, accessToken)

        /** 显式四参数版本，用于从 Cookie 解析（修复 Java 版丢失 acctype 的 bug）。 */
        fun create(openid: String, acctype: String, appid: String, accessToken: String): AmsCredential =
            AmsCredential(openid, acctype, appid, accessToken)
    }
}

data class AmsAccountPlatform(
    val areaName: String,
    val sArea: String,
    val gameAppId: String,
    val channelKey: String,
    val acctypeAliases: Set<String>,
) {
    companion object {
        val QQ = AmsAccountPlatform(
            areaName = "QQ区",
            sArea = "1",
            gameAppId = "101491592",
            channelKey = "qq",
            acctypeAliases = setOf("qc", "qq"),
        )

        val WECHAT = AmsAccountPlatform(
            areaName = "微信区",
            sArea = "3",
            gameAppId = "wx1cd4fbe9335888fe",
            channelKey = "weixin",
            acctypeAliases = setOf("wx", "weixin", "wechat"),
        )

        fun fromAcctype(acctype: String): AmsAccountPlatform {
            val normalized = acctype.trim().lowercase()
            return when {
                normalized in WECHAT.acctypeAliases -> WECHAT
                else -> QQ
            }
        }
    }
}
