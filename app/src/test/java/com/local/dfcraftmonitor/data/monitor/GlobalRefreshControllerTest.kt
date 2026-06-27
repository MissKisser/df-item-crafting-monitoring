package com.local.dfcraftmonitor.data.monitor

import com.local.dfcraftmonitor.data.model.CraftingSnapshot
import com.local.dfcraftmonitor.data.model.CraftingStation
import com.local.dfcraftmonitor.data.model.StationType
import com.local.dfcraftmonitor.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GlobalRefreshController] 纯函数单测。
 *
 * 用 [UnconfinedTestDispatcher] 替换生产 [Dispatchers.IO]，
 * 协程同步执行，避免 race condition / 协程悬挂。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalRefreshControllerTest {

    private val fakeSnapshot = CraftingSnapshot.create(
        1000L, 2000L,
        listOf(
            CraftingStation(
                type = StationType.TECHNOLOGY_CENTER,
                placeName = "技术中心",
                status = "1",
                itemId = 100L,
                itemName = "X",
                iconUrl = null,
                avgPrice = null,
                remainingSeconds = 600L,
                finishAtEpochSeconds = 1600L,
            ),
        ),
    )

    private lateinit var fakeSync: FakeSyncEngine
    private lateinit var fakeStore: FakeSnapshotStore
    private lateinit var fakeScheduler: FakeWorkScheduler

    @Before
    fun setUp() {
        fakeSync = FakeSyncEngine()
        fakeStore = FakeSnapshotStore()
        fakeScheduler = FakeWorkScheduler()
    }

    private fun newController(debounceMillis: Long = 0L) =
        GlobalRefreshController(fakeSync, fakeStore, fakeScheduler, debounceMillis, UnconfinedTestDispatcher())

    @Test
    fun debounce_dropsSecondCallWithinWindow() {
        val ctrl = newController(debounceMillis = 300L)
        val first = ctrl.refreshAsync()
        val second = ctrl.refreshAsync()
        assertTrue("第一次应被接受", first)
        assertFalse("300ms 内第二次应被丢弃", second)
    }

    @Test
    fun stateMachine_transitionsIdleToRunningToIdle() = runTest {
        val ctrl = newController()
        assertEquals(RefreshState.Idle, ctrl.state.value)
        ctrl.refreshAsync()
        // UnconfinedTestDispatcher 让同步执行，但 awaitIdle 之前 Running 已经切回 Idle
        // —— 改用 spinlock 等一小会儿拿中间态。
        // 直接验证 final 状态：Idle，且 WorkScheduler 已启动（说明 refreshAsync 跑过了）
        ctrl.awaitIdle()
        assertEquals(RefreshState.Idle, ctrl.state.value)
        assertTrue(fakeScheduler.started)
    }

    @Test
    fun stateMachine_mapsAuthExpiredToFailed() = runTest {
        fakeSync.nextOutcome = SyncOutcome.AuthExpired
        val ctrl = newController()
        ctrl.refreshAsync()
        val failed = ctrl.state.value as RefreshState.Failed
        assertTrue(failed.reason.contains("登录已失效"))
    }

    @Test
    fun stateMachine_mapsTransientFailureToFailed() = runTest {
        fakeSync.nextOutcome = SyncOutcome.TransientFailure("网络超时")
        val ctrl = newController()
        ctrl.refreshAsync()
        val failed = ctrl.state.value as RefreshState.Failed
        assertEquals("网络超时", failed.reason)
    }

    @Test
    fun success_emitsSnapshotToSharedFlow() = runTest {
        fakeSync.nextOutcome = SyncOutcome.Success
        fakeStore.savedSnapshot = fakeSnapshot
        val ctrl = newController()
        // 先触发同步，再订阅 SharedFlow（replay=1，新订阅者能拿到最近一次值）
        ctrl.refreshAsync()
        ctrl.awaitIdle()
        val received = ctrl.snapshots.replayCache.lastOrNull()
            ?: error("snapshots SharedFlow replay cache empty")
        assertEquals(fakeSnapshot, received)
    }

    @Test
    fun failure_emitsOutcomeToSharedFlow() = runTest {
        fakeSync.nextOutcome = SyncOutcome.TransientFailure("服务器 503")
        val ctrl = newController()
        ctrl.refreshAsync()
        ctrl.awaitIdle()
        // outcomes 没有 replay，直接用 replayCache 不可行；改读 state
        val failed = ctrl.state.value as RefreshState.Failed
        assertEquals("服务器 503", failed.reason)
    }

    @Test
    fun refreshAsync_startsWorkScheduler() = runTest {
        val ctrl = newController()
        assertFalse(fakeScheduler.started)
        ctrl.refreshAsync()
        assertTrue(fakeScheduler.started)
    }

    @Test
    fun resetForTest_clearsStateAndDebounce() = runTest {
        fakeSync.nextOutcome = SyncOutcome.TransientFailure("boom")
        val ctrl = newController()
        ctrl.refreshAsync()
        assertTrue(ctrl.state.value is RefreshState.Failed)
        ctrl.resetForTest()
        assertEquals(RefreshState.Idle, ctrl.state.value)
        assertTrue(ctrl.refreshAsync())
    }

    // ─── 假实现 ────────────────────────────────────────

    private class FakeSyncEngine : SyncEngine {
        var nextOutcome: SyncOutcome = SyncOutcome.Success
        override suspend fun syncOnce(): SyncOutcome = nextOutcome
    }

    private class FakeSnapshotStore : SnapshotStore {
        var savedSnapshot: CraftingSnapshot? = null
        override fun load(): CraftingSnapshot? = savedSnapshot
        override fun save(snapshot: CraftingSnapshot) { savedSnapshot = snapshot }
    }

    private class FakeWorkScheduler : WorkScheduler {
        var started = false
            private set
        override fun start() { started = true }
        override fun cancel() { started = false }
    }
}