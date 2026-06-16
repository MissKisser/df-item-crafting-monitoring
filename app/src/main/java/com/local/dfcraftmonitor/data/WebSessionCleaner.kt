package com.local.dfcraftmonitor.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 清除 WebView 登录态与缓存，确保退出后下一次扫码不会复用旧 QQ / 微信账号。
 */
@Singleton
class WebSessionCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun clear() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            removeSessionCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).apply {
            clearHttpAuthUsernamePassword()
            @Suppress("DEPRECATION")
            clearFormData()
        }
        runCatching { context.cacheDir.deleteRecursively() }
        runCatching { context.codeCacheDir.deleteRecursively() }
    }
}
