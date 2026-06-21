package com.local.dfcraftmonitor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 三角洲助手主题。
 *
 * 关键设计：
 *  1. **默认深色**：产品定位夜间作战信息终端，深色为唯一交付主题。
 *  2. **动态取色（Monet）**：Android 12+ 自动从用户壁纸派生主题色，并叠加品牌电光绿调性。
 *  3. **品牌回退**：不支持 Monet 或用户关闭时使用内置品牌色（保证一致体验）。
 *  4. **Expressive 形状与字体**：应用 [DfShapes] 与 [DfTypography]，与 Material 3 Expressive 对齐。
 *
 * 启用/关闭动态取色：通过 [dynamicColor] 参数控制（默认开启）。
 */
@Composable
fun DfCraftingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DfTypography,
        shapes = DfShapes,
        content = content,
    )
}

/** 品牌深色配色（无动态取色时的回退）。 */
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

/** 品牌浅色配色（暂未启用，保留扩展位）。 */
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
