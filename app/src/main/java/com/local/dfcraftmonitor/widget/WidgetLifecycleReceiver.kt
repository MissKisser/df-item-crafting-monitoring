package com.local.dfcraftmonitor.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetLifecycleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
            -> WidgetRefreshReceiver.restoreAndRequestRefresh(context)
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (intent.data?.schemeSpecificPart == context.packageName) {
                    WidgetRefreshReceiver.restoreAndRequestRefresh(context)
                }
            }
        }
    }
}
