package com.local.dfcraftmonitor.data.monitor

import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import com.local.dfcraftmonitor.data.repository.CraftingRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.widget.WidgetRefresher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后台同步编排器（spec M2 主干）。
 *
 * 流程（syncOnce）：
 *  1. 拿当前 SessionHolder 里的 AmsCredential；无凭据 → AuthNotifier + 返 NoCredential
 *  2. 调 CraftingRepository.fetchCrafting
 *  3. 失败：按异常类型分派（AuthExpired → AuthNotifier + 取消调度；其他 → 退避）
 *  4. 成功：从 SnapshotCache 取旧快照，与新快照 diff 找"未完成→完成"工位
 *  5. 触发 CompletionNotifier（按 stationId 各自一条）+ MonitoringNotifier（常驻更新）
 *  6. 写新快照到 SnapshotCache
 *
 * 返回 [SyncOutcome] 告诉调用方（Worker）怎么处理这次执行。
 *
 * 设计：业务规则集中在这里，未来要加"接近完成时提高关注度"等策略也好改。
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val craftingRepository: CraftingRepository,
    private val snapshotCache: SnapshotCache,
    private val completionNotifier: com.local.dfcraftmonitor.notify.CompletionNotifier,
    private val monitoringNotifier: com.local.dfcraftmonitor.notify.MonitoringNotifier,
    private val authNotifier: com.local.dfcraftmonitor.notify.AuthNotifier,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend fun syncOnce(): SyncOutcome {
        val credential = sessionHolder.get()
        if (credential == null) {
            authNotifier.notifyLoggedOut()
            return SyncOutcome.NoCredential
        }

        val result = craftingRepository.fetchCrafting(credential)
        result.onSuccess { newSnapshot ->
            handleSuccess(newSnapshot)
            return SyncOutcome.Success
        }
        result.onFailure { e ->
            return handleFailure(e)
        }
        // runCatching 保证 onSuccess/onFailure 至少一个触发，编译器也认这个不可达
        return SyncOutcome.Unknown
    }

    private suspend fun handleSuccess(newSnapshot: CraftingSnapshot) {
        val oldSnapshot = snapshotCache.load()
        if (oldSnapshot != null) {
            val completed = SyncDiff.completed(oldSnapshot, newSnapshot)
            completed.forEach { station ->
                completionNotifier.notifyCompleted(station)
            }
        }
        monitoringNotifier.notifyMonitoring(newSnapshot)
        snapshotCache.save(newSnapshot)
        // 刷新桌面卡片（spec 8.1：同步成功后更新 widget）
        widgetRefresher.refresh()
    }

    private fun handleFailure(e: Throwable): SyncOutcome = when (e) {
        is AmsCraftingParser.AuthExpiredException -> {
            // 凭据失效：触发通知 + 停掉周期 Worker
            authNotifier.notifyAuthExpired(e.message ?: "登录已失效")
            SyncOutcome.AuthExpired
        }
        else -> {
            // 网络/解析/其他：让 WorkManager 退避重试
            SyncOutcome.TransientFailure(e.message ?: "未知错误")
        }
    }

    /**
     * diff"未完成→完成"工位。委托给 [SyncDiff] 纯函数，让纯逻辑可单测。
     */
    @Suppress("unused")  // 保留给未来扩展（如通知完成百分比进度）
    internal fun diffCompleted(
        old: CraftingSnapshot,
        new: CraftingSnapshot,
    ): List<CraftingStation> = SyncDiff.completed(old, new)
}

/**
 * 快照 diff 纯函数（spec 7.4 "未完成→完成"识别）。
 *
 * 抽出 SyncCoordinator 的类是为了让单测不依赖 Hilt 注入。
 */
object SyncDiff {
    fun completed(old: CraftingSnapshot, new: CraftingSnapshot): List<CraftingStation> {
        val oldByKey = old.stations.associateBy { it.stableKey() }
        return new.stations.filter { newStation ->
            val oldStation = oldByKey[newStation.stableKey()] ?: return@filter false
            wasInProgress(oldStation) && isCompleted(newStation)
        }
    }

    private fun wasInProgress(station: CraftingStation): Boolean {
        val remaining = station.remainingSeconds ?: return false
        return remaining > 0
    }

    private fun isCompleted(station: CraftingStation): Boolean {
        val remaining = station.remainingSeconds ?: return false
        return remaining <= 0
    }

    private fun CraftingStation.stableKey(): String =
        itemId?.toString() ?: "${type.name}:$placeName"
}

sealed interface SyncOutcome {
    data object Success : SyncOutcome
    data object NoCredential : SyncOutcome
    data object AuthExpired : SyncOutcome
    data class TransientFailure(val reason: String) : SyncOutcome
    data object Unknown : SyncOutcome
}
