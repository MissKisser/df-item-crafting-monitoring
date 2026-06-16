package com.local.dfcraftmonitor.data.backend

object SensitiveRedactor {
    private val sensitiveKeys = listOf(
        "openid",
        "access_token",
        "appid",
        "ieg_ams_session_token",
        "ieg_ams_token",
        "ieg_ams_token_time",
        "ieg_ams_token_v2",
        "unionid",
        "qq_openid",
        "cookie",
    )

    fun redact(input: String): String =
        sensitiveKeys.fold(input) { acc, key ->
            Regex("(?i)($key=)[^;&\\s]+").replace(acc) {
                "${it.groupValues[1]}<redacted>"
            }
        }
}
