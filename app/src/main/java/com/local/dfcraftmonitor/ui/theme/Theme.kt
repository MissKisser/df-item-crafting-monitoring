package com.local.dfcraftmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 暗色板（参考腾讯深色风格）
private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    background = Color(0xFF1F2937),
    surface = Color(0xFF273345),
)

// 亮色板
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FED),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun DfCraftingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
