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
import java.util.Locale;
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
        }
        super.onActivityResult(requestCode, resultCode, data);
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
