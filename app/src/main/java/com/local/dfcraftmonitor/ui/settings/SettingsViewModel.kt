package com.local.dfcraftmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.AppDataCleaner
import com.local.dfcraftmonitor.data.preference.MonitoringMode
import com.local.dfcraftmonitor.data.preference.UserPreferences
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel。暴露 [UserPreferences] Flow + 写方法 + 清除数据/退出登录。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appDataCleaner: AppDataCleaner,
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setMonitoringMode(mode: MonitoringMode) {
        viewModelScope.launch {
            userPreferencesRepository.setMonitoringMode(mode)
        }
    }

    fun setLowPowerPolicyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLowPowerPolicyEnabled(enabled)
        }
    }

    fun setWelcomeShown(shown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWelcomeShown(shown)
        }
    }

    /** 清除所有本地数据（缓存 + 偏好 + 登录凭据），然后回调通知 UI 跳登录页。 */
    fun clearDataAndLogout(onCleared: () -> Unit) {
        viewModelScope.launch {
            appDataCleaner.clearAll()
            onCleared()
        }
    }
}
