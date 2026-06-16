package com.local.dfcraftmonitor.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 周期同步调度接口（spec M2 调度层）。
 *
 * 单一入口：start() / cancel()，避免 Worker 调度逻辑散落在 UI 各个 ViewModel。
 */
interface WorkScheduler {
    /** 启动 15 分钟周期同步；幂等可重入（KEEP 策略）。 */
    fun start()

    /** 停止周期同步（如用户退出登录 / 凭据失效）。 */
    fun cancel()
}

/**
 * 默认实现：用 WorkManager 唯一周期任务（KEEP 策略：已存在不替换）。
 *
 * 退避：指数退避，初始 30 秒（spec 9.3 "网络失败 短期重试随后指数退避"）。
 * 约束：网络可用、电量不低（spec 9.1 Doze/App Standby、低电量约束）。
 */
@Singleton
class DefaultWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkScheduler {

    override fun start() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<CraftingCheckWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_INITIAL_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        /** spec 7.3 "约 15 分钟保守同步"。 */
        const val SYNC_INTERVAL_MINUTES = 15L
        const val BACKOFF_INITIAL_SECONDS = 30L
        const val WORK_NAME = "df_crafting_sync"
    }
}
