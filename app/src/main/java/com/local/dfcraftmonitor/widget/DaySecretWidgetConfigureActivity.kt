package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.R
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DaySecretConfigureState(
    val availableMaps: List<WidgetPayload.DaySecretEntry> = emptyList(),
    val initialSelection: Set<String> = emptySet(),
)

@HiltViewModel
class DaySecretConfigureViewModel @Inject constructor(
    private val cache: WidgetCache,
    private val userPrefs: UserPreferencesRepository,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val _state = MutableStateFlow(DaySecretConfigureState())
    val state: StateFlow<DaySecretConfigureState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val maps = cache.loadForWidget()?.daySecrets ?: emptyList()
            val initial = userPrefs.userPreferences.first().daySecretWidgetVisibleMaps
            _state.value = DaySecretConfigureState(maps, initial)
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
 * 拖放 widget 后弹出的配置页（LaunchMode=standard，由系统用
 * APPWIDGET_CONFIGURE intent 启动）。
 *
 * 关闭时需要返回带 EXTRA_APPWIDGET_ID 的 OK result，Launcher 才会完成拖放流程。
 */
@AndroidEntryPoint
class DaySecretWidgetConfigureActivity : ComponentActivity() {

    private val viewModel: DaySecretConfigureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        setContent {
            val state by viewModel.state.collectAsState()
            DaySecretMapPickerContent(
                availableMaps = state.availableMaps,
                initialSelection = state.initialSelection,
                title = getString(R.string.widget_day_secret_configure_title),
                onSave = { selected ->
                    viewModel.save(selected)
                    val result = Intent().apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                },
            )
        }
    }
}
