package com.local.dfcraftmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.AppDataCleaner
import com.local.dfcraftmonitor.data.model.AccountEntry
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.preference.UserPreferences
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.widget.WidgetUpdater
import com.local.dfcraftmonitor.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel。管理账号列表、Widget 锁定、通知开关。
 *
 * 账号列表与当前账号通过订阅 [SessionHolder.changes] 自动更新，
 * 无需 UI 手动调用 refreshAccounts()。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appDataCleaner: AppDataCleaner,
    private val widgetCache: WidgetCache,
    private val widgetUpdater: WidgetUpdater,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _accounts = MutableStateFlow(sessionHolder.listAccounts())
    val accounts: StateFlow<List<AccountEntry>> = _accounts.asStateFlow()

    private val _currentAccountId = MutableStateFlow(sessionHolder.getCurrentEntry()?.accountId)
    val currentAccountId: StateFlow<String?> = _currentAccountId.asStateFlow()

    init {
        sessionHolder.changes
            .onEach { change ->
                _accounts.value = change.accounts
                _currentAccountId.value = change.currentAccountId
            }
            .launchIn(viewModelScope)
    }

    fun setCraftingNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCraftingNotificationEnabled(enabled)
        }
    }

    fun setWelcomeShown(shown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWelcomeShown(shown)
        }
    }

    /** 切换当前账号。 */
    fun switchAccount(accountId: String) {
        val entry = sessionHolder.switchTo(accountId) ?: return
        widgetCache.setCurrentAccountId(accountId)
        workScheduler.start()
        widgetUpdater.updateAll()
    }

    /**
     * 退出当前账号。
     * @param onResult 返回切换后的新账号（null 表示已无账号）
     */
    fun logoutCurrent(onResult: (AccountEntry?) -> Unit) {
        viewModelScope.launch {
            val next = appDataCleaner.logoutCurrent()
            onResult(next)
        }
    }

    /** 设置 Widget 锁定账号。null 表示跟随当前账号。 */
    fun setWidgetLockedAccount(accountId: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setWidgetLockedAccountId(accountId)
            widgetCache.setLockedAccountId(accountId)
            widgetUpdater.updateAll()
        }
    }

    /** 清除所有本地数据（全部账号 + 缓存 + 偏好）。 */
    fun clearAll(onCleared: () -> Unit) {
        viewModelScope.launch {
            appDataCleaner.clearAll()
            onCleared()
        }
    }
}
