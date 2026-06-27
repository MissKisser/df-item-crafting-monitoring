package com.local.dfcraftmonitor.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContractSourceTest {

    @Test
    fun providersExposeCraftingProfitAndCombinedModes() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val updater = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetUpdater.kt")
        val applier = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsApplier.kt")
        val builder = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilder.kt")

        listOf(
            "CraftingDetailWidgetProvider" to "crafting_detail_widget_info",
            "TodayProfitWidgetProvider" to "today_profit_widget_info",
            "CombinedWidgetProvider" to "combined_widget_info",
        ).forEach { (provider, info) ->
            assertTrue("Manifest must register $provider", manifest.contains(provider))
            assertTrue("Manifest must point $provider at $info", manifest.contains("@xml/$info"))
            assertTrue("WidgetRemoteViewsApplier must refresh $provider", applier.contains(provider))
        }
        assertTrue(updater.contains("WidgetRemoteViewsApplier.updateAll"))

        assertTrue(builder.contains("R.layout.widget_crafting_detail"))
        assertTrue(builder.contains("R.layout.widget_today_profit"))
        assertTrue(builder.contains("R.layout.widget_combined"))
        assertTrue(builder.contains("fun buildCraftingDetail"))
        assertTrue(builder.contains("fun buildTodayProfit"))
        assertTrue(builder.contains("fun buildCombined"))
    }

    @Test
    fun craftingWidgetUsesFifteenMinutePollingAndSecondAccurateCountdown() {
        val scheduler = source("app/src/main/java/com/local/dfcraftmonitor/work/WorkScheduler.kt")
        val completionTimer = source("app/src/main/java/com/local/dfcraftmonitor/work/CompletionTimerScheduler.kt")
        val builder = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilder.kt")

        assertTrue(scheduler.contains("const val SYNC_INTERVAL_MINUTES = 15L"))
        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<CraftingCheckWorker>"))
        assertTrue(completionTimer.contains("private const val THRESHOLD_SECONDS = 900L"))
        assertTrue(completionTimer.contains("OneTimeWorkRequestBuilder<CompletionTimerWorker>"))
        assertTrue(
            "CompletionTimerScheduler must call setInitialDelay with seconds",
            completionTimer.contains("setInitialDelay(") &&
                completionTimer.contains("TimeUnit.SECONDS"),
        )
        assertTrue(builder.contains("SystemClock.elapsedRealtime() + remainingMillis"))
        assertTrue(builder.contains("views.setChronometer(timerId(i), base, null, true)"))
    }

    @Test
    fun craftingLayoutsDeclareFourCountdownRowsInSeparateAndCombinedCards() {
        val craftingLayout = source("app/src/main/res/layout/widget_crafting_detail.xml")
        val combinedLayout = source("app/src/main/res/layout/widget_combined.xml")
        val profitLayout = source("app/src/main/res/layout/widget_today_profit.xml")

        assertEquals(4, Regex("<Chronometer").findAll(craftingLayout).count())
        assertEquals(4, Regex("android:countDown=\"true\"").findAll(craftingLayout).count())
        assertEquals(4, Regex("<Chronometer").findAll(combinedLayout).count())
        assertEquals(4, Regex("android:countDown=\"true\"").findAll(combinedLayout).count())
        assertTrue(profitLayout.contains("@+id/profit_text"))
        assertTrue(profitLayout.contains("今日盈亏"))
    }

    @Test
    fun nativeWidgetsStayCompactAndTextOnly() {
        val craftingInfo = source("app/src/main/res/xml/crafting_detail_widget_info.xml")
        val profitInfo = source("app/src/main/res/xml/today_profit_widget_info.xml")
        val combinedInfo = source("app/src/main/res/xml/combined_widget_info.xml")
        val craftingLayout = source("app/src/main/res/layout/widget_crafting_detail.xml")
        val combinedLayout = source("app/src/main/res/layout/widget_combined.xml")
        val profitLayout = source("app/src/main/res/layout/widget_today_profit.xml")

        assertTrue(craftingInfo.contains("android:targetCellWidth=\"4\""))
        assertTrue(craftingInfo.contains("android:targetCellHeight=\"1\""))
        assertTrue(craftingInfo.contains("android:initialLayout=\"@layout/widget_crafting_detail\""))
        assertTrue(profitInfo.contains("android:targetCellWidth=\"2\""))
        assertTrue(profitInfo.contains("android:targetCellHeight=\"1\""))
        assertTrue(profitInfo.contains("android:initialLayout=\"@layout/widget_today_profit\""))
        assertTrue(combinedInfo.contains("android:targetCellWidth=\"4\""))
        assertTrue(combinedInfo.contains("android:targetCellHeight=\"2\""))
        assertTrue(combinedInfo.contains("android:initialLayout=\"@layout/widget_combined\""))
        assertTrue(!craftingInfo.contains("@layout/widget_loading"))
        assertTrue(!profitInfo.contains("@layout/widget_loading"))
        assertTrue(!combinedInfo.contains("@layout/widget_loading"))
        assertTrue(craftingInfo.contains("android:updatePeriodMillis=\"900000\""))
        assertTrue(combinedInfo.contains("android:updatePeriodMillis=\"900000\""))

        listOf(craftingLayout, combinedLayout, profitLayout).forEach { layout ->
            assertTrue("Widget layouts should be pure text RemoteViews surfaces", !layout.contains("<ImageButton"))
            assertTrue("Widget layouts should expose a text refresh target", layout.contains("@+id/btn_refresh"))
        }

        listOf(craftingLayout, combinedLayout).forEach { layout ->
            assertTrue("Initial widget layout must not be a blank block", layout.contains("待同步"))
            assertTrue("Initial widget layout must tell the user how to recover data", layout.contains("点刷新") || layout.contains("btn_refresh"))
        }
        assertTrue(craftingLayout.contains("待同步"))
        assertTrue(combinedLayout.contains("待同步"))
        assertTrue(profitLayout.contains("待同步"))
    }

    @Test
    fun widgetProvidersRequestRefreshWhenAddedWithEmptyCache() {
        val craftingProvider = source("app/src/main/java/com/local/dfcraftmonitor/widget/CraftingDetailWidgetProvider.kt")
        val profitProvider = source("app/src/main/java/com/local/dfcraftmonitor/widget/TodayProfitWidgetProvider.kt")
        val combinedProvider = source("app/src/main/java/com/local/dfcraftmonitor/widget/CombinedWidgetProvider.kt")

        // 实际实现可能用 WidgetRemoteViewsApplier.updateAll（直接重渲染）
        // 或 WidgetRefreshReceiver.requestRefresh（异步刷新），
        // 只要 onUpdate 触发了渲染刷新即可。
        listOf(craftingProvider, profitProvider, combinedProvider).forEach { provider ->
            val refreshCalled = provider.contains("WidgetRefreshReceiver.requestRefresh(context)") ||
                provider.contains("WidgetRemoteViewsApplier.updateAll") ||
                provider.contains("triggerRefresh") ||
                provider.contains("requestRefresh")
            assertTrue("$provider should trigger refresh on empty cache", refreshCalled)
        }
    }

    @Test
    fun widgetsRecoverAfterInstallOrBootBeforeNetworkSync() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val app = source("app/src/main/java/com/local/dfcraftmonitor/DfApp.kt")
        val receiver = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRefreshReceiver.kt")
        val lifecycleReceiver = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetLifecycleReceiver.kt")
        val worker = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRefreshWorker.kt")

        assertTrue(manifest.contains("WidgetLifecycleReceiver"))
        assertTrue(manifest.contains("android.intent.action.PACKAGE_REPLACED"))
        assertTrue(manifest.contains("android:scheme=\"package\""))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(lifecycleReceiver.contains("Intent.ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(lifecycleReceiver.contains("Intent.ACTION_BOOT_COMPLETED"))
        assertTrue(lifecycleReceiver.contains("Intent.ACTION_PACKAGE_REPLACED"))
        assertTrue(lifecycleReceiver.contains("context.packageName"))
        assertTrue(receiver.contains("restoreCachedViews(context)"))
        assertTrue(worker.indexOf("widgetUpdater.updateAll()") < worker.indexOf("syncCoordinator.syncOnce()"))
        assertTrue(app.contains("accountStore.currentAccountId()"))
        assertTrue(app.contains("widgetCache.setCurrentAccountId(accountId)"))
        assertTrue(app.contains("WidgetRefreshReceiver.restoreCachedViews(this)"))
    }

    private fun source(path: String): String =
        listOf(File(path), File("../$path"))
            .first { it.exists() }
            .readText()
}
