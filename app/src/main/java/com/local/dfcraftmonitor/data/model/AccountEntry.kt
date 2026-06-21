package com.local.dfcraftmonitor.data.model

import kotlinx.serialization.Serializable

/**
 * 账号条目。在 [com.local.dfcraftmonitor.data.account.AccountStore] 中以列表形式持久化。
 *
 * accountId 用 `${acctype}_${openid}` 保证 QQ 区和微信区不冲突。
 * nickname / avatarUrl / areaName 在登录后异步拉取仪表盘数据时填充。
 */
@Serializable
data class AccountEntry(
    val accountId: String,
    val openid: String,
    val acctype: String,
    val appid: String,
    val accessToken: String,
    val nickname: String = "",
    val avatarUrl: String = "",
    val areaName: String = "",
) {
    fun toCredential(): AmsCredential = AmsCredential(openid, acctype, appid, accessToken)

    fun isComplete(): Boolean =
        openid.isNotBlank() && acctype.isNotBlank() && appid.isNotBlank() && accessToken.isNotBlank()

    companion object {
        fun fromCredential(credential: AmsCredential): AccountEntry = AccountEntry(
            accountId = "${credential.acctype}_${credential.openid}",
            openid = credential.openid,
            acctype = credential.acctype,
            appid = credential.appid,
            accessToken = credential.accessToken,
        )
    }
}
