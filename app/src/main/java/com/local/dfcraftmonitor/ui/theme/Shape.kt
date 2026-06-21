package com.local.dfcraftmonitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 三角洲助手形状方案。
 *
 * Material 3 Expressive 强调"触感形状"——用更大的圆角与异形圆角来引导焦点。
 * 我们在保留"作战终端"硬朗感的同时：
 *  - extraSmall/Small：标签、徽章、输入框（紧实）
 *  - medium：列表项、按钮（友好但克制）
 *  - large/extralarge：卡片、弹窗（柔和焦点）
 *  - topExtraLarge、bottomExtraLarge：抽屉、底部弹层（异形圆角）
 */
val DfShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
