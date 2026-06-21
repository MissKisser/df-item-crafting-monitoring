package com.local.dfcraftmonitor.ui.login

import com.local.dfcraftmonitor.data.account.AccountStore
import com.local.dfcraftmonitor.data.model.AccountEntry
import com.local.dfcraftmonitor.data.model.AmsCredential
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 当前激活会话。支持多账号切换。
 *
 * AccountStore 持久化账号列表和当前账号 ID；SessionHolder 在内存中缓存当前账号条目。
 * 启动时自动从旧版 CredentialStore 合并历史 cookie（同 accountId 覆盖凭据）。
 *
 * 通过 [changes] 暴露账号列表/当前账号变更事件，供 UI 自动响应（如设置页刷新昵称/头像）。
 */
@Singleton
class SessionHolder @Inject constructor(
    private val accountStore: AccountStore,
    private val credentialStore: CredentialStore,
) {
    private val _changes = MutableSharedFlow<Change>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 账号列表或当前账号变更事件。replay=1 保证新订阅者立即拿到当前快照。 */
    val changes: SharedFlow<Change> = _changes.asSharedFlow()

    @Volatile
    private var currentEntry: AccountEntry? = initialize()

    private fun initialize(): AccountEntry? {
        currentEntry = loadCurrent() ?: accountStore.migrateFromCredentialStore(credentialStore)
        currentEntry?.let { emitSnapshot() }
        return currentEntry
    }

    private fun loadCurrent(): AccountEntry? {
        val id = accountStore.currentAccountId() ?: return null
        return accountStore.find(id)
    }

    private fun emitSnapshot() {
        _changes.tryEmit(
            Change(
                accounts = accountStore.loadAll(),
                currentAccountId = currentEntry?.accountId,
            )
        )
    }

    /**
     * 保存新账号（或覆盖已存在的同 accountId 账号的凭据），并设为当前账号。
     * 返回最终的 AccountEntry。
     */
    fun set(credential: AmsCredential): AccountEntry {
        val entry = AccountEntry.fromCredential(credential)
        val existing = accountStore.find(entry.accountId)
        if (existing != null) {
            val merged = existing.copy(
                openid = entry.openid,
                acctype = entry.acctype,
                appid = entry.appid,
                accessToken = entry.accessToken,
            )
            accountStore.update(merged)
            currentEntry = merged
        } else {
            accountStore.add(entry)
            currentEntry = entry
        }
        accountStore.setCurrentAccountId(entry.accountId)
        emitSnapshot()
        return currentEntry!!
    }

    /**
     * 检查 accountId 是否已存在（用于新增账号时去重）。
     */
    fun exists(accountId: String): Boolean = accountStore.find(accountId) != null

    /** 切换当前账号。返回切换后的 entry，或 null（accountId 不存在）。 */
    fun switchTo(accountId: String): AccountEntry? {
        val entry = accountStore.find(accountId) ?: return null
        accountStore.setCurrentAccountId(accountId)
        currentEntry = entry
        emitSnapshot()
        return entry
    }

    fun get(): AmsCredential? = currentEntry?.takeIf { it.isComplete() }?.toCredential()

    fun getCurrentEntry(): AccountEntry? = currentEntry

    /**
     * 退出当前账号：从列表移除，自动切换到剩余第一个账号。
     * 只清会话，不清缓存数据。
     * @return 切换后的新账号 entry，或 null（已无账号）。
     */
    fun logoutCurrent(): AccountEntry? {
        currentEntry?.let { accountStore.remove(it.accountId) }
        val remaining = accountStore.loadAll()
        currentEntry = remaining.firstOrNull()
        if (currentEntry != null) {
            accountStore.setCurrentAccountId(currentEntry!!.accountId)
        } else {
            accountStore.clearCurrent()
        }
        emitSnapshot()
        return currentEntry
    }

    /** 更新当前账号的资料（昵称、头像、大区）。 */
    fun updateCurrentProfile(nickname: String, avatarUrl: String, areaName: String) {
        val entry = currentEntry ?: return
        val updated = entry.copy(
            nickname = nickname.ifBlank { entry.nickname },
            avatarUrl = avatarUrl.normalizedAvatarUrl()
                .ifBlank { entry.avatarUrl.normalizedAvatarUrl() },
            areaName = areaName.ifBlank { entry.areaName },
        )
        accountStore.update(updated)
        currentEntry = updated
        emitSnapshot()
    }

    /** 清除当前账号的 token（保留账号资料），用于凭据失效场景。 */
    fun clearCurrentSession() {
        currentEntry?.let { entry ->
            val cleared = entry.copy(accessToken = "")
            accountStore.update(cleared)
            currentEntry = cleared
            emitSnapshot()
        }
    }

    fun isLoggedIn(): Boolean = currentEntry?.isComplete() == true

    fun listAccounts(): List<AccountEntry> = accountStore.loadAll()

    /** 清除全部账号数据（设置页"清除所有数据"时调用）。 */
    fun clearAll() {
        currentEntry = null
        accountStore.clearAll()
        emitSnapshot()
    }

    /** 账号变更事件快照。 */
    data class Change(
        val accounts: List<AccountEntry>,
        val currentAccountId: String?,
    )
}

private fun String.normalizedAvatarUrl(): String {
    val value = trim()
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://", ignoreCase = true) -> "https://${value.drop(7)}"
        value.startsWith("https://", ignoreCase = true) -> value
        else -> ""
    }
}
