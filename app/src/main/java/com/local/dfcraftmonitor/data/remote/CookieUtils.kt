package com.local.dfcraftmonitor.data.remote

/**
 * Cookie 字符串处理工具。从 spike 期的 `AmsApiClient.parseCookieString` 迁移而来——
 * 把 "k1=v1; k2=v2" 解析成有序 map，忽略空段与 `eq<=0` 的无效段。
 *
 * 纯逻辑、可单测，无 Android 依赖。
 */
object CookieUtils {
    fun parseCookieString(cookieHeader: String?): Map<String, String> {
        if (cookieHeader.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (part in cookieHeader.split(';')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val key = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }
}
