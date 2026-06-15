package com.local.dfcraftmonitor;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;

public final class AmsApiClient {
    private static final String CRAFTING_ENDPOINT =
            "https://comm.ams.game.qq.com/ide/?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2";
    private static final String USER_LOGIN_ENDPOINT = "https://ams.game.qq.com/ams/userLoginSvr";
    private static final String QQ_OPENID_ENDPOINT = "https://graph.qq.com/oauth2.0/me?access_token=";

    public AmsProbeResult fetchCrafting(AmsCredential credential) throws Exception {
        String body = post(CRAFTING_ENDPOINT, "", credential.cookieHeader());
        return AmsProbeResult.fromCraftingBody(body);
    }

    public String exchangeQqServerSideCode(QqLoginSession session, String appId) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("appid", appId);
        fields.put("miniappid", appId);
        fields.put("openid", session.openid);
        fields.put("code", session.accessTokenOrCode);
        fields.put("access_token", session.accessTokenOrCode);
        fields.put("acctype", "qc");
        fields.put("game", "dfm");
        fields.put("gameId", "dfm");
        fields.put("source", "2");
        return post(USER_LOGIN_ENDPOINT, encodeForm(fields), null);
    }

    public String fetchQqOpenid(String accessToken) throws Exception {
        String body = get(QQ_OPENID_ENDPOINT + URLEncoder.encode(accessToken, "UTF-8"));
        int jsonStart = body.indexOf('{');
        int jsonEnd = body.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalStateException("QQ openid response is not JSON-like: " + body);
        }
        JSONObject json = new JSONObject(body.substring(jsonStart, jsonEnd + 1));
        String openid = json.optString("openid", "");
        if (openid.isEmpty()) {
            throw new IllegalStateException("QQ openid response did not include openid: " + body);
        }
        return openid;
    }

    public AmsCredential tryCredentialFromUserLoginResponse(String body, String fallbackAppId) {
        try {
            JSONObject root = new JSONObject(body);
            String token = firstString(root,
                    "qq_ieg_ams_session_token",
                    "ieg_ams_session_token",
                    "access_token",
                    "ticket");
            String openid = firstString(root, "qq_openid", "openid");
            String appid = firstString(root, "qq_ieg_ams_appid", "appid", "app_id");
            if (appid == null || appid.isEmpty()) {
                appid = fallbackAppId;
            }
            if (token != null && openid != null) {
                return AmsCredential.qq(openid, appid, token);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String post(String endpoint, String formBody, String cookie) throws Exception {
        byte[] payload = formBody.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (cookie != null && !cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
        }
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(payload);
        }

        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private static String get(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private static String encodeForm(Map<String, String> fields) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return builder.toString();
    }

    private static String firstString(JSONObject root, String... keys) {
        for (String key : keys) {
            String value = findString(root, key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String findString(Object value, String key) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has(key) && !object.isNull(key)) {
                return object.optString(key, null);
            }
            Iterator<String> names = object.keys();
            while (names.hasNext()) {
                String name = names.next();
                String nested = findString(object.opt(name), key);
                if (nested != null && !nested.isEmpty()) {
                    return nested;
                }
            }
        }
        return null;
    }
}
