package com.local.dfcraftmonitor.ui.common

import androidx.compose.ui.graphics.Color

/**
 * 物品等级背景色，与三角洲行动微信小程序的 itemBackColor 视觉一致。
 *
 * grade 含义：
 *  - 1：灰（无等级）
 *  - 2：绿
 *  - 3：蓝
 *  - 4：紫
 *  - 5：金
 *  - 6：红
 *  - 7：深红
 */
fun gradeBackgroundColor(grade: Int): Color = when (grade) {
    2 -> Color(0xFF0A1F1A)
    3 -> Color(0xFF11243A)
    4 -> Color(0xFF1F1830)
    5 -> Color(0xFF2E2210)
    6 -> Color(0xFF34191B)
    7 -> Color(0xFF341E20)
    else -> Color(0xFF15191A)
}
