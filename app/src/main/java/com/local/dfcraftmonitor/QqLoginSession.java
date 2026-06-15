package com.local.dfcraftmonitor;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QqLoginSession {
    public final String openid;
    public final String accessTokenOrCode;
    public final String expiresIn;
    public final String rawJson;
    private final Map<String, String> fields;

    private QqLoginSession(
            String openid,
            String accessTokenOrCode,
            String expiresIn,
            String rawJson,
            Map<String, String> fields
    ) {
        this.openid = openid;
        this.accessTokenOrCode = accessTokenOrCode;
        this.expiresIn = expiresIn;
        this.rawJson = rawJson;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static QqLoginSession fromCallback(Object callbackValue) throws Exception {
        JSONObject json = callbackValue instanceof JSONObject
                ? (JSONObject) callbackValue
                : new JSONObject(String.valueOf(callbackValue));
        Map<String, String> fields = fieldsFrom(json);
        return new QqLoginSession(
                value(fields, "openid"),
                value(fields, "access_token"),
                value(fields, "expires_in"),
                json.toString(),
                fields
        );
    }

    public QqLoginSession withFallback(String fallbackOpenid, String fallbackAccessToken) {
        String nextOpenid = hasText(openid) ? openid : fallbackOpenid;
        String nextToken = hasText(accessTokenOrCode) ? accessTokenOrCode : fallbackAccessToken;
        Map<String, String> nextFields = new LinkedHashMap<>(fields);
        putIfMissing(nextFields, "openid", nextOpenid);
        putIfMissing(nextFields, "access_token", nextToken);
        return new QqLoginSession(
                nextOpenid == null ? "" : nextOpenid,
                nextToken == null ? "" : nextToken,
                expiresIn,
                rawJson,
                nextFields
        );
    }

    public String value(String key) {
        return value(fields, key);
    }

    public Map<String, String> callbackFields() {
        return fields;
    }

    public String fieldSummary() {
        if (fields.isEmpty()) {
            return "rawCallback(len=" + rawJson.length() + ")";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey())
                    .append("(len=")
                    .append(entry.getValue() == null ? 0 : entry.getValue().length())
                    .append(")");
        }
        return builder.toString();
    }

    public AmsCredential asDirectQqCredential(String appId) {
        return AmsCredential.qq(openid, appId, accessTokenOrCode);
    }

    private static Map<String, String> fieldsFrom(JSONObject json) {
        Map<String, String> fields = new LinkedHashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            fields.put(key, json.optString(key, ""));
        }
        return fields;
    }

    private static String value(Map<String, String> fields, String key) {
        String value = fields.get(key);
        return value == null ? "" : value;
    }

    private static void putIfMissing(Map<String, String> fields, String key, String value) {
        if (!hasText(fields.get(key)) && hasText(value)) {
            fields.put(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
