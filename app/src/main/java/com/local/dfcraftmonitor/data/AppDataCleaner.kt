package com.local.dfcraftmonitor.data

import com.local.dfcraftmonitor.data.monitor.SnapshotCache
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import com.local.dfcraftmonitor.ui.login.SessionHolder
import com.local.dfcraftmonitor.widget.WidgetUpdater
import com.local.dfcraftmonitor.work.CompletionTimerScheduler
import com.local.dfcraftmonitor.work.WorkScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用数据清除器，统一入口。
 *
 * - [clearAll]：清除所有本地数据（全部账号 + 缓存 + 偏好）
 * - [logoutCurrent]：退出当前账号，切换到下一个（只清会话不清缓存）
 */
@Singleton
class AppDataCleaner @Inject constructor(
    private val workScheduler: WorkScheduler,
    private val sessionHolder: SessionHolder,
    private val webSessionCleaner: WebSessionCleaner,
    private val snapshotCache: SnapshotCache,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val widgetCache: WidgetCache,
    private val widgetUpdater: WidgetUpdater,
    private val completionTimerScheduler: CompletionTimerScheduler,
) {
    /** 清除所有本地数据（全部账号 + 缓存 + 偏好 + Widget 缓存）。 */
    suspend fun clearAll() {
        workScheduler.cancel()
        completionTimerScheduler.cancelAll()
        sessionHolder.clearAll()
        webSessionCleaner.clear()
        snapshotCache.clear()
        widgetCache.clearAll()
        userPreferencesRepository.clear()
    }

    /**
     * 退出当前账号：从列表移除，自动切换到剩余第一个账号，并清理该账号的所有缓存。
     *
     * 清理范围（仅限被退出的账号）：
     * - 该账号在 [WidgetCache] 的 payload（按 accountId 定向删除）
     * - 工位快照 [SnapshotCache]（只存当前账号一份，退出即失效）
     * - WebView 登录态（QQ/微信 Cookie），保证下次「新增账号」扫码是干净环境，
     *   不会复用被退出账号的登录态自动登录
     * - 取消该账号的同步定时任务；若有剩余账号则按新账号重建
     *
     * @return 切换后的新账号 entry，或 null（已无账号）
     */
    suspend fun logoutCurrent(): com.local.dfcraftmonitor.data.model.AccountEntry? {
        // 先记下被退出的账号，用于定向清理其缓存
        val loggingOutAccountId = sessionHolder.getCurrentEntry()?.accountId
        val nextAccount = sessionHolder.logoutCurrent()

        // 1. 清理被退出账号的缓存（无论是否还有剩余账号）
        if (loggingOutAccountId != null) {
            widgetCache.clear(loggingOutAccountId)
        }
        snapshotCache.clear()
        webSessionCleaner.clear()

        // 2. 同步任务：有剩余账号则切到新账号并重启，无则取消
        if (nextAccount != null) {
            widgetCache.setCurrentAccountId(nextAccount.accountId)
            workScheduler.start()
        } else {
            // 无账号：清空全部 widget 缓存（含镜像字段），取消定时任务
            workScheduler.cancel()
            completionTimerScheduler.cancelAll()
            widgetCache.clearAll()
        }
        widgetUpdater.updateAll()
        return nextAccount
    }
}
