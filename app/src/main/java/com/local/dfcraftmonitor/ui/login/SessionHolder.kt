package com.local.dfcraftmonitor.ui.login

import com.local.dfcraftmonitor.data.model.AmsCredential
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前激活会话（内存版）。M3 不持久化；App 重启需重新登录。
 * M4 替换为 Room + DataStore + 加密的账号仓库。
 */
@Singleton
class SessionHolder @Inject constructor() {
    @Volatile
    private var current: AmsCredential? = null

    fun set(credential: AmsCredential) {
        current = credential
    }

    fun get(): AmsCredential? = current

    fun clear() {
        current = null
    }

    fun isLoggedIn(): Boolean = current != null
}
