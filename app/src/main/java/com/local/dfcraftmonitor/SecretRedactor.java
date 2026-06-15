package com.local.dfcraftmonitor;

public final class SecretRedactor {
    private SecretRedactor() {
    }

    public static String redact(String text, QqLoginSession session) {
        if (text == null) {
            return "";
        }
        String redacted = text;
        if (session != null) {
            redacted = replace(redacted, session.openid);
            redacted = replace(redacted, session.accessTokenOrCode);
            for (String value : session.callbackFields().values()) {
                redacted = replace(redacted, value);
            }
        }
        return redacted;
    }

    private static String replace(String text, String secret) {
        if (secret == null || secret.length() < 4) {
            return text;
        }
        return text.replace(secret, "[REDACTED]");
    }
}
