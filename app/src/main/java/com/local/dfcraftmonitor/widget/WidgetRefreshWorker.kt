package com.local.dfcraftmonitor.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.local.dfcraftmonitor.data.monitor.SyncCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Widget 刷新触发的一次性同步 Worker。
 *
 * 只调 [SyncCoordinator.syncOnce]：成功后其内部 handleSuccess 会刷新 Widget；
 * 失败时不重建（缓存未变，[WidgetRemoteViewsApplier] 的变化检测会跳过）。
 *
 * 不再先 updateAll() 再 syncOnce()——那会让一次刷新按钮触发两次重建（① 本类 updateAll，
 * ② handleSuccess 的 updateAll），是卡片闪烁的来源之一。
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncCoordinator: SyncCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        syncCoordinator.syncOnce()
        return Result.success()
    }
}
