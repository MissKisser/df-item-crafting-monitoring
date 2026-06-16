package com.local.dfcraftmonitor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.local.dfcraftmonitor.notify.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 依赖图。
 *
 * 启动时：
 * 1. 注册 3 个通知 channel（spec 7.4）
 * 2. 实现 Configuration.Provider 让 WorkManager 使用 HiltWorkerFactory，
 *    这样 @HiltWorker 标注的 Worker 能被注入依赖。
 */
@HiltAndroidApp
class DfApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)
    }
}
