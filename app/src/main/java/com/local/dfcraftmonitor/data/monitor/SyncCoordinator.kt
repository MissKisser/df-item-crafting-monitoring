package com.local.dfcraftmonitor.data.monitor

import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import com.local.dfcraftmonitor.data.repository.CraftingRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.widget.WidgetUpdater
import com.local.dfcraftmonitor.work.CompletionTimerScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后台同步编排器。
 *
 * 流程（syncOnce）：
 *  1. 拿当前 SessionHolder 里的 AmsCredential；无凭据 → AuthNotifier + 返 NoCredential
 *  2. 调 CraftingRepository.fetchCrafting
 *  3. 失败：按异常类型分派
 *  4. 成功：diff 找"未完成→完成"工位 → 通知 → 写快照 → 更新 WidgetCache → 刷 Widget
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val craftingRepository: CraftingRepository,
    private val snapshotCache: SnapshotCache,
    private val completionNotifier: com.local.dfcraftmonitor.notify.CompletionNotifier,
    private val monitoringNotifier: com.local.dfcraftmonitor.notify.MonitoringNotifier,
    private val authNotifier: com.local.dfcraftmonitor.notify.AuthNotifier,
    private val widgetUpdater: WidgetUpdater,
    private val widgetCache: WidgetCache,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val completionTimerScheduler: CompletionTimerScheduler,
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
        return SyncOutcome.Unknown
    }

    private suspend fun handleSuccess(newSnapshot: CraftingSnapshot) {
        val oldSnapshot = snapshotCache.load()
        val notificationEnabled = userPreferencesRepository.userPreferences.first()
            .craftingNotificationEnabled

        if (notificationEnabled) {
            if (oldSnapshot != null) {
                val completed = SyncDiff.completed(oldSnapshot, newSnapshot)
                completed.forEach { station ->
                    completionNotifier.notifyCompleted(station)
                }
            }
            monitoringNotifier.notifyMonitoring(newSnapshot)
            completionTimerScheduler.scheduleTimers(newSnapshot)
        }
        snapshotCache.save(newSnapshot)

        val accountId = sessionHolder.getCurrentEntry()?.accountId
        if (accountId != null) {
            widgetCache.updateFromSync(accountId, newSnapshot)
        }
        widgetUpdater.updateAll()
    }

    private fun handleFailure(e: Throwable): SyncOutcome = when (e) {
        is AmsCraftingParser.AuthExpiredException -> {
            authNotifier.notifyAuthExpired(e.message ?: "登录已失效")
            SyncOutcome.AuthExpired
        }
        else -> {
            SyncOutcome.TransientFailure(e.message ?: "未知错误")
        }
    }

    @Suppress("unused")
    internal fun diffCompleted(
        old: CraftingSnapshot,
        new: CraftingSnapshot,
    ): List<CraftingStation> = SyncDiff.completed(old, new)
}

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
