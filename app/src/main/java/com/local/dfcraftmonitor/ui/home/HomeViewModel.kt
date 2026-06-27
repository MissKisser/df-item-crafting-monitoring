package com.local.dfcraftmonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.AppDataCleaner
import com.local.dfcraftmonitor.data.backend.LocalDashboardData
import com.local.dfcraftmonitor.data.model.AccountEntry
import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.monitor.GlobalRefreshController
import com.local.dfcraftmonitor.data.monitor.SyncOutcome
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.repository.CraftingRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.widget.WidgetUpdater
import com.local.dfcraftmonitor.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeasonOption(
    val id: Int,
    val label: String,
    val isAllSeason: Boolean = false,
)

val SEASON_OPTIONS = listOf(
    SeasonOption(9, "当前赛季(S9)", false),
    SeasonOption(8, "S8", false),
    SeasonOption(7, "S7", false),
    SeasonOption(6, "S6", false),
    SeasonOption(5, "S5", false),
    SeasonOption(4, "S4", false),
    SeasonOption(3, "S3", false),
    SeasonOption(2, "S2", false),
    SeasonOption(1, "S1", false),
    SeasonOption(9, "全部赛季", true),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val craftingRepository: CraftingRepository,
    private val sessionHolder: SessionHolder,
    private val appDataCleaner: AppDataCleaner,
    private val workScheduler: WorkScheduler,
    private val widgetUpdater: WidgetUpdater,
    private val widgetCache: WidgetCache,
    private val globalRefreshController: GlobalRefreshController,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _dashboard = MutableStateFlow(LocalDashboardData.empty())
    val dashboard: StateFlow<LocalDashboardData> = _dashboard.asStateFlow()

    private val _selectedSeason = MutableStateFlow(SEASON_OPTIONS.first())
    val selectedSeason: StateFlow<SeasonOption> = _selectedSeason.asStateFlow()

    private val _seasonLoading = MutableStateFlow(false)
    val seasonLoading: StateFlow<Boolean> = _seasonLoading.asStateFlow()

    init {
        // VM 创建时不再触发 refresh —— 由 MainActivity.onCreate 调全局控制器，
        // 避免冷启动时两次并发请求（VM init + onCreate）。
        // 订阅全局控制器的结果 SharedFlow 更新 UiState：
        //  - snapshots 成功 → Success(snapshot)
        //  - outcomes 失败 → Error / AuthExpired
        //  这样页面 UI 与 AppBar 旋转状态共享同一份"同步事实"。
        globalRefreshController.snapshots
            .onEach { snapshot -> _state.value = UiState.Success(snapshot) }
            .launchIn(viewModelScope)
        globalRefreshController.outcomes
            .onEach { outcome ->
                _state.value = when (outcome) {
                    is SyncOutcome.AuthExpired ->
                        UiState.AuthExpired("登录已失效，请重新绑定账号。")
                    is SyncOutcome.TransientFailure -> UiState.Error(outcome.reason)
                    else -> _state.value
                }
            }
            .launchIn(viewModelScope)

        // 监听当前账号变化：从设置页切账号后回到 Home 时自动刷新数据
        sessionHolder.changes
            .map { it.currentAccountId }
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /**
     * 触发完整同步 —— 委派给 [GlobalRefreshController]。
     *
     * UiState 不会立刻切到 Loading，因为 AppBar 旋转图标已经给用户"正在刷新"的反馈，
     * 避免页面内 Spinner 与 AppBar 旋转图标双重表达造成视觉噪音。
     *
     * dashboard 数据（玩家档案/今日盈亏）不走 SyncCoordinator，仍由本 VM 拉取。
     */
    fun refresh() {
        val credential = sessionHolder.get()
        if (credential == null) {
            _state.value = UiState.NotLoggedIn
            refreshDashboard(null)
            return
        }
        viewModelScope.launch {
            refreshDashboard(credential)
            globalRefreshController.refreshAsync()
        }
    }

    private fun refreshDashboard(credential: AmsCredential?) {
        viewModelScope.launch {
            craftingRepository.fetchDashboard(credential)
                .onSuccess { dashboard ->
                    _dashboard.value = dashboard
                    val profile = dashboard.profile
                    val accountId = sessionHolder.getCurrentEntry()?.accountId
                    if (accountId != null) {
                        widgetCache.updateFromDashboard(accountId, dashboard)
                        widgetUpdater.updateAll()
                        sessionHolder.updateCurrentProfile(
                            nickname = profile.nickname,
                            avatarUrl = profile.avatarUrl,
                            areaName = profile.areaName,
                        )
                    }
                }
        }
    }

    fun switchSeason(season: SeasonOption) {
        val credential = sessionHolder.get() ?: return
        if (season == _selectedSeason.value) return
        _selectedSeason.value = season
        _seasonLoading.value = true
        viewModelScope.launch {
            craftingRepository.fetchSolCareer(credential, season.id, season.isAllSeason)
                .onSuccess { solProfile ->
                    val current = _dashboard.value
                    val merged = solProfile.copy(
                        nickname = current.profile.nickname,
                        avatarUrl = current.profile.avatarUrl,
                        areaName = current.profile.areaName,
                    )
                    _dashboard.value = current.copy(profile = merged)
                }
            _seasonLoading.value = false
        }
    }

    /** 切换账号。 */
    fun switchAccount(accountId: String) {
        val entry = sessionHolder.switchTo(accountId) ?: return
        widgetCache.setCurrentAccountId(entry.accountId)
        workScheduler.start()
        refresh()
    }

    /** 凭据失效：清除当前账号 token（保留资料），停止同步。 */
    fun onAuthExpired() {
        sessionHolder.clearCurrentSession()
        workScheduler.cancel()
    }

    /** 重新登录：清除当前会话并跳转登录页。 */
    fun reLogin(onNavigateToLogin: () -> Unit) {
        viewModelScope.launch {
            sessionHolder.clearCurrentSession()
            workScheduler.cancel()
            onNavigateToLogin()
        }
    }

    /**
     * 退出当前账号：移除账号条目，自动切换到下一个。
     * 有其他账号则刷新数据；无账号则回调通知 UI 跳登录页。
     */
    fun logoutCurrent(onNoAccountsLeft: () -> Unit) {
        viewModelScope.launch {
            val next = appDataCleaner.logoutCurrent()
            if (next != null) {
                refresh()
            } else {
                onNoAccountsLeft()
            }
        }
    }

    fun accountMenuInfo(): AccountMenuInfo {
        val credential = sessionHolder.get()
        val currentState = state.value
        return AccountMenuInfo(
            account = if (credential == null) "未登录" else "已登录",
            accountMark = if (credential == null) "访客模式" else "账号已保护",
            deltaRole = when (currentState) {
                is UiState.Success -> "已绑定（三角洲特勤处，${currentState.snapshot.stations.size} 个工位）"
                UiState.Loading -> "读取中…"
                UiState.NotLoggedIn -> "未绑定"
                is UiState.AuthExpired -> "登录失效，需重新绑定"
                is UiState.Error -> "待同步"
            },
        )
    }

    fun listAccounts(): List<AccountEntry> = sessionHolder.listAccounts()

    fun getCurrentAccount(): AccountEntry? = sessionHolder.getCurrentEntry()

    sealed interface UiState {
        data object Loading : UiState
        data object NotLoggedIn : UiState
        data class Success(val snapshot: CraftingSnapshot) : UiState
        data class Error(val message: String) : UiState
        data class AuthExpired(val reason: String) : UiState
    }

    data class AccountMenuInfo(
        val account: String,
        val accountMark: String,
        val deltaRole: String,
    )
}
