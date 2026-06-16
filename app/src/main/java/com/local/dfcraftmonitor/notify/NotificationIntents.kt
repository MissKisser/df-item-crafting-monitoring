package com.local.dfcraftmonitor.notify

/**
 * 通知 action / content intent 的 intent action 常量。
 * 与 NotificationActionReceiver 的 intent-filter 对应。
 */
object NotificationIntents {
    const val ACTION_OPEN_APP = "com.local.dfcraftmonitor.notify.OPEN_APP"
    const val ACTION_PAUSE_SYNC = "com.local.dfcraftmonitor.notify.PAUSE_SYNC"
    const val ACTION_RESUME_SYNC = "com.local.dfcraftmonitor.notify.RESUME_SYNC"
    const val ACTION_RELOGIN = "com.local.dfcraftmonitor.notify.RELOGIN"

    /** intent extras key：完成工位的 itemId（CompletionNotifier 详情点击携带） */
    const val EXTRA_ITEM_ID = "itemId"
    const val EXTRA_STATION_PLACE = "placeName"
    const val EXTRA_STATION_ITEM = "itemName"
}
