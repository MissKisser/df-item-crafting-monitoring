package com.local.dfcraftmonitor.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 公共格式化工具。
 *
 * 集中放这里，避免散落在多个文件里：UI 测试要锁文案时也只改这一处。
 */
object Formatters {

    /** "2时30分15秒" / "30分15秒" */
    fun duration(seconds: Long): String {
        if (seconds <= 0) return "0分0秒"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "${h}时${m}分${s}秒" else "${m}分${s}秒"
    }

    /** "MM-dd HH:mm:ss"（中国时区，锁语言环境为 CHINA 避免海外设备数字/格式漂移） */
    fun epochSeconds(epochSeconds: Long?): String {
        if (epochSeconds == null || epochSeconds <= 0) return "-"
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        return fmt.format(Date(epochSeconds * 1000L))
    }

    /** 数字美化：>=10000 显示为 "1.2万"，>=1e8 显示为 "1.2亿" */
    fun prettyLong(value: Long?): String {
        if (value == null) return "-"
        val absVal = abs(value)
        return when {
            absVal >= 100_000_000L -> String.format(Locale.US, "%.1f亿", absVal / 100_000_000.0)
            absVal >= 10_000L -> String.format(Locale.US, "%.1f万", absVal / 10_000.0)
            else -> absVal.toString()
        }
    }

    /** 千分位原始数值（与小程序 addComma 一致）。 */
    fun commaLong(value: Long): String {
        val sign = if (value < 0) "-" else ""
        val v = abs(value).toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        return sign + v
    }

    /** "yyyy-MM-dd" 今日日期字符串（CHINA 时区）。 */
    fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
}

/** 工具扩展：Long 千分位格式化（带负号）。 */
fun Long.formatCommaSafe(): String = Formatters.commaLong(this)
