package com.local.dfcraftmonitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;
import com.tencent.connect.auth.AuthAgent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsApiClient amsApiClient = new AmsApiClient();

    private Tencent tencent;
    private TextView stateView;
    private TextView logView;
    private Button loginButton;
    private Button normalLoginButton;
    private IUiListener loginListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        setupLoginListener();
        initTencent();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Tencent.REQUEST_LOGIN) {
            Tencent.onActivityResultData(requestCode, resultCode, data, loginListener);
        } else if (requestCode == WebLoginActivity.REQUEST_WEB_LOGIN) {
            handleWebLoginResult(resultCode, data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startWebLogin() {
        Intent intent = new Intent(this, WebLoginActivity.class);
        startActivityForResult(intent, WebLoginActivity.REQUEST_WEB_LOGIN);
    }

    private void handleWebLoginResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            appendLog("网页登录已取消或未返回数据。");
            setState("网页登录已取消");
            return;
        }
        String[] domains = data.getStringArrayExtra(WebLoginActivity.EXTRA_COOKIE_DOMAINS);
        String[] values = data.getStringArrayExtra(WebLoginActivity.EXTRA_COOKIE_VALUES);
        String lastUrl = data.getStringExtra(WebLoginActivity.EXTRA_LAST_URL);
        if (domains == null || values == null || domains.length == 0) {
            appendLog("网页登录未抓到任何 Cookie。");
            setState("网页登录未抓到 Cookie");
            return;
        }

        Map<String, String> cookies = new LinkedHashMap<>();
        for (int i = 0; i < domains.length && i < values.length; i++) {
            cookies.put(domains[i], values[i] == null ? "" : values[i]);
        }
        appendLog("网页登录返回，最后 URL: " + (lastUrl == null ? "?" : lastUrl));
        runWebCookieProbe(cookies);
    }

    private void buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xfff6f7f9);

        TextView title = new TextView(this);
        title.setText("三角洲特勤处监控 M1");
        title.setTextColor(0xff111827);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        stateView = new TextView(this);
        stateView.setTextColor(0xff334155);
        stateView.setTextSize(14);
        stateView.setPadding(0, dp(10), 0, dp(10));
        root.addView(stateView);

        loginButton = new Button(this);
        loginButton.setText("QQ 扫码登录并验证");
        loginButton.setAllCaps(false);
        loginButton.setOnClickListener(v -> startQqLogin(true));
        root.addView(loginButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        normalLoginButton = new Button(this);
        normalLoginButton.setText("QQ 普通登录对照");
        normalLoginButton.setAllCaps(false);
        normalLoginButton.setOnClickListener(v -> startQqLogin(false));
        root.addView(normalLoginButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        Button webLoginButton = new Button(this);
        webLoginButton.setText("网页登录(WebView) 验证");
        webLoginButton.setAllCaps(false);
        webLoginButton.setOnClickListener(v -> startWebLogin());
        root.addView(webLoginButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        Button clearButton = new Button(this);
        clearButton.setText("清空日志");
        clearButton.setAllCaps(false);
        clearButton.setOnClickListener(v -> logView.setText(""));
        root.addView(clearButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(0xff111827);
        logView.setTextSize(13);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(12), 0, 0);
        scrollView.addView(logView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private void setupLoginListener() {
        loginListener = new IUiListener() {
            @Override
            public void onComplete(Object response) {
                try {
                    QqLoginSession session = QqLoginSession.fromCallback(response);
                    session = session.withFallback(
                            tencent == null ? "" : tencent.getOpenId(),
                            tencent == null ? "" : tencent.getAccessToken()
                    );
                    appendLog("QQ 登录回调成功");
                    appendLog("QQ 回调字段: " + session.fieldSummary());
                    appendLog("openid length=" + session.openid.length());
                    appendLog("access_token/code length=" + session.accessTokenOrCode.length());
                    runAmsProbe(session);
                } catch (Exception error) {
                    appendLog("QQ 回调解析失败: " + error.getMessage());
                    setState("回调解析失败");
                }
            }

            @Override
            public void onError(UiError error) {
                appendLog("QQ 登录错误: code=" + error.errorCode
                        + " message=" + error.errorMessage
                        + " detail=" + error.errorDetail);
                setState("QQ 登录错误");
            }

            @Override
            public void onCancel() {
                appendLog("QQ 登录已取消");
                setState("QQ 登录已取消");
            }

            @Override
            public void onWarning(int code) {
                appendLog("QQ 登录警告: " + code);
            }
        };
    }

    private void initTencent() {
        String appId = BuildConfig.QQ_APP_ID;
        if (appId == null || appId.trim().isEmpty() || "YOUR_QQ_CONNECT_APP_ID".equals(appId)) {
            loginButton.setEnabled(false);
            normalLoginButton.setEnabled(false);
            setState("缺少 qq.appId，请配置 local.properties");
            appendLog("local.properties 示例: qq.appId=你的QQ互联AppID");
            return;
        }

        Tencent.setIsPermissionGranted(true);
        tencent = Tencent.createInstance(appId, getApplicationContext());
        setState("QQ AppID 已配置: " + appId + "，等待登录");
        appendLog("M1 使用 loginServerSide，回调 access_token 字段按官方文档视作 code。");
    }

    private void startQqLogin(boolean serverSideQrMode) {
        if (tencent == null) {
            initTencent();
        }
        if (tencent == null) {
            return;
        }
        setState("正在拉起 QQ 登录...");
        int code;
        if (serverSideQrMode) {
            getIntent().putExtra(AuthAgent.KEY_FORCE_QR_LOGIN, true);
            appendLog("调用 Tencent.loginServerSide(scope=get_simple_userinfo, qrcode=true)");
            code = tencent.loginServerSide(this, "get_simple_userinfo", loginListener, true);
            appendLog("loginServerSide 返回码: " + code);
        } else {
            getIntent().removeExtra(AuthAgent.KEY_FORCE_QR_LOGIN);
            appendLog("调用 Tencent.login(scope=get_simple_userinfo)");
            code = tencent.login(this, "get_simple_userinfo", loginListener);
            appendLog("login 返回码: " + code);
        }
    }

    private void runAmsProbe(QqLoginSession session) {
        setState("QQ 登录成功，正在验证 AMS...");
        final QqLoginSession initialSession = session;
        executor.submit(() -> {
            try {
                QqLoginSession workingSession = initialSession;
                if ((workingSession.openid == null || workingSession.openid.isEmpty())
                        && workingSession.accessTokenOrCode != null
                        && !workingSession.accessTokenOrCode.isEmpty()) {
                    appendLog("QQ 回调缺少 openid，尝试用 access_token 查询 graph.qq.com/oauth2.0/me。");
                    try {
                        String fetchedOpenid = amsApiClient.fetchQqOpenid(workingSession.accessTokenOrCode);
                        workingSession = workingSession.withFallback(fetchedOpenid, "");
                        appendLog("QQ openid 查询成功，openid length=" + workingSession.openid.length());
                    } catch (Exception error) {
                        appendLog("QQ openid 查询失败: " + error.getMessage());
                        appendLog("当前扫码 token 不是 QQ OpenAPI access_token；请在测试机安装并登录 QQ 后走普通登录，或替换为正式 QQ 互联 AppID。");
                    }
                }

                String exchangeBody = amsApiClient.exchangeQqServerSideCode(workingSession, BuildConfig.QQ_APP_ID);
                appendLog("userLoginSvr 响应:");
                appendLog(SecretRedactor.redact(exchangeBody, workingSession));

                AmsCredential exchanged = amsApiClient.tryCredentialFromUserLoginResponse(exchangeBody, BuildConfig.QQ_APP_ID);
                if (exchanged != null && exchanged.isComplete()) {
                    appendLog("userLoginSvr 提取到 AMS credential，开始请求特勤处。");
                    AmsProbeResult exchangedResult = amsApiClient.fetchCrafting(exchanged);
                    appendCraftingResult("userLoginSvr credential", exchangedResult);
                    if (exchangedResult.isSuccess()) {
                        setState("AMS 验证成功");
                        return;
                    }
                } else {
                    appendLog("userLoginSvr 未提取到可用 AMS credential。");
                }

                appendLog("开始直接用 QQ SDK 回调 token/code 构造 qc Cookie 请求特勤处。");
                AmsProbeResult directResult = amsApiClient.fetchCrafting(workingSession.asDirectQqCredential(BuildConfig.QQ_APP_ID));
                appendCraftingResult("direct QQ SDK credential", directResult);
                setState(directResult.isSuccess() ? "AMS 验证成功" : "AMS 验证未通过");
            } catch (Exception error) {
                appendLog("AMS 验证异常: " + error.getClass().getSimpleName() + ": " + error.getMessage());
                setState("AMS 验证异常");
            }
        });
    }

    /**
     * 办法 A 验证：用 WebView 抓到的 Cookie 调 AMS。
     * 路径1：原样用 comm.ams.game.qq.com 域 Cookie（最忠实）。
     * 路径2：解析出四元组构造 qc Cookie。
     */
    private void runWebCookieProbe(Map<String, String> cookiesByDomain) {
        setState("网页 Cookie 已抓取，正在验证 AMS...");
        executor.submit(() -> {
            try {
                // 脱敏 dump 各域 Cookie 字段长度
                for (Map.Entry<String, String> entry : cookiesByDomain.entrySet()) {
                    String domain = entry.getKey();
                    String cookie = entry.getValue();
                    if (cookie.isEmpty()) {
                        continue;
                    }
                    Map<String, String> kv = AmsApiClient.parseCookieString(cookie);
                    StringBuilder summary = new StringBuilder();
                    for (Map.Entry<String, String> field : kv.entrySet()) {
                        if (summary.length() > 0) {
                            summary.append(", ");
                        }
                        summary.append(field.getKey())
                                .append("(len=")
                                .append(field.getValue() == null ? 0 : field.getValue().length())
                                .append(")");
                    }
                    appendLog("[" + domain + "] " + summary);
                }

                // 找 AMS 目标域的 Cookie
                String amsCookie = pickAmsCookie(cookiesByDomain);
                if (amsCookie == null || amsCookie.isEmpty()) {
                    appendLog("未抓到 ams.game.qq.com 相关域 Cookie；尝试合并所有域 Cookie 再试。");
                    amsCookie = mergeAllCookies(cookiesByDomain);
                }

                // 路径1：原样 Cookie
                if (!amsCookie.isEmpty()) {
                    appendLog("路径1：用原始 Cookie 请求特勤处接口（不改写）。");
                    AmsProbeResult rawResult = amsApiClient.fetchCraftingWithRawCookie(amsCookie);
                    appendCraftingResult("web raw cookie", rawResult);
                    if (rawResult.isSuccess()) {
                        setState("网页登录验证成功（原始 Cookie）");
                        return;
                    }
                } else {
                    appendLog("路径1：无可用原始 Cookie。");
                }

                // 路径2：构造 qc 四元组
                appendLog("路径2：尝试从所有 Cookie 中解析 openid/acctype/appid/access_token 四元组。");
                AmsCredential parsed = AmsApiClient.fromCookieString(mergeAllCookies(cookiesByDomain));
                appendLog("解析结果: openid(len=" + parsed.openid.length()
                        + ") appid(len=" + parsed.appid.length()
                        + ") access_token(len=" + parsed.accessToken.length() + ")");
                if (parsed.isComplete()) {
                    AmsProbeResult parsedResult = amsApiClient.fetchCrafting(parsed);
                    appendCraftingResult("web parsed qc cookie", parsedResult);
                    if (parsedResult.isSuccess()) {
                        setState("网页登录验证成功（构造 qc）");
                        return;
                    }
                } else {
                    appendLog("Cookie 中未同时出现 openid/appid/access_token，无法构造 qc。");
                }

                // 路径3（M2 命门）：pvp.qq.com ptlogin Cookie + g_tk
                runPtloginGtkProbe(cookiesByDomain);
            } catch (Exception error) {
                appendLog("网页 Cookie 验证异常: " + error.getClass().getSimpleName() + ": " + error.getMessage());
                setState("网页 Cookie 验证异常");
            }
        });
    }

    private static String pickAmsCookie(Map<String, String> cookiesByDomain) {
        for (String key : new String[]{"comm.ams.game.qq.com", "ams.game.qq.com", "game.qq.com"}) {
            String cookie = cookiesByDomain.get(key);
            if (cookie != null && !cookie.isEmpty()) {
                return cookie;
            }
        }
        return "";
    }

    private static String mergeAllCookies(Map<String, String> cookiesByDomain) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String cookie : cookiesByDomain.values()) {
            if (cookie == null || cookie.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String> field : AmsApiClient.parseCookieString(cookie).entrySet()) {
                merged.putIfAbsent(field.getKey(), field.getValue());
            }
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    /**
     * M2 命门验证：用 pvp.qq.com 登录后抓到的 ptlogin Cookie（p_skey/skey）
     * 算出 g_tk，走 GET + Cookie + g_tk 通道请求特勤处。
     *
     * 关键：ptlogin2.qq.com 域 Cookie 是登录态的真正归属域，优先单域取，
     * 绕开 mergeAllCookies 的同名字段覆盖坑。
     */
    private void runPtloginGtkProbe(Map<String, String> cookiesByDomain) {
        String ptloginCookie = pickPtloginCookie(cookiesByDomain);
        String cookieSource = ptloginCookie.isEmpty() ? "(none)" : "(ptlogin2.qq.com 单域)";

        if (ptloginCookie.isEmpty()) {
            appendLog("路径3：未抓到 ptlogin2.qq.com 域 Cookie，回退到合并所有域 Cookie 取 skey。");
            ptloginCookie = mergeAllCookies(cookiesByDomain);
            cookieSource = "(合并所有域)";
        }
        if (ptloginCookie.isEmpty()) {
            appendLog("路径3：无任何 Cookie，跳过 g_tk 验证。");
            setState("网页登录验证未通过");
            return;
        }

        String skey = G_tkCalculator.pickSkeyFromCookie(ptloginCookie);
        if (skey.isEmpty()) {
            appendLog("路径3：Cookie " + cookieSource + " 中没有 p_skey 也没有 skey，无法算 g_tk。");
            appendLog("  说明 pvp.qq.com 登录未产生 ptlogin 票据，或登录页/域不对。");
            setState("网页登录验证未通过");
            return;
        }

        int gtk = G_tkCalculator.calc(skey);
        appendLog("路径3：用 " + cookieSource + " 的 skey(len=" + skey.length() + ") 算出 g_tk=" + gtk);
        appendLog("  GET 特勤处接口 + 桌面 UA + Referer(pvp.qq.com) + Cookie + g_tk");

        try {
            AmsProbeResult gtkResult = amsApiClient.fetchCraftingWithCookieAndGtk(ptloginCookie, gtk);
            // 命门验证必须先 dump 原始响应，失败时人眼判断是鉴权问题还是参数问题。
            appendLog("路径3 原始响应(ret=" + gtkResult.ret + ", iRet=" + gtkResult.iRet + "): "
                    + truncateForLog(gtkResult.rawBody));
            appendCraftingResult("web cookie+gtk", gtkResult);
            setState(gtkResult.isSuccess() ? "网页登录验证成功（Cookie+g_tk）" : "网页登录验证未通过");
        } catch (Exception error) {
            appendLog("路径3 异常: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            setState("网页登录验证未通过");
        }
    }

    /** 优先取 ptlogin2.qq.com 单域 Cookie；该域是 p_skey/skey 的真正归属域。 */
    private static String pickPtloginCookie(Map<String, String> cookiesByDomain) {
        for (String key : new String[]{"ptlogin2.qq.com", "pvp.qq.com"}) {
            String cookie = cookiesByDomain.get(key);
            if (cookie != null && !cookie.isEmpty()) {
                return cookie;
            }
        }
        return "";
    }

    /** 把长响应体截断到适合日志显示的长度，避免刷屏。 */
    private static String truncateForLog(String body) {
        if (body == null) {
            return "(null)";
        }
        return body.length() > 400 ? body.substring(0, 400) + "…(len=" + body.length() + ")" : body;
    }

    private void appendCraftingResult(String source, AmsProbeResult result) {
        appendLog(source + ": ret=" + result.ret + " iRet=" + result.iRet + " msg=" + result.message);
        if (!result.isSuccess()) {
            appendLog(source + " 未返回有效 placeData。");
            return;
        }
        appendLog(source + " placeData=" + result.snapshot.stations.size());
        for (CraftingStation station : result.snapshot.stations) {
            appendLog("- " + station.placeName
                    + " | " + emptyDash(station.itemName)
                    + " | 剩余 " + emptyDash(station.remainingSeconds)
                    + "s | 完成 " + formatEpoch(station.finishAtEpochSeconds));
        }
    }

    private void setState(String message) {
        runOnUiThread(() -> stateView.setText(message));
    }

    private void appendLog(String line) {
        runOnUiThread(() -> {
            String prefix = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
            logView.append("[" + prefix + "] " + line + "\n");
        });
    }

    private String formatEpoch(Long epochSeconds) {
        if (epochSeconds == null) {
            return "-";
        }
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.format(new Date(epochSeconds * 1000L));
    }

    private String emptyDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
