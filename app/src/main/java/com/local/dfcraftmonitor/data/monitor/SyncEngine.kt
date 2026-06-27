package com.local.dfcraftmonitor.data.monitor

/**
 * 同步引擎抽象。生产实现为 [SyncCoordinator]，测试用假实现替换。
 */
interface SyncEngine {
    suspend fun syncOnce(): SyncOutcome
}
