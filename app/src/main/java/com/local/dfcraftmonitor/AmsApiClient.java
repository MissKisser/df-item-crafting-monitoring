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
    /** 与 WebLoginActivity 共用的桌面 Chrome UA，避免被 AMS 判成 WebView 做风控。 */
    public static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final String CRAFTING_ENDPOINT =
            "https://comm.ams.game.qq.com/ide/?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2";
    private static final String USER_LOGIN_ENDPOINT = "https://ams.game.qq.com/ams/userLoginSvr";
    private static final String QQ_OPENID_ENDPOINT = "https://graph.qq.com/oauth2.0/me?access_token=";

    public AmsProbeResult fetchCrafting(AmsCredential credential) throws Exception {
        String body = post(CRAFTING_ENDPOINT, "", credential.cookieHeader());
        return AmsProbeResult.fromCraftingBody(body);
    }

    /**
     * 用浏览器原样的 Cookie 串请求特勤处接口，不经过任何 AmsCredential 改写。
     * 用于办法 A 验证：WebView 抓到什么 Cookie，就原样发什么。
     */
    public AmsProbeResult fetchCraftingWithRawCookie(String rawCookie) throws Exception {
        String body = post(CRAFTING_ENDPOINT, "", rawCookie);
        return AmsProbeResult.fromCraftingBody(body);
    }

    /**
     * M2 命门验证通道：GET 特勤处接口，带完整浏览器请求头 + ptlogin Cookie + g_tk。
     *
     * pvp.qq.com 登录后产出的 p_skey/skey 经 {@link G_tkCalculator} 算出 g_tk，
     * 作为 CSRF token 拼到 query；Cookie 头原样带上。这是路线 B 的核心链路。
     *
     * 与 {@link #fetchCraftingWithRawCookie}（POST 空 body）的对照点：
     * 方法改 GET、补 Referer/Origin/X-Requested-With/桌面 UA、加 g_tk 参数。
     *
     * @param rawCookie ptlogin 域 Cookie（至少含 p_skey 或 skey）
     * @param gtk       由 p_skey/skey 算出的 g_tk
     * @return 探针结果；响应体同时保留在 {@link AmsProbeResult#rawBody} 供人眼排查
     */
    public AmsProbeResult fetchCraftingWithCookieAndGtk(String rawCookie, int gtk) throws Exception {
        String url = CRAFTING_ENDPOINT + "&g_tk=" + gtk;
        String body = getWithCookie(url, rawCookie);
        return AmsProbeResult.fromCraftingBody(body);
    }

    /**
     * 从 "k1=v1; k2=v2" 串里提取 AMS 四元组，构造 AmsCredential。
     * 找不到的字段返回空串。
     */
    public static AmsCredential fromCookieString(String cookieHeader) {
        Map<String, String> kv = parseCookieString(cookieHeader);
        String openid = kv.getOrDefault("openid", "");
        String appid = kv.getOrDefault("appid", "");
        String accessToken = kv.getOrDefault("access_token", "");
        String acctype = kv.getOrDefault("acctype", "");
        return AmsCredential.qq(openid, appid, accessToken);
    }

    /** 解析 "k1=v1; k2=v2" 为 map，忽略空段。 */
    static Map<String, String> parseCookieString(String cookieHeader) {
        Map<String, String> map = new LinkedHashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return map;
        }
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    public String exchangeQqServerSideCode(QqLoginSession session, String appId) throws Exception {
        Map<String, String> fields = buildQqServerSideExchangeFields(session, appId);
        return post(USER_LOGIN_ENDPOINT, encodeForm(fields), null);
    }

    static Map<String, String> buildQqServerSideExchangeFields(QqLoginSession session, String appId) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIfText(fields, "appid", appId);
        putIfText(fields, "miniappid", appId);
        putIfText(fields, "client_id", appId);
        putIfText(fields, "iAppId", appId);
        putIfText(fields, "appId", appId);
        fields.put("acctype", "qc");
        fields.put("game", "dfm");
        fields.put("gameId", "dfm");
        fields.put("source", "2");
        fields.put("format", "json");
        fields.put("need_pay", "1");

        if (session != null) {
            for (Map.Entry<String, String> entry : session.callbackFields().entrySet()) {
                putIfText(fields, entry.getKey(), entry.getValue());
            }
            putIfText(fields, "openid", session.openid);
            putIfText(fields, "code", session.accessTokenOrCode);
            putIfText(fields, "access_token", session.accessTokenOrCode);
        }
        return fields;
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

    /**
     * 带完整浏览器请求头 + Cookie 的 GET 通道。
     * M2 命门验证专用：模拟浏览器从 pvp.qq.com 发起的 AJAX 请求。
     */
    private static String getWithCookie(String endpoint, String cookie) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", DESKTOP_UA);
        connection.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        connection.setRequestProperty("Referer", "https://pvp.qq.com/");
        connection.setRequestProperty("Origin", "https://pvp.qq.com");
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        if (cookie != null && !cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
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

    private static void putIfText(Map<String, String> fields, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            fields.put(key, value);
        }
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
