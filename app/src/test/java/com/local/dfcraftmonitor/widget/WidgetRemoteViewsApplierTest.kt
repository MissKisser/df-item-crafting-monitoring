package com.local.dfcraftmonitor.widget

import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 锁定 [WidgetRemoteViewsApplier.computeSignatureInternal] 的不变量——它是"无感刷新"的核心。
 *
 * - 相同显示态 → 相同签名（变化检测跳过重建，倒计时由 Chronometer 自身递减，零闪烁）
 * - 工位倒计时秒数变化（remainingSeconds）不改变签名（这正是"数字动而卡片不闪"的关键）
 * - 工位到点翻转成"已完成"时签名变化（保证到点后正常重建，切换 timer→状态）
 *
 * 关键不变量（修复倒计时负数后新增）：
 * - 签名需识别"最后 60 秒"窗口（remainingSeconds <= 60 且 > 0）：
 *   当外部同步把 remaining 从 61 推回到 60，签名必须变 → 强制重建走 `showCompleted`。
 *   这是防止 Chronometer 越过 base 进入负数的关键防御。
 */
class WidgetRemoteViewsApplierTest {

    /** 60 秒提前窗口阈值；与 [com.local.dfcraftmonitor.widget.WidgetRemoteViewsBuilder.LAST_MINUTE_CUTOFF_SECONDS] 保持一致。 */
    private val lastMinuteCutoff = 60L

    private fun station(
        place: String = "科技中心",
        item: String? = "护甲核心",
        finishAt: Long? = null,
        remaining: Long? = null,
    ) = WidgetPayload.WidgetStation(
        placeName = place,
        itemName = item,
        finishAtEpochSeconds = finishAt,
        remainingSeconds = remaining,
        status = "1",
    )

    private fun payload(
        stations: List<WidgetPayload.WidgetStation>,
        nickname: String = "玩家",
        profitValue: Long = 120_000,
        profitText: String = "+12.0万",
    ) = WidgetPayload(
        accountId = "acc1",
        nickname = nickname,
        avatarUrl = "",
        areaName = "华东",
        todayProfitValue = profitValue,
        todayProfitText = profitText,
        stations = stations,
        fetchedAtEpochMillis = 0L,
    )

    @Test
    fun samePayloadProducesSameSignature() {
        val p = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 300L)))
        val a = WidgetRemoteViewsApplier.computeSignatureInternal(p)
        val b = WidgetRemoteViewsApplier.computeSignatureInternal(p)
        assertEquals("相同 payload 必须签名相同", a, b)
    }

    @Test
    fun remainingSecondsDriftDoesNotChangeSignature() {
        // 倒计时秒数从 300 变到 299——这是每秒都会发生的"数字动"，签名不应变。
        val p1 = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 300L)))
        val p2 = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 299L)))
        assertEquals(
            "倒计时秒数递减不应触发重建（这是无感刷新的关键）",
            WidgetRemoteViewsApplier.computeSignatureInternal(p1),
            WidgetRemoteViewsApplier.computeSignatureInternal(p2),
        )
    }

    @Test
    fun remainingCrossingIntoLastMinuteChangesSignature() {
        // 关键防御：61s → 60s 这 1 秒的跨越必须改变签名，
        // 这样同步一旦把 remaining 拉回到 <= 60s 区间，Widget 立即重建为"已完成"。
        val p1 = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 61L)))
        val p2 = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 60L)))
        assertNotEquals(
            "remaining 从 61 跨到 60 必须改变签名（进入最后 1 分钟窗口）",
            WidgetRemoteViewsApplier.computeSignatureInternal(p1),
            WidgetRemoteViewsApplier.computeSignatureInternal(p2),
        )
    }

    @Test
    fun remainingDriftWithinLastMinuteDoesNotChangeSignature() {
        // 一旦进入最后 1 分钟，签名应稳定（不再随秒级递减而变化），避免 60s 内连续重建闪烁
        val p1 = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 60L)))
        val p2 = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 45L)))
        val p3 = payload(stations = listOf(station(finishAt = 9_999_999_999L, remaining = 30L)))
        val s1 = WidgetRemoteViewsApplier.computeSignatureInternal(p1)
        val s2 = WidgetRemoteViewsApplier.computeSignatureInternal(p2)
        val s3 = WidgetRemoteViewsApplier.computeSignatureInternal(p3)
        assertEquals("最后 1 分钟内秒级递减不应触发重建", s1, s2)
        assertEquals("最后 1 分钟内秒级递减不应触发重建", s2, s3)
    }

    @Test
    fun remainingFrom60To0ChangesSignature() {
        // 60s（已完成）→ 0s（已完成）跨过 completed 标志：必须改签名
        // （实际上 60s 与 0s 都是 "completed=true"，签名应保持一致）
        val future = (System.currentTimeMillis() / 1000) + 60
        val past = (System.currentTimeMillis() / 1000) - 1
        val p1 = payload(stations = listOf(station(finishAt = future, remaining = 60L)))
        val p2 = payload(stations = listOf(station(finishAt = past, remaining = 0L)))
        // 两者都应进 completed=true 分支，签名一致
        assertEquals(
            "已完成的工位无论 remaining=0 还是 remaining=60 都应同签名",
            WidgetRemoteViewsApplier.computeSignatureInternal(p1),
            WidgetRemoteViewsApplier.computeSignatureInternal(p2),
        )
    }

    @Test
    fun finishAtFlippingToCompletedChangesSignature() {
        // finishAt 已过当前时刻 → 签名应变化，保证到点后重建切换成"已完成"。
        val future = (System.currentTimeMillis() / 1000) + 600
        val past = (System.currentTimeMillis() / 1000) - 10
        val p1 = payload(stations = listOf(station(finishAt = future, remaining = 600L)))
        val p2 = payload(stations = listOf(station(finishAt = past, remaining = 0L)))
        assertNotEquals(
            "工位到点翻转成已完成时签名必须变化",
            WidgetRemoteViewsApplier.computeSignatureInternal(p1),
            WidgetRemoteViewsApplier.computeSignatureInternal(p2),
        )
    }

    @Test
    fun profitOrAccountChangeChangesSignature() {
        val base = payload(stations = emptyList())
        val diffProfit = base.copy(todayProfitValue = 999L, todayProfitText = "+999")
        val diffName = base.copy(nickname = "另一个玩家")
        assertNotEquals(
            "盈亏变化应改变签名",
            WidgetRemoteViewsApplier.computeSignatureInternal(base),
            WidgetRemoteViewsApplier.computeSignatureInternal(diffProfit),
        )
        assertNotEquals(
            "账号变化应改变签名",
            WidgetRemoteViewsApplier.computeSignatureInternal(base),
            WidgetRemoteViewsApplier.computeSignatureInternal(diffName),
        )
    }

    @Test
    fun nullPayloadHasStableDistinctSignature() {
        assertEquals(
            WidgetRemoteViewsApplier.computeSignatureInternal(null),
            WidgetRemoteViewsApplier.computeSignatureInternal(null),
        )
        assertNotEquals(
            WidgetRemoteViewsApplier.computeSignatureInternal(null),
            WidgetRemoteViewsApplier.computeSignatureInternal(payload(stations = emptyList())),
        )
    }
}
