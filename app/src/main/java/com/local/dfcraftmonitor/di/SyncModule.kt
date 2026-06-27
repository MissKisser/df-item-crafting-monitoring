package com.local.dfcraftmonitor.di

import com.local.dfcraftmonitor.data.monitor.SnapshotCache
import com.local.dfcraftmonitor.data.monitor.SnapshotStore
import com.local.dfcraftmonitor.data.monitor.SyncCoordinator
import com.local.dfcraftmonitor.data.monitor.SyncEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 将同步相关抽象绑定到生产实现：
 *  - [SyncEngine] → [SyncCoordinator]（mock 10 个构造参数的类不好搞）
 *  - [SnapshotStore] → [SnapshotCache]（避免单测依赖 Android Context）
 *
 * 见 [com.local.dfcraftmonitor.data.monitor.GlobalRefreshController]。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: SyncCoordinator): SyncEngine

    @Binds
    @Singleton
    abstract fun bindSnapshotStore(impl: SnapshotCache): SnapshotStore
}