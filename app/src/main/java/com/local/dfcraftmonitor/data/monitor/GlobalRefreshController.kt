package com.local.dfcraftmonitor.data.monitor

import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.work.WorkScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局刷新控制器（spec "全局刷新"）。
 *
 * 单一入口：所有"全 App 同步"场景都走 [refreshAsync]：
 *  - AppBar 右上角刷新按钮（用户主动触发）
 *  - App 冷启动（已登录时在 MainActivity.onCreate 触发）
 *  - 登录成功 / 切账号 / 凭据失效恢复
 *  - Widget 刷新按钮 / WorkManager 周期任务最终都回到 [SyncCoordinator.syncOnce]，
 *    本控制器是 UI 层的便捷封装
 *
 * 设计要点：
 *  - **走完整 SyncCoordinator 链路**：解决 [com.local.dfcraftmonitor.ui.home.HomeViewModel.refresh]
 *    旧版只调 `fetchCrafting` 而绕过 SyncDiff / CompletionTimerScheduler / Notifier 的 bug。
 *  - **去抖**：300ms 内的多次点击只触发一次，避免用户连点造成服务器压力。
 *  - **状态机**：暴露 [state] 给 AppBar 驱动旋转动画与失败提示。
 *  - **副作用**：每次 refresh 顺手启动 WorkManager 周期任务，解决"App 启动后
 *    周期同步没起来"的问题。
 *  - **结果订阅**：每次成功后把 snapshot 通过 [snapshots] SharedFlow 推送给
 *    UI 层（如 HomeViewModel）以更新 UiState，**避免 UI 自行再调一次 fetchCrafting
 *    导致双请求**。
 */
@Singleton
class GlobalRefreshController @Inject constructor(
    private val syncCoordinator: SyncEngine,
    private val snapshotStore: SnapshotStore,
    private val workScheduler: WorkScheduler,
) {
    /**
     * 去抖窗口（毫秒）。默认 [DEFAULT_DEBOUNCE_MILLIS]。
     * 单测可通过副构造传入 0L 加速测试节奏。
     */
    constructor(
        syncCoordinator: SyncEngine,
        snapshotStore: SnapshotStore,
        workScheduler: WorkScheduler,
        debounceMillis: Long,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(syncCoordinator, snapshotStore, workScheduler) {
        this.debounceMillis = debounceMillis
        this.scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    private var debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastTriggerAt: Long = 0L
    private var inFlight: Job? = null

    private val _state = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val state: StateFlow<RefreshState> = _state.asStateFlow()

    /**
     * 成功同步后推送最新 snapshot。HomeViewModel 等 UI 订阅它以更新页面状态。
     * replay=1：新订阅者立即拿到最近一次结果（覆盖冷启动场景）。
     */
    private val _snapshots = MutableSharedFlow<CraftingSnapshot>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    val snapshots: SharedFlow<CraftingSnapshot> = _snapshots.asSharedFlow()

    /** 通知失败结果：让订阅方把 UiState 切到 Error/AuthExpired。 */
    private val _outcomes = MutableSharedFlow<SyncOutcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<SyncOutcome> = _outcomes.asSharedFlow()

    /**
     * 触发一次完整同步。幂等去抖：[debounceMillis] 内多次调用只跑一次。
     *
     * @return true 表示实际发起了一次同步；false 表示被去抖丢弃。
     */
    fun refreshAsync(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastTriggerAt
        val debounced = last != 0L && (now - last) < debounceMillis
        if (debounced) return false
        lastTriggerAt = now

        inFlight?.cancel()
        inFlight = scope.launch {
            _state.value = RefreshState.Running
            // 顺手启动周期任务：保证 App 启动后 / 异常恢复时周期同步能跑起来
            runCatching { workScheduler.start() }
            val outcome = syncCoordinator.syncOnce()
            when (outcome) {
                is SyncOutcome.Success -> {
                    // 把最新 snapshot 推给订阅者（HomeViewModel 用它更新 UiState）
                    snapshotStore.load()?.let { snap -> _snapshots.tryEmit(snap) }
                    _state.value = RefreshState.Idle
                }
                is SyncOutcome.NoCredential,
                is SyncOutcome.Unknown -> _state.value = RefreshState.Idle
                is SyncOutcome.AuthExpired -> {
                    _outcomes.tryEmit(outcome)
                    _state.value = RefreshState.Failed("登录已失效，请重新绑定账号")
                }
                is SyncOutcome.TransientFailure -> {
                    _outcomes.tryEmit(outcome)
                    _state.value = RefreshState.Failed(outcome.reason)
                }
            }
        }
        return true
    }

    /**
     * 测试钩子：等待当前进行中的同步结束。生产代码不要调用 —— 仅供单测同步点。
     */
    internal suspend fun awaitIdle() {
        inFlight?.join()
    }

    /**
     * 重置去抖窗口与状态机 —— 仅供单测使用，避免 sleep 拖慢测试。
     */
    internal fun resetForTest() {
        lastTriggerAt = 0L
        inFlight?.cancel()
        inFlight = null
        _state.value = RefreshState.Idle
    }

    companion object {
        /** 默认 300ms 去抖窗口；过短对低端机滚动不友好，过长错过快速反馈。 */
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}

/** 刷新状态机 —— AppBar 据此驱动旋转动画与失败提示。 */
sealed interface RefreshState {
    data object Idle : RefreshState
    data object Running : RefreshState
    data class Failed(val reason: String) : RefreshState
}