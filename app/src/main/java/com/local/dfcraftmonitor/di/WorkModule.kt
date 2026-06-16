package com.local.dfcraftmonitor.di

import com.local.dfcraftmonitor.work.DefaultWorkScheduler
import com.local.dfcraftmonitor.work.WorkScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WorkScheduler 接口 → DefaultWorkScheduler 实现的绑定。
 * Hilt 编译期检查要求显式 @Binds，否则 WorkScheduler 无法被注入。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    @Binds
    @Singleton
    abstract fun bindWorkScheduler(impl: DefaultWorkScheduler): WorkScheduler
}
