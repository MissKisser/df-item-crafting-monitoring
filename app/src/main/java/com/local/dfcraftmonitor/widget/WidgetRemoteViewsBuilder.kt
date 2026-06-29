package com.local.dfcraftmonitor.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.local.dfcraftmonitor.R
import com.local.dfcraftmonitor.data.monitor.WidgetPayload

/**
 * 根据 [WidgetPayload] 构建 RemoteViews。
 *
 * 三种 Widget 共用同一套工位行渲染逻辑，区别仅在于布局文件和是否显示盈亏区。
 * Chronometer 的 base 设为 SystemClock.elapsedRealtime() + remainingMillis，
 * 系统自动每秒递减，app 进程死亡后仍由 Launcher 托管继续计时。
 */
object WidgetRemoteViewsBuilder {

    private const val MAX_STATIONS = 4

    /**
     * 最后 1 分钟阈值：工位剩余时间 ≤ 此值时，Widget 不再渲染 Chronometer，
     * 直接显示"已完成"。这是防止 Chronometer 越过 base 进入负数的核心防御。
     *
     * 为什么选 60s：
     *  - Chronometer 的时间分辨率 = 1s，最后 1 分钟内的视觉差异微弱
     *  - 1 分钟 = 4 个 15 分钟同步周期之内，签名几乎必刷新（用户手动刷、Worker 触发都覆盖）
     *  - 选更大阈值（如 300s）会损失用户体验——用户想看精确倒计时
     *  - 选更小阈值（如 5s）则可能因为 WorkManager 延迟到点 > 阈值而出现负数
     *
     * 注：此常量与 [WidgetRemoteViewsApplier] 的"提前 60s 视作 completed"必须保持一致。
     */
    const val LAST_MINUTE_CUTOFF_SECONDS = 60L

    // 与 res/values/colors.xml 调色板对齐（RemoteViews 无法引用 @color，故用 ARGB 常量）。
    private const val COLOR_TEXT_PRIMARY = 0xFFF3F4F6.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFF9AA7C7.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF5E6B8A.toInt()
    private const val COLOR_GREEN = 0xFF34D399.toInt()
    private const val COLOR_RED = 0xFFF87171.toInt()
    private const val COLOR_ORANGE = 0xFFFBBF24.toInt()

    /** 构建制造详情 Widget (4×1) 的 RemoteViews。 */
    fun buildCraftingDetail(context: Context, payload: WidgetPayload?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_crafting_detail)
        bindRefreshButton(context, views)
        bindAccountName(views, payload)
        bindStations(views, payload)
        return views
    }

    /** 构建今日盈亏 Widget (1×1) 的 RemoteViews。 */
    fun buildTodayProfit(context: Context, payload: WidgetPayload?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_profit)
        bindRefreshButton(context, views)
        bindProfit(views, payload)
        views.setTextViewText(R.id.account_name, payload?.nickname ?: "")
        return views
    }

    /** 构建组合 Widget (3×2) 的 RemoteViews。 */
    fun buildCombined(context: Context, payload: WidgetPayload?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_combined)
        bindRefreshButton(context, views)
        bindAccountName(views, payload)
        bindStations(views, payload)
        bindProfit(views, payload)
        return views
    }

    /** 构建今日密码 Widget (4×1) 的 RemoteViews。
     *
     *  @param prefs 用户已勾选的地图名集合（来自 UserPreferences.daySecretWidgetVisibleMaps）。
     *               空集 → 用全部地图按字典序兜底填前 4 个。
     */
    fun buildDaySecret(context: Context, payload: WidgetPayload?, prefs: Set<String>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_secret)
        bindRefreshButton(context, views)
        bindAccountName(views, payload)
        bindDaySecretCells(views, pickDaySecretCells(payload, prefs))
        return views
    }

    /**
     * 选格纯函数（与 RemoteViews 解耦，方便纯 JUnit 测试）。
     *
     * 返回 List 大小固定为 [MAX_STATIONS]=4，元素可能为 null（"未选"）。
     *
     * 规则（spec §3.2）：
     *  - payload 为空 / daySecrets 为空 → 全部 null
     *  - prefs 空 → 按 daySecrets 字典序取前 4 个，少于 4 余下 null
     *  - prefs 非空 → 按 prefs 顺序取 4 个；若某名字不在 daySecrets 里则该位置 null
     */
    fun pickDaySecretCells(payload: WidgetPayload?, prefs: Set<String>): List<WidgetPayload.DaySecretEntry?> {
        val all = payload?.daySecrets ?: emptyList()
        return if (prefs.isEmpty()) {
            // 用全部地图按字典序填前 4 个，未填的位置 null
            val top = all.sortedBy { it.mapName }.take(MAX_STATIONS)
            top + List(MAX_STATIONS - top.size) { null }
        } else {
            // 严格保持 prefs 顺序（linkedSetOf 入参顺序保留）：命中的用 entry，未命中的 null
            prefs.take(MAX_STATIONS).map { name -> all.firstOrNull { it.mapName == name } }
                .let { filled -> filled + List(MAX_STATIONS - filled.size) { null } }
        }
    }

    private fun bindDaySecretCells(
        views: RemoteViews,
        cells: List<WidgetPayload.DaySecretEntry?>,
    ) {
        for (i in 0 until MAX_STATIONS) {
            val entry = cells.getOrNull(i)
            views.setViewVisibility(rowId(i), android.view.View.VISIBLE)
            if (entry == null) {
                views.setTextViewText(mapNameId(i), "未选")
                views.setTextViewText(secretId(i), "--")
                views.setTextColor(mapNameId(i), COLOR_TEXT_MUTED)
                views.setTextColor(secretId(i), COLOR_TEXT_MUTED)
            } else {
                views.setTextViewText(mapNameId(i), entry.mapName)
                views.setTextViewText(secretId(i), entry.secret)
                views.setTextColor(mapNameId(i), COLOR_TEXT_SECONDARY)
                views.setTextColor(secretId(i), COLOR_ORANGE)
            }
        }
    }

    private fun mapNameId(i: Int) = when (i) {
        0 -> R.id.map_name_0
        1 -> R.id.map_name_1
        2 -> R.id.map_name_2
        else -> R.id.map_name_3
    }

    private fun secretId(i: Int) = when (i) {
        0 -> R.id.secret_0
        1 -> R.id.secret_1
        2 -> R.id.secret_2
        else -> R.id.secret_3
    }

    private fun bindRefreshButton(context: Context, views: RemoteViews) {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, pi)
    }

    private fun bindAccountName(views: RemoteViews, payload: WidgetPayload?) {
        val name = payload?.nickname?.ifBlank { payload.areaName } ?: ""
        views.setTextViewText(R.id.account_name, name)
    }

    private fun bindStations(views: RemoteViews, payload: WidgetPayload?) {
        val stations = payload?.stations ?: emptyList()
        if (stations.isEmpty()) {
            views.setViewVisibility(rowId(0), android.view.View.VISIBLE)
            views.setTextViewText(stationNameId(0), "暂无数据")
            views.setTextViewText(itemNameId(0), "点刷新")
            views.setTextViewText(statusId(0), "--")
            views.setTextColor(statusId(0), COLOR_TEXT_MUTED)
            views.setViewVisibility(timerId(0), android.view.View.GONE)
            views.setViewVisibility(statusId(0), android.view.View.VISIBLE)
            for (i in 1 until MAX_STATIONS) {
                views.setViewVisibility(rowId(i), android.view.View.GONE)
            }
            return
        }
        for (i in 0 until MAX_STATIONS) {
            val station = stations.getOrNull(i)
            if (station == null) {
                views.setViewVisibility(rowId(i), android.view.View.GONE)
                continue
            }
            views.setViewVisibility(rowId(i), android.view.View.VISIBLE)
            views.setTextViewText(stationNameId(i), station.placeName)
            views.setTextViewText(itemNameId(i), station.itemName ?: "空闲")

            val remaining = station.remainingSeconds
            if (remaining != null && remaining > 0) {
                // 修复负数显示：最后 1 分钟不再渲染 Chronometer，
                // 直接走 showCompleted。这与 [WidgetRemoteViewsApplier] 的签名提前窗口保持一致。
                if (remaining <= LAST_MINUTE_CUTOFF_SECONDS) {
                    showCompleted(views, i)
                } else {
                    val finishAt = station.finishAtEpochSeconds
                    if (finishAt != null) {
                        val nowEpochSeconds = System.currentTimeMillis() / 1000
                        val remainingMillis = (finishAt - nowEpochSeconds) * 1000
                        if (remainingMillis > 0) {
                            val base = SystemClock.elapsedRealtime() + remainingMillis
                            views.setChronometer(timerId(i), base, null, true)
                            views.setTextColor(timerId(i), getRemainingColor(remaining))
                            views.setViewVisibility(timerId(i), android.view.View.VISIBLE)
                            views.setViewVisibility(statusId(i), android.view.View.GONE)
                        } else {
                            showCompleted(views, i)
                        }
                    } else {
                        views.setTextViewText(statusId(i), formatRemaining(remaining))
                        views.setTextColor(statusId(i), getRemainingColor(remaining))
                        views.setViewVisibility(timerId(i), android.view.View.GONE)
                        views.setViewVisibility(statusId(i), android.view.View.VISIBLE)
                    }
                }
            } else if (remaining != null && remaining <= 0) {
                showCompleted(views, i)
            } else {
                views.setTextViewText(statusId(i), "空闲")
                views.setTextColor(statusId(i), COLOR_TEXT_MUTED)
                views.setViewVisibility(timerId(i), android.view.View.GONE)
                views.setViewVisibility(statusId(i), android.view.View.VISIBLE)
            }
        }
    }

    private fun showCompleted(views: RemoteViews, index: Int) {
        views.setTextViewText(statusId(index), "已完成")
        views.setTextColor(statusId(index), COLOR_GREEN)
        views.setViewVisibility(timerId(index), android.view.View.GONE)
        views.setViewVisibility(statusId(index), android.view.View.VISIBLE)
    }

    private fun bindProfit(views: RemoteViews, payload: WidgetPayload?) {
        val rawText = payload?.todayProfitText ?: "--"
        val color = when {
            payload == null -> COLOR_TEXT_MUTED
            payload.todayProfitValue > 0 -> COLOR_GREEN
            payload.todayProfitValue < 0 -> COLOR_RED
            else -> COLOR_TEXT_PRIMARY
        }
        // 正负收益加方向符（▲/▼），0 与未知保持原样。
        val text = when {
            payload == null -> rawText
            payload.todayProfitValue > 0 -> "▲ $rawText"
            payload.todayProfitValue < 0 -> "▼ $rawText"
            else -> rawText
        }
        views.setTextViewText(R.id.profit_text, text)
        views.setTextColor(R.id.profit_text, color)
    }

    private fun getRemainingColor(seconds: Long): Int = when {
        seconds <= 0 -> COLOR_GREEN
        seconds < 300 -> COLOR_RED
        seconds < 900 -> COLOR_ORANGE
        else -> COLOR_TEXT_PRIMARY
    }

    private fun formatRemaining(seconds: Long): String {
        if (seconds <= 0) return "已完成"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun rowId(i: Int) = when (i) {
        0 -> R.id.row_0
        1 -> R.id.row_1
        2 -> R.id.row_2
        else -> R.id.row_3
    }

    private fun stationNameId(i: Int) = when (i) {
        0 -> R.id.station_name_0
        1 -> R.id.station_name_1
        2 -> R.id.station_name_2
        else -> R.id.station_name_3
    }

    private fun itemNameId(i: Int) = when (i) {
        0 -> R.id.item_name_0
        1 -> R.id.item_name_1
        2 -> R.id.item_name_2
        else -> R.id.item_name_3
    }

    private fun timerId(i: Int) = when (i) {
        0 -> R.id.timer_0
        1 -> R.id.timer_1
        2 -> R.id.timer_2
        else -> R.id.timer_3
    }

    private fun statusId(i: Int) = when (i) {
        0 -> R.id.status_0
        1 -> R.id.status_1
        2 -> R.id.status_2
        else -> R.id.status_3
    }
}
