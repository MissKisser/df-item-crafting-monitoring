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
import com.local.dfcraftmonitor.widget.LocalWidgetConfigureBridge
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

/**
 * ViewModel 与历史上被删除的独立 Configure Activity（[com.local.dfcraftmonitor.widget.DaySecretWidgetConfigureActivity]
 * 中持有的 DaySecretConfigureViewModel）同形态。本任务把 widget 配置入口合并到
 * MainActivity + DAY_SECRET_PICKER 路由后，那个独立 ViewModel 没了；本 ViewModel
 * 唯一注入 picker 屏幕，单独存在没有引发"两处定义"的问题——为了避免无谓的"共享基类 /
 * 接口"抽象，保留独立类。
 */
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
            // 防御性 cap：UI 层 [DaySecretMapPickerContent] 已通过 DaySecretPickerState
            // 截断到 [DEFAULT_MAX_SELECTION]；此处再 take 一次作为兜底。
            // 必须与 widget 渲染层 WidgetRemoteViewsBuilder.MAX_STATIONS 保持一致。
            val capped = selected.take(MAX_STATIONS).toSet()
            userPrefs.setDaySecretWidgetVisibleMaps(capped)
            widgetUpdater.forceUpdateAll()
        }
    }

    private companion object {
        // 必须与 WidgetRemoteViewsBuilder.MAX_STATIONS 保持一致。
        const val MAX_STATIONS = 4
    }
}

/**
 * 设置页的"今日密码 桌面卡 已选地图"子屏。
 *
 * 入口：
 * - 设置页 → [com.local.dfcraftmonitor.ui.settings.SettingsScreen] 的
 *   `onNavigateToDaySecretPicker` 跳入。保存后通过 [onDone] 回调让父 NavHost 弹出回退栈。
 * - 桌面拖完 widget → Launcher 通过 APPWIDGET_CONFIGURE intent 启动
 *   [com.local.dfcraftmonitor.MainActivity]；MainActivity 把 widgetId 包成
 *   [com.local.dfcraftmonitor.widget.WidgetConfigureBridge] 注入 CompositionLocal。
 *   本屏幕检测到 bridge 非空时，保存走 `bridge.onComplete()`（Activity 内部
 *   `setResult(OK) + finish()`），返回键走 `bridge.onCancel()`（`setResult(CANCELED)`）。
 *   此时 [onDone] 不会被调用——bridge 完成时 Activity 直接 finish，与 NavHost 无关。
 */
@Composable
fun DaySecretMapPickerScreen(
    title: String,
    onDone: () -> Unit,
    viewModel: SettingsDaySecretPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val bridge = LocalWidgetConfigureBridge.current
    DaySecretMapPickerContent(
        availableMaps = state.availableMaps,
        initialSelection = state.initialSelection,
        title = title,
        maxSelection = MAX_SELECTION,
        onSave = { selected ->
            viewModel.save(selected)
            // 在 widget 配置上下文里：保存即确认，由 bridge 通知 Launcher 完成 pin。
            // 不在 widget 配置上下文里：保存即返回，让 NavHost 弹栈。
            if (bridge != null) bridge.onComplete() else onDone()
        },
    )
}

/**
 * picker 上限的默认值。必须与 [com.local.dfcraftmonitor.widget.DEFAULT_MAX_SELECTION]
 * 和 [com.local.dfcraftmonitor.widget.WidgetRemoteViewsBuilder.MAX_STATIONS] 保持一致。
 */
private const val MAX_SELECTION: Int = 4
