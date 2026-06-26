package com.local.dfcraftmonitor.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 精确完成计时调度器。
 *
 * 当工位剩余时间 < 15 分钟（轮询间隔）时，注册一个 OneTimeWorkRequest，
 * delay 到 finishAtEpochSeconds 时刻执行，直接发完成通知——不再等下一次轮询。
 *
 * 每个工位用唯一 work name（itemId），REPLACE 策略保证每次同步后重新校准。
 *
 * 关键修复（首次同步漏通知 bug）：
 * 之前的实现里，若 `finishAt <= now`（工位首次同步时就已完成 / 系统时钟回退 /
 * Worker 被 Doze 拖延导致触发晚于 finishAt），scheduleOne 直接 return，
 * 这类工位永远拿不到主动通知。现在改为：delay <= 0 时以 delay=0 立即入队，
 * 由 Worker 即刻发通知——不再静默丢弃。
 */
@Singleton
class CompletionTimerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleTimers(snapshot: CraftingSnapshot) {
        val nowSeconds = System.currentTimeMillis() / 1000
        selectSchedulableStations(snapshot, nowSeconds).forEach { scheduled ->
            scheduleOne(scheduled.station, scheduled.delaySeconds)
        }
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    private fun scheduleOne(station: CraftingStation, delaySeconds: Long) {
        val finishAt = station.finishAtEpochSeconds ?: return
        val itemId = station.itemId ?: return
        val workName = "$WORK_NAME_PREFIX$itemId"

        val data = Data.Builder()
            .putString(CompletionTimerWorker.KEY_PLACE_NAME, station.placeName)
            .putString(CompletionTimerWorker.KEY_ITEM_NAME, station.itemName)
            .putLong(CompletionTimerWorker.KEY_ITEM_ID, itemId)
            .putLong(CompletionTimerWorker.KEY_FINISH_AT, finishAt)
            .build()

        // delaySeconds 来自 selectSchedulableStations，已保证 >= 0；
        // 原值为负（已过期）时被钳到 0，让 Worker 立即触发。
        val safeDelay = delaySeconds.coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<CompletionTimerWorker>()
            .setInitialDelay(safeDelay, TimeUnit.SECONDS)
            .setInputData(data)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private const val THRESHOLD_SECONDS = 900L
        private const val WORK_TAG = "df_completion_timer"
        private const val WORK_NAME_PREFIX = "df_completion_timer_"

        /**
         * 纯函数：从快照中筛选出需要调度的工位及其延迟。
         *
         * 规则：
         *  1. remainingSeconds > 0（在做）且 <= THRESHOLD_SECONDS（进入精确计时窗口）
         *  2. 有 finishAtEpochSeconds 和 itemId（缺一则无法调度）
         *  3. delaySeconds = finishAt - now；若已过期（<=0）钳到 0 —— **立即触发**，
         *     不再像旧版那样静默丢弃。
         *
         * 抽成纯函数是为了能脱离 WorkManager / Android Context 做单测。
         */
        fun selectSchedulableStations(
            snapshot: CraftingSnapshot,
            nowSeconds: Long,
        ): List<ScheduledStation> = snapshot.stations.mapNotNull { station ->
            val remaining = station.remainingSeconds ?: return@mapNotNull null
            if (remaining <= 0 || remaining > THRESHOLD_SECONDS) return@mapNotNull null
            val finishAt = station.finishAtEpochSeconds ?: return@mapNotNull null
            if (station.itemId == null) return@mapNotNull null
            val delaySeconds = finishAt - nowSeconds
            ScheduledStation(station, delaySeconds)
        }
    }
}

/**
 * 调度决策结果。[delaySeconds] 可能为负（已过期），由调用方决定如何处理
 * （生产代码会钳到 0 立即入队，单测断言该字段本身）。
 */
data class ScheduledStation(
    val station: CraftingStation,
    val delaySeconds: Long,
)
