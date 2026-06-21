package com.local.dfcraftmonitor.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import com.local.dfcraftmonitor.notify.CompletionNotifier
import com.local.dfcraftmonitor.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 精确计时 Worker：到点后发完成通知 + 刷新 Widget 显示"已完成"状态。
 */
@HiltWorker
class CompletionTimerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val completionNotifier: CompletionNotifier,
    private val widgetUpdater: WidgetUpdater,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val placeName = inputData.getString(KEY_PLACE_NAME) ?: "工位"
        val itemName = inputData.getString(KEY_ITEM_NAME)
        val itemId = inputData.getLong(KEY_ITEM_ID, -1L)
        val finishAt = inputData.getLong(KEY_FINISH_AT, 0L)

        val station = CraftingStation(
            type = StationType.TECHNOLOGY_CENTER,
            placeName = placeName,
            status = "0",
            itemId = if (itemId >= 0) itemId else null,
            itemName = itemName,
            iconUrl = null,
            avgPrice = null,
            remainingSeconds = 0L,
            finishAtEpochSeconds = if (finishAt > 0) finishAt else null,
        )

        completionNotifier.notifyCompleted(station)
        widgetUpdater.updateAll()
        return Result.success()
    }

    companion object {
        const val KEY_PLACE_NAME = "place_name"
        const val KEY_ITEM_NAME = "item_name"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_FINISH_AT = "finish_at"
    }
}
