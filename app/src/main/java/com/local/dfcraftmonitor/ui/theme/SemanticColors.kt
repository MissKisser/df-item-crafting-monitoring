package com.local.dfcraftmonitor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 业务语义色：用于在动态取色主题下保持"作战终端"的稳定语义。
 *
 * 例如：成功/失败/警告/价格涨跌——不应随系统壁纸变化。
 * 而按钮/卡片背景可以跟随动态取色。
 */
data class DfSemanticColors(
    val profit: Color,
    val loss: Color,
    val warn: Color,
    val neutralText: Color,
    val faintText: Color,
    val onDarkSurface: Color,
    val gold: Color,
)

private val defaultSemantic = DfSemanticColors(
    profit = DfGreenPrimary,
    loss = DfErrorRed,
    warn = DfAmberSecondary,
    neutralText = DfMutedDark,
    faintText = DfFaintDark,
    onDarkSurface = DfWhite,
    gold = DfGold,
)

/** 业务色（直接 Color，可在非 Composable 上下文使用）。 */
val SemanticProfit: Color = DfGreenPrimary
val SemanticLoss: Color = DfErrorRed
val SemanticWarn: Color = DfAmberSecondary
val SemanticNeutral: Color = DfMutedDark
val SemanticFaint: Color = DfFaintDark
val SemanticOnDark: Color = DfWhite
val SemanticGold: Color = DfGold

val LocalSemanticColors = compositionLocalOf { defaultSemantic }

/** 在 Composable 中读取语义色，缺省回退到品牌色。 */
object SemanticColors {
    val profit: Color
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current.profit
    val loss: Color
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current.loss
    val warn: Color
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current.warn
    val neutralText: Color
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current.neutralText
    val faintText: Color
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current.faintText
    val onDarkSurface: Color
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current.onDarkSurface
    val gold: Color
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current.gold
}
