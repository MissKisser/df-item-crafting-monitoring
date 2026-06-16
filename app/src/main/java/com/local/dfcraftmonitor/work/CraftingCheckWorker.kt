package com.local.dfcraftmonitor.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.local.dfcraftmonitor.data.monitor.SyncCoordinator
import com.local.dfcraftmonitor.data.monitor.SyncOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 周期同步 Worker（spec M2 核心）。
 *
 * 由 WorkScheduler 调起，周期 15 分钟。每次执行：
 *  1. 调 [SyncCoordinator.syncOnce]
 *  2. SyncOutcome 映射到 Worker Result：
 *     - Success / NoCredential → Result.success()
 *     - AuthExpired → Result.failure()（不再重试，等待用户重新登录）
 *     - TransientFailure → Result.retry()（让 WorkManager 退避）
 */
@HiltWorker
class CraftingCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncCoordinator: SyncCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = syncCoordinator.syncOnce()
        return when (outcome) {
            is SyncOutcome.Success -> Result.success()
            is SyncOutcome.NoCredential -> {
                // 无凭据：不要 retry 浪费电，等用户重新登录再 start 周期
                Result.success()
            }
            is SyncOutcome.AuthExpired -> {
                // 凭据失效：不要 retry，等用户重新登录
                Result.failure()
            }
            is SyncOutcome.TransientFailure -> {
                // 让 WorkManager 退避重试（setBackoffCriteria 已配置）
                Result.retry()
            }
            is SyncOutcome.Unknown -> Result.success()
        }
    }
}
