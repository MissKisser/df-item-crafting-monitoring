package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.local.dfcraftmonitor.data.monitor.WidgetPayload

/**
 * 把 RemoteViews 应用到桌面 Widget 的统一执行器。
 *
 * ## 无感刷新（变化检测）
 *
 * 卡片"一直在刷新/闪烁"的根因是：一次数据更新往往串出 3~4 次全量 `updateAppWidget`
 * （系统 updatePeriodMillis tick → onUpdate 的 updateAppWidget + requestRefresh →
 * WidgetRefreshWorker 的 updateAll + syncOnce → handleSuccess 的 updateAll），
 * 而每次全量替换都会重置 `Chronometer` 的 base，导致倒计时数字"跳一下"+ 整卡闪烁。
 *
 * `Chronometer` 本身由 launcher 每秒自动递减——只要不再反复整张重建，数字就会自己平滑跳动。
 * 因此本类在每次 updateAll 前计算 payload 的"显示签名"，与上次相同时直接跳过 `updateAppWidget`，
 * 避免无意义的重建与 Chronometer 重置。
 *
 * 签名纳入：账号名、今日盈亏（值+文本）、每个工位的显示态（placeName/itemName/finishAt 以及
 * "是否已到完成时刻"）——保证工位到点翻转成"已完成"时签名变化、正常重建切换 timer→状态。
 * `force = true` 时绕过签名检查（用于手动刷新按钮的即时反馈）。
 */
object WidgetRemoteViewsApplier {

    /** 进程级最近一次成功渲染的签名。进程重启后为 null → 首次必重建。 */
    @Volatile
    private var lastSignature: String? = null

    fun updateAll(context: Context, payload: WidgetPayload?) {
        updateAll(context, payload, force = false)
    }

    /**
     * @param force true 时绕过变化检测，强制重建（如手动点刷新按钮的即时反馈）。
     */
    fun updateAll(context: Context, payload: WidgetPayload?, force: Boolean) {
        if (!force) {
            val signature = computeSignature(payload)
            if (signature == lastSignature) {
                // 数据未变：跳过全量重建，Chronometer 继续由系统自行递减（零闪烁）。
                return
            }
            lastSignature = signature
        }

        val manager = AppWidgetManager.getInstance(context)
        updateWidgetsOfClass(manager, context, CraftingDetailWidgetProvider::class.java, payload)
        updateWidgetsOfClass(manager, context, TodayProfitWidgetProvider::class.java, payload)
        updateWidgetsOfClass(manager, context, CombinedWidgetProvider::class.java, payload)
    }

    private fun computeSignature(payload: WidgetPayload?): String {
        return computeSignatureInternal(payload)
    }

    /**
     * 计算影响"可见显示"的签名。只有这些字段变化时才需要重建卡片。
     * 注意：工位的倒计时秒数（remainingSeconds）刻意不入签名——倒计时的实时变化
     * 由 Chronometer 自身每秒递减呈现，不应触发重建。
     * 取而代之纳入"是否已到完成时刻"，让工位在 finishAt 时点正常翻转成"已完成"。
     */
    internal fun computeSignatureInternal(payload: WidgetPayload?): String {
        if (payload == null) return "<null>"
        val nowSeconds = System.currentTimeMillis() / 1000
        val stationsSig = payload.stations.joinToString("|") { station ->
            val completed = station.finishAtEpochSeconds != null &&
                station.finishAtEpochSeconds <= nowSeconds
            "${station.placeName}#${station.itemName}#${station.finishAtEpochSeconds}#$completed"
        }
        return buildString {
            append(payload.nickname).append('/')
            append(payload.areaName).append('/')
            append(payload.todayProfitValue).append('/')
            append(payload.todayProfitText).append('/')
            append(stationsSig)
        }
    }

    private fun updateWidgetsOfClass(
        manager: AppWidgetManager,
        context: Context,
        providerClass: Class<out android.appwidget.AppWidgetProvider>,
        payload: WidgetPayload?,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = when (providerClass) {
                CraftingDetailWidgetProvider::class.java ->
                    WidgetRemoteViewsBuilder.buildCraftingDetail(context, payload)
                TodayProfitWidgetProvider::class.java ->
                    WidgetRemoteViewsBuilder.buildTodayProfit(context, payload)
                else ->
                    WidgetRemoteViewsBuilder.buildCombined(context, payload)
            }
            manager.updateAppWidget(id, views)
        }
    }
}
