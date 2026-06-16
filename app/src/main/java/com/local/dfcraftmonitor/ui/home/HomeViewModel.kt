package com.local.dfcraftmonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.repository.CraftingRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主界面 ViewModel。负责拉特勤处快照 + 状态管理。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val craftingRepository: CraftingRepository,
    private val sessionHolder: SessionHolder,
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
                    _state.value = UiState.Error(e.message ?: "未知错误")
                }
        }
    }

    sealed interface UiState {
        data object Loading : UiState
        data object NotLoggedIn : UiState
        data class Success(val snapshot: CraftingSnapshot) : UiState
        data class Error(val message: String) : UiState
    }
}
