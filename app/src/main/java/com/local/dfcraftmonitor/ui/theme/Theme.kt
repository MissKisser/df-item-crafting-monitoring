package com.local.dfcraftmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 暗色板（参考三角洲行动微信小程序的深色面板 + 绿色高亮）
private val DarkColors = darkColorScheme(
    primary = Color(0xFF0FF796),
    background = Color(0xFF070A0B),
    surface = Color(0xFF101615),
    surfaceVariant = Color(0xFF17211F),
    onPrimary = Color(0xFF07100E),
    onBackground = Color(0xFFE8EFEC),
    onSurface = Color(0xFFE8EFEC),
    onSurfaceVariant = Color(0xFF9AA6A1),
)

// 亮色板
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FED),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun DfCraftingTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
