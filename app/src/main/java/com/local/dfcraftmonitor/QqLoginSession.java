package com.local.dfcraftmonitor;

import org.json.JSONObject;

public final class QqLoginSession {
    public final String openid;
    public final String accessTokenOrCode;
    public final String expiresIn;
    public final String rawJson;

    private QqLoginSession(String openid, String accessTokenOrCode, String expiresIn, String rawJson) {
        this.openid = openid;
        this.accessTokenOrCode = accessTokenOrCode;
        this.expiresIn = expiresIn;
        this.rawJson = rawJson;
    }

    public static QqLoginSession fromCallback(Object callbackValue) throws Exception {
        JSONObject json = callbackValue instanceof JSONObject
                ? (JSONObject) callbackValue
                : new JSONObject(String.valueOf(callbackValue));
        return new QqLoginSession(
                json.optString("openid", ""),
                json.optString("access_token", ""),
                json.optString("expires_in", ""),
                json.toString()
        );
    }

    public QqLoginSession withFallback(String fallbackOpenid, String fallbackAccessToken) {
        String nextOpenid = hasText(openid) ? openid : fallbackOpenid;
        String nextToken = hasText(accessTokenOrCode) ? accessTokenOrCode : fallbackAccessToken;
        return new QqLoginSession(
                nextOpenid == null ? "" : nextOpenid,
                nextToken == null ? "" : nextToken,
                expiresIn,
                rawJson
        );
    }

    public String fieldSummary() {
        try {
            JSONObject json = new JSONObject(rawJson);
            StringBuilder builder = new StringBuilder();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = json.optString(key, "");
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(key).append("(len=").append(value.length()).append(")");
            }
            return builder.toString();
        } catch (Exception error) {
            return "rawCallback(len=" + rawJson.length() + ")";
        }
    }

    public AmsCredential asDirectQqCredential(String appId) {
        return AmsCredential.qq(openid, appId, accessTokenOrCode);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
