package com.local.dfcraftmonitor.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 桌面卡片 BroadcastReceiver，Glance 框架自动注册。
 *
 * 在 AndroidManifest 中声明即可，无需手动处理 onReceive。
 * 框架会调用 [CraftingWidget.provideGlance] 渲染内容。
 */
class CraftingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CraftingWidget()
}
