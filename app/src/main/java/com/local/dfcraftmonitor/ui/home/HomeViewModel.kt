package com.local.dfcraftmonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.AppDataCleaner
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import com.local.dfcraftmonitor.data.repository.CraftingRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.widget.WidgetRefresher
import com.local.dfcraftmonitor.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主界面 ViewModel。负责拉特勤处快照 + 状态管理。
 *
 * spec M2：refresh() 失败时区分 AuthExpired（提示用户重新登录）和通用错误。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val craftingRepository: CraftingRepository,
    private val sessionHolder: SessionHolder,
    private val appDataCleaner: AppDataCleaner,
    private val workScheduler: WorkScheduler,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val credential = sessionHolder.get()
        if (credential == null) {
            _state.value = UiState.NotLoggedIn
            return
        }
        _state.value = UiState.Loading
        viewModelScope.launch {
            craftingRepository.fetchCrafting(credential)
                .onSuccess {
                    _state.value = UiState.Success(it)
                    // 刷新桌面卡片（spec 8.1：手动刷新成功后更新 widget）
                    widgetRefresher.refresh()
                }
                .onFailure { e ->
                    _state.value = when (e) {
                        is AmsCraftingParser.AuthExpiredException ->
                            UiState.AuthExpired(e.message ?: "登录已失效")
                        else -> UiState.Error(e.message ?: "未知错误")
                    }
                }
        }
    }

    /**
     * 用户点"重新登录"按钮：清掉 SessionHolder，跳回 LoginScreen。
     * WorkManager 调度也停掉。
     */
    fun onAuthExpired() {
        sessionHolder.clear()
        workScheduler.cancel()
    }

    fun accountMenuInfo(): AccountMenuInfo {
        val credential = sessionHolder.get()
        val currentState = state.value
        return AccountMenuInfo(
            account = if (credential == null) {
                "未登录"
            } else {
                "${credential.acctype.uppercase()} · OpenID ${credential.openid.maskMiddle()}"
            },
            appId = credential?.appid?.takeIf { it.isNotBlank() } ?: "-",
            deltaRole = when (currentState) {
                is UiState.Success -> "已绑定（三角洲特勤处，${currentState.snapshot.stations.size} 个工位）"
                UiState.Loading -> "读取中…"
                UiState.NotLoggedIn -> "未绑定"
                is UiState.AuthExpired -> "登录失效，需重新绑定"
                is UiState.Error -> "未确认：${currentState.message}"
            },
        )
    }

    fun clearDataAndLogout(onCleared: () -> Unit) {
        viewModelScope.launch {
            appDataCleaner.clearAll()
            onCleared()
        }
    }

    sealed interface UiState {
        data object Loading : UiState
        data object NotLoggedIn : UiState
        data class Success(val snapshot: CraftingSnapshot) : UiState
        data class Error(val message: String) : UiState
        data class AuthExpired(val reason: String) : UiState
    }

    data class AccountMenuInfo(
        val account: String,
        val appId: String,
        val deltaRole: String,
    )
}

private fun String.maskMiddle(): String {
    if (length <= 8) return this
    return "${take(4)}…${takeLast(4)}"
}
