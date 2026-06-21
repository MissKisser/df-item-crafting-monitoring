package com.local.dfcraftmonitor.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 拦截器：给所有 AMS 请求注入桌面 UA + 官方 WebMP Referer/Origin + 当前 Cookie。
 *
 * 来自 spike 抓包比对出来的请求头组合，是 M2 命门验证通过的关键之一。
 * 这些头缺一不可，缺哪个 AMS 都可能返回"未登录"。
 *
 * Cookie 由外部通过 [setCookie] 注入（来自 SessionHolder），不持久化。
 */
class AmsHeadersInterceptor : Interceptor {

    @Volatile
    var cookie: String = ""
        set(value) {
            field = value.ifBlank { "" }
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", AmsConstants.DESKTOP_UA)
            .header("Referer", "https://df.qq.com/cp/a20241230webmp/index.html")
            .header("Origin", "https://df.qq.com")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .apply {
                if (cookie.isNotEmpty()) header("Cookie", cookie)
            }
            .build()
        return chain.proceed(request)
    }
}
