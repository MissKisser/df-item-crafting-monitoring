package com.local.dfcraftmonitor.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 作战面板：替代旧的 [androidx.compose.material3.Card]，表达"作战终端卡片"质感。
 *
 * 设计要点：
 *  - **柔和圆角** 16dp（跟随 Material 3 Expressive `medium` 形状）
 *  - **纵向微渐变**（顶亮底暗）模拟"背光面板"
 *  - **1dp 描边**，描边色可由调用方覆盖（成功/警告/错误）
 *  - **无阴影**（信息终端风格）
 */
@Composable
fun TacticalPanel(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val targetBorder = borderColor ?: MaterialTheme.colorScheme.outline
    val animatedBorder by animateColorAsState(targetBorder, label = "tactical-panel-border")
    val surface = MaterialTheme.colorScheme.surface
    val surfaceTop = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, animatedBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to surfaceTop,
                        1f to surface,
                    ),
                )
                .padding(16.dp),
            content = content,
        )
    }
}

/** 段落标题：粗体大标题 + 副标题（可选）。 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    compact: Boolean = false,
) {
    Column {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 键值行：左标签 + 右值，支持值着色。 */
@Composable
fun StatusRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 列表行之间的细分隔线（半透明描边色）。 */
@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
