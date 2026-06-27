package com.local.dfcraftmonitor.data.monitor

import com.local.dfcraftmonitor.data.model.CraftingSnapshot

/**
 * 快照存储抽象。生产实现 [SnapshotCache] 用 DataStore 持久化。
 *
 * 单测用假实现替换，避免依赖 Android Context / DataStore。
 */
interface SnapshotStore {
    fun save(snapshot: CraftingSnapshot)
    fun load(): CraftingSnapshot?
}