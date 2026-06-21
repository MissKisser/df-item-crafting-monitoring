package com.local.dfcraftmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 三角洲助手主题。
 *
 * 关键设计：
 *  1. **全局深色**：产品定位夜间作战信息终端，深色为唯一交付主题。
 *     即使系统切到浅色，APP 仍维持深色调色板，不跟随系统 uiMode 切换。
 *  2. **禁用 Monet 动态取色**（产品决策）：品牌电光绿 / 琥珀金 / 战术红必须
 *     在所有设备上保持一致，壁纸变化不应改变作战面板语义色。
 *  3. **Expressive 形状与字体**：应用 [DfShapes] 与 [DfTypography]，与 Material 3 Expressive 对齐。
 *
 * 注：保留 [darkTheme] / [dynamicColor] 形参是为了在 Settings 页未来可让用户单独开启个性化，
 * 默认行为不启用。
 */
@Composable
fun DfCraftingTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 全局锁定深色调色板。dynamicColor 形参保留供将来扩展，默认不启用。
    val colorScheme = BrandDarkColorScheme
    @Suppress("UNUSED_VARIABLE")
    val unusedLight = if (!darkTheme) BrandLightColorScheme else colorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DfTypography,
        shapes = DfShapes,
        content = content,
    )
}

/** 品牌深色配色（应用全局深色主题）。 */
private val BrandDarkColorScheme = darkColorScheme(
    primary = DfGreenPrimary,
    onPrimary = DfGreenOnPrimary,
    primaryContainer = DfGreenContainer,
    onPrimaryContainer = DfGreenOnContainer,

    secondary = DfAmberSecondary,
    onSecondary = DfAmberOnSecondary,
    secondaryContainer = DfAmberContainer,
    onSecondaryContainer = DfAmberOnContainer,

    tertiary = DfCyanTertiary,
    onTertiary = DfCyanOnTertiary,
    tertiaryContainer = DfCyanContainer,
    onTertiaryContainer = DfCyanOnContainer,

    error = DfErrorRed,
    onError = DfOnError,
    errorContainer = DfErrorContainer,
    onErrorContainer = DfOnErrorContainer,

    background = DfBackgroundDark,
    onBackground = DfOnBackgroundDark,

    surface = DfSurfaceDark,
    onSurface = DfOnSurfaceDark,
    surfaceVariant = DfSurfaceVariantDark,
    onSurfaceVariant = DfOnSurfaceVariantDark,
    surfaceTint = DfGreenPrimary,

    outline = DfOutlineDark,
    outlineVariant = DfOutlineVariantDark,
    scrim = DfScrimDark,

    inverseSurface = DfInverseSurfaceDark,
    inverseOnSurface = DfInverseOnSurfaceDark,
    inversePrimary = DfInversePrimaryDark,
)

/** 品牌浅色配色（保留扩展位，全局不使用）。 */
@Suppress("unused")
private val BrandLightColorScheme = lightColorScheme(
    primary = DfGreenContainer,
    onPrimary = DfGreenOnContainer,
    secondary = DfAmberContainer,
    onSecondary = DfAmberOnContainer,
    tertiary = DfCyanContainer,
    onTertiary = DfCyanOnContainer,
    error = DfErrorRed,
    onError = DfOnError,
)
