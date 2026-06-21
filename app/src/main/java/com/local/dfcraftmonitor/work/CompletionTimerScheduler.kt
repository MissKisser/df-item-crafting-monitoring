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
 */
@Singleton
class CompletionTimerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleTimers(snapshot: CraftingSnapshot) {
        val nowSeconds = System.currentTimeMillis() / 1000
        snapshot.stations
            .filter { station ->
                val remaining = station.remainingSeconds ?: return@filter false
                remaining > 0 && remaining <= THRESHOLD_SECONDS
            }
            .forEach { station ->
                scheduleOne(station, nowSeconds)
            }
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    private fun scheduleOne(station: CraftingStation, nowSeconds: Long) {
        val finishAt = station.finishAtEpochSeconds ?: return
        val delaySeconds = finishAt - nowSeconds
        if (delaySeconds <= 0L) return

        val itemId = station.itemId ?: return
        val workName = "$WORK_NAME_PREFIX$itemId"

        val data = Data.Builder()
            .putString(CompletionTimerWorker.KEY_PLACE_NAME, station.placeName)
            .putString(CompletionTimerWorker.KEY_ITEM_NAME, station.itemName)
            .putLong(CompletionTimerWorker.KEY_ITEM_ID, itemId)
            .putLong(CompletionTimerWorker.KEY_FINISH_AT, finishAt)
            .build()

        val request = OneTimeWorkRequestBuilder<CompletionTimerWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
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
    }
}
