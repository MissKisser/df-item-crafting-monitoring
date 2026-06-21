package com.local.dfcraftmonitor.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 清除 WebView 登录态与缓存，确保"新增账号"时不会复用旧 QQ / 微信 Cookie。
 *
 * suspend：必须等待 CookieManager 异步回调完成后再继续后续流程，
 * 否则紧跟其后的 loadUrl 仍可能读到旧 Cookie。
 */
@Singleton
class WebSessionCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun clear() {
        val cm = CookieManager.getInstance()
        suspendCancellableCoroutine<Unit> { cont ->
            cm.removeAllCookies {
                cm.removeSessionCookies {
                    cm.flush()
                    if (cont.isActive) cont.resume(Unit)
                }
            }
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
