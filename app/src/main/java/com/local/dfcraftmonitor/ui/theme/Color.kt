package com.local.dfcraftmonitor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 三角洲助手品牌色板。
 *
 * 主题强调色继承自 Material 3 Expressive 调色板：
 *   - 电光绿（主）：行动/可用/正向
 *   - 琥珀金（次）：价格/重点数字
 *   - 战术红（错误）：登录失效/负收益
 *   - 深墨背景：作战面板底色
 *
 * 浅色主题暂不开放（产品定位夜间作战信息终端）。
 */

// 主色：电光绿（品牌强调）
internal val DfGreenPrimary = Color(0xFF0FF796)
internal val DfGreenOnPrimary = Color(0xFF003822)
internal val DfGreenContainer = Color(0xFF005236)
internal val DfGreenOnContainer = Color(0xFF74FBC2)

// 次色：琥珀金（价格/重点）
internal val DfAmberSecondary = Color(0xFFFFC95C)
internal val DfAmberOnSecondary = Color(0xFF3D2D00)
internal val DfAmberContainer = Color(0xFF5B4300)
internal val DfAmberOnContainer = Color(0xFFFFE08A)

// 第三色：战术青蓝（战报/链接）
internal val DfCyanTertiary = Color(0xFF7BD7FF)
internal val DfCyanOnTertiary = Color(0xFF003545)
internal val DfCyanContainer = Color(0xFF004D63)
internal val DfCyanOnContainer = Color(0xFFCAF3FF)

// 错误色：战术红
internal val DfErrorRed = Color(0xFFFF5C7A)
internal val DfOnError = Color(0xFF570019)
internal val DfErrorContainer = Color(0xFF7E0024)
internal val DfOnErrorContainer = Color(0xFFFFD9DF)

// 中性色：深墨背景系列
internal val DfBackgroundDark = Color(0xFF060A0B)
internal val DfOnBackgroundDark = Color(0xFFE8EFEC)

internal val DfSurfaceDark = Color(0xFF0D1312)
internal val DfOnSurfaceDark = Color(0xFFE8EFEC)

internal val DfSurfaceVariantDark = Color(0xFF17211F)
internal val DfOnSurfaceVariantDark = Color(0xFF9AA6A1)

internal val DfOutlineDark = Color(0xFF25463C)
internal val DfOutlineVariantDark = Color(0xFF14201D)

internal val DfInverseOnSurfaceDark = Color(0xFF060A0B)
internal val DfInverseSurfaceDark = Color(0xFFE8EFEC)
internal val DfInversePrimaryDark = Color(0xFF00B46C)

// 滚动/分割线
internal val DfScrimDark = Color(0xCC000000)

// 自定义语义色（与具体业务绑定）
internal val DfGold = Color(0xFFFFC95C)
internal val DfLossRed = Color(0xFFFF5C7A)
internal val DfFaintDark = Color(0xFF5D6B65)
internal val DfMutedDark = Color(0xFF9AA6A1)
internal val DfWhite = Color(0xFFEAF2EE)
