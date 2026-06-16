package com.local.dfcraftmonitor.data

import com.local.dfcraftmonitor.data.monitor.SnapshotCache
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.work.WorkScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用数据清除器，统一入口。
 *
 * 清除顺序：
 * 1. 停 WorkManager 调度（先停，防止清数据后还在跑）
 * 2. 清 SessionHolder（登录凭据）
 * 3. 清 SnapshotCache（快照缓存）
 * 4. 清 UserPreferencesRepository（用户偏好）
 *
 * spec M3：设置页「清除数据」+ 退出登录时调用。
 */
@Singleton
class AppDataCleaner @Inject constructor(
    private val workScheduler: WorkScheduler,
    private val sessionHolder: SessionHolder,
    private val snapshotCache: SnapshotCache,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend fun clearAll() {
        workScheduler.cancel()
        sessionHolder.clear()
        snapshotCache.clear()
        userPreferencesRepository.clear()
    }
}
