package com.local.dfcraftmonitor.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import com.local.dfcraftmonitor.widget.DaySecretMapPickerContent
import com.local.dfcraftmonitor.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsDaySecretPickerUiState(
    val availableMaps: List<WidgetPayload.DaySecretEntry> = emptyList(),
    val initialSelection: Set<String> = emptySet(),
)

@HiltViewModel
class SettingsDaySecretPickerViewModel @Inject constructor(
    private val cache: WidgetCache,
    private val userPrefs: UserPreferencesRepository,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsDaySecretPickerUiState())
    val state: StateFlow<SettingsDaySecretPickerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val maps = cache.loadForWidget()?.daySecrets ?: emptyList()
            val initial = userPrefs.userPreferences.first().daySecretWidgetVisibleMaps
            _state.value = SettingsDaySecretPickerUiState(maps, initial)
        }
    }

    fun save(selected: Set<String>) {
        viewModelScope.launch {
            userPrefs.setDaySecretWidgetVisibleMaps(selected)
            widgetUpdater.forceUpdateAll()
        }
    }
}

/**
 * 设置页的"今日密码 桌面卡 已选地图"子屏。
 *
 * 入口由 [SettingsScreen] 的 onNavigateToDaySecretPicker 跳入。
 * 保存后通过 [onDone] 回调让父 NavHost 弹出回退栈。
 */
@Composable
fun DaySecretMapPickerScreen(
    title: String,
    onDone: () -> Unit,
    viewModel: SettingsDaySecretPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    DaySecretMapPickerContent(
        availableMaps = state.availableMaps,
        initialSelection = state.initialSelection,
        title = title,
        onSave = { selected ->
            viewModel.save(selected)
            onDone()
        },
    )
}
