package com.local.dfcraftmonitor.widget

import androidx.compose.runtime.compositionLocalOf

/**
 * 桌面 widget 的"配置完成"桥。
 *
 * 当 [android.appwidget.AppWidgetManager] 启动 App 进行 widget 配置时（拖放卡片 → 弹
 * 配置页），App 必须用 `setResult(RESULT_OK, Intent().putExtra(EXTRA_APPWIDGET_ID, id))`
 * + `finish()` 通知 Launcher 完成 pin 流程；若用户中途取消则 `RESULT_CANCELED`。
 *
 * 该结构由承载 Activity 注入 CompositionLocal，被配置页 Composable
 * （如 [com.local.dfcraftmonitor.ui.settings.DaySecretMapPickerScreen]）消费，
 * 以避免配置页 Composable 与具体 Activity 类型耦合。
 *
 * @property widgetId Launcher 传进来的 widget id，需带回 result intent。
 * @property onComplete 保存后调用：内部 `setResult(OK, EXTRA_APPWIDGET_ID) + finish()`。
 * @property onCancel 取消时调用：内部 `setResult(CANCELED) + finish()`。返回键、未点保存
 *                    直接退出配置页等"用户没确认"路径走这里。
 */
data class WidgetConfigureBridge(
    val widgetId: Int,
    val onComplete: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * 当前 Composition 内是否存在一个"等待完成的 widget 配置"任务。
 *
 * - 正常启动 App（launcher 图标）→ `null`，picker 屏幕走普通 `onDone` popBack。
 * - Launcher 拖完 widget 启动 App → 非空，picker 保存后调 `bridge.onComplete()`。
 */
val LocalWidgetConfigureBridge = compositionLocalOf<WidgetConfigureBridge?> { null }
