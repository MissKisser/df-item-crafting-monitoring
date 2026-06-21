package com.local.dfcraftmonitor.data.account

import android.content.Context
import com.local.dfcraftmonitor.data.model.AccountEntry
import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.ui.login.CredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多账号持久化存储。
 *
 * 使用 SharedPreferences + JSON 序列化存储账号列表和当前激活账号 ID。
 * 首次升级时从 [CredentialStore] 迁移旧的单账号数据。
 */
@Singleton
class AccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 加载全部账号。 */
    fun loadAll(): List<AccountEntry> {
        val text = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<AccountEntry>>(text)
        }.getOrDefault(emptyList())
    }

    /** 保存全部账号（覆盖写）。 */
    fun saveAll(accounts: List<AccountEntry>) {
        prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(ListSerializer(AccountEntry.serializer()), accounts)).apply()
    }

    /**
     * 追加新账号。
     * @return true 成功；false 表示 accountId 已存在。
     */
    fun add(entry: AccountEntry): Boolean {
        val accounts = loadAll().toMutableList()
        if (accounts.any { it.accountId == entry.accountId }) return false
        accounts.add(entry)
        saveAll(accounts)
        return true
    }

    /** 更新已有账号（按 accountId 匹配）。 */
    fun update(entry: AccountEntry) {
        saveAll(loadAll().map { if (it.accountId == entry.accountId) entry else it })
    }

    /** 移除账号。 */
    fun remove(accountId: String) {
        saveAll(loadAll().filter { it.accountId != accountId })
    }

    fun find(accountId: String): AccountEntry? = loadAll().find { it.accountId == accountId }

    fun currentAccountId(): String? = prefs.getString(KEY_CURRENT, null)

    fun setCurrentAccountId(accountId: String) {
        prefs.edit().putString(KEY_CURRENT, accountId).apply()
    }

    fun clearCurrent() {
        prefs.edit().remove(KEY_CURRENT).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * 从旧版 [CredentialStore] 迁移数据。仅在 AccountStore 为空且 CredentialStore 有数据时执行。
     *
     * 设计原因：addMode 进入时已通过 WebSessionCleaner 清空 WebView Cookie，
     * 旧 CredentialStore 不会再被新登录流程访问；保留旧数据反而可能让"已存在"检查误判。
     * @return 迁移出的 AccountEntry，或 null 表示无需迁移
     */
    fun migrateFromCredentialStore(credentialStore: CredentialStore): AccountEntry? {
        if (loadAll().isNotEmpty()) return null
        val oldCredential = credentialStore.load() ?: return null
        val entry = AccountEntry.fromCredential(oldCredential)
        add(entry)
        setCurrentAccountId(entry.accountId)
        credentialStore.clear()
        return entry
    }

    companion object {
        private const val PREFS_NAME = "df_account_store"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CURRENT = "current_account_id"
    }
}
