package com.local.dfcraftmonitor.data.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveRedactorTest {

    @Test
    fun redactsAmsCookieValues() {
        val input = "openid=OPENID123456; acctype=qc; appid=APPID987; access_token=TOKEN654321"
        val redacted = SensitiveRedactor.redact(input)

        assertFalse(redacted.contains("OPENID123456"))
        assertFalse(redacted.contains("APPID987"))
        assertFalse(redacted.contains("TOKEN654321"))
        assertTrue(redacted.contains("openid=<redacted>"))
        assertTrue(redacted.contains("access_token=<redacted>"))
    }

    @Test
    fun redactsMiniProgramTokensAndUnionId() {
        val input = "ieg_ams_session_token=s1&ieg_ams_token=t1&ieg_ams_token_v2=t2&unionid=u1&qq_openid=q1"
        val redacted = SensitiveRedactor.redact(input)

        assertFalse(redacted.contains("s1"))
        assertFalse(redacted.contains("t1"))
        assertFalse(redacted.contains("t2"))
        assertFalse(redacted.contains("u1"))
        assertFalse(redacted.contains("q1"))
        assertTrue(redacted.contains("ieg_ams_session_token=<redacted>"))
        assertTrue(redacted.contains("unionid=<redacted>"))
    }
}
