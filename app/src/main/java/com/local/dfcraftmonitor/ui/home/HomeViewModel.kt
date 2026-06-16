package com.local.dfcraftmonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import com.local.dfcraftmonitor.data.repository.CraftingRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
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
    private val workScheduler: WorkScheduler,
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
                .onSuccess { _state.value = UiState.Success(it) }
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

    sealed interface UiState {
        data object Loading : UiState
        data object NotLoggedIn : UiState
        data class Success(val snapshot: CraftingSnapshot) : UiState
        data class Error(val message: String) : UiState
        data class AuthExpired(val reason: String) : UiState
    }
}
