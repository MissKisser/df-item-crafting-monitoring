package com.local.dfcraftmonitor;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QqLoginSessionTest {
    @Test
    public void preservesQrCallbackFieldsAndReportsLengths() throws Exception {
        QqLoginSession session = QqLoginSession.fromCallback("{"
                + "\"openid\":\"\","
                + "\"access_token\":\"qr-code-secret\","
                + "\"pay_token\":\"pay-secret\","
                + "\"pf\":\"openmobile_android\","
                + "\"pfkey\":\"pf-key-secret\""
                + "}");

        assertEquals("pay-secret", session.value("pay_token"));
        assertEquals("pf-key-secret", session.value("pfkey"));
        assertTrue(session.fieldSummary().contains("pay_token(len=10)"));
        assertTrue(session.fieldSummary().contains("pfkey(len=13)"));
    }

    @Test
    public void redactsAllCallbackTokenValues() throws Exception {
        QqLoginSession session = QqLoginSession.fromCallback("{"
                + "\"openid\":\"openid-secret\","
                + "\"access_token\":\"qr-code-secret\","
                + "\"pay_token\":\"pay-secret\","
                + "\"pfkey\":\"pf-key-secret\""
                + "}");

        String redacted = SecretRedactor.redact(
                "openid-secret qr-code-secret pay-secret pf-key-secret",
                session
        );

        assertFalse(redacted.contains("openid-secret"));
        assertFalse(redacted.contains("qr-code-secret"));
        assertFalse(redacted.contains("pay-secret"));
        assertFalse(redacted.contains("pf-key-secret"));
    }

    @Test
    public void serverSideExchangeFieldsIncludeQrCallbackTokens() throws Exception {
        QqLoginSession session = QqLoginSession.fromCallback("{"
                + "\"access_token\":\"qr-code-secret\","
                + "\"pay_token\":\"pay-secret\","
                + "\"pf\":\"openmobile_android\","
                + "\"pfkey\":\"pf-key-secret\","
                + "\"expires_in\":\"7776000\""
                + "}");

        Map<String, String> fields = AmsApiClient.buildQqServerSideExchangeFields(
                session,
                "1110543085"
        );

        assertEquals("1110543085", fields.get("appid"));
        assertEquals("1110543085", fields.get("client_id"));
        assertEquals("qr-code-secret", fields.get("code"));
        assertEquals("qr-code-secret", fields.get("access_token"));
        assertEquals("pay-secret", fields.get("pay_token"));
        assertEquals("openmobile_android", fields.get("pf"));
        assertEquals("pf-key-secret", fields.get("pfkey"));
        assertEquals("7776000", fields.get("expires_in"));
    }
}
