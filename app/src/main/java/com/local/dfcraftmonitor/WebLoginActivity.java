package com.local.dfcraftmonitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M1 办法 A 验证页：内嵌官方网页登录，抓取 WebView 自己的 Cookie 后回传。
 *
 * 设计原则：
 * - 登录走官方页面、官方流程，APP 只读本 WebView 自己的存储，不碰微信/QQ 沙箱，无 root。
 * - Cookie 原文只在内存里回传给 AMS 验证；界面只显示字段长度。
 */
public final class WebLoginActivity extends Activity {

    /** startActivityForResult 请求码。 */
    public static final int REQUEST_WEB_LOGIN = 0x571;

    /** Intent extra：回传的域名 -> cookie 串 map（序列化为两组平行数组）。 */
    public static final String EXTRA_COOKIE_DOMAINS = "cookieDomains";
    public static final String EXTRA_COOKIE_VALUES = "cookieValues";
    public static final String EXTRA_LAST_URL = "lastUrl";

    /** 候选域名：登录后对每个域名调 getCookie，覆盖 AMS/QQ/微信常见域。 */
    private static final String[] COOKIE_URLS = new String[]{
            "https://comm.ams.game.qq.com/ide/",
            "https://ams.game.qq.com/",
            "https://df.qq.com/",
            "https://graph.qq.com/",
            "https://ptlogin2.qq.com/",
            "https://game.qq.com/",
    };

    /** 桌面 Chrome UA，避免被 QQ 判成 WebView 做风控。 */
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private WebView webView;
    private EditText urlField;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        // 确保 Cookie 写入即时生效。
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().flush();

        loadUrl(getInitialUrl());
    }

    private String getInitialUrl() {
        String url = BuildConfig.DF_WEB_LOGIN_URL;
        if (url == null || url.trim().isEmpty()) {
            url = "https://df.qq.com/cp/record202410ver/";
        }
        return url;
    }

    private void buildUi() {
        int pad = dp(12);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xfff6f7f9);

        // 顶部：地址栏 + 跳转
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        urlField = new EditText(this);
        urlField.setText(getInitialUrl());
        urlField.setTextSize(13);
        urlField.setSingleLine(true);
        urlField.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(urlField, fieldLp);

        Button goButton = new Button(this);
        goButton.setText("跳转");
        goButton.setAllCaps(false);
        goButton.setOnClickListener(v -> {
            String text = urlField.getText().toString().trim();
            if (text.isEmpty()) {
                toast("请输入网址");
                return;
            }
            loadUrl(text);
        });
        bar.addView(goButton);

        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 状态行
        statusView = new TextView(this);
        statusView.setTextColor(0xff334155);
        statusView.setTextSize(12);
        statusView.setPadding(0, dp(8), 0, dp(8));
        root.addView(statusView);

        // 抓取并验证按钮
        Button grabButton = new Button(this);
        grabButton.setText("抓取 Cookie 并验证 AMS");
        grabButton.setAllCaps(false);
        grabButton.setOnClickListener(v -> finishWithCookies());
        root.addView(grabButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));

        // WebView
        webView = new WebView(this);
        configureWebView();
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // pvp.qq.com 登录按钮用 window.open() 弹 ptlogin 登录窗：
        // 必须开多窗口 + 提供 WebChromeClient.onCreateWindow，否则 window.open 返回 null，点击无反应。
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(DESKTOP_UA);

        // QQ/微信登录链路依赖第三方 Cookie，必须开。
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 同步 Cookie 到全局，保证 getCookie 能读到最新值。
                CookieManager.getInstance().flush();
                urlField.setText(url);
                setStatus("已加载: " + shorten(url) + "\n" + cookieSummary());
            }
        });

        // 处理 window.open：ptlogin 登录窗通过 window.open() 打开。
        // 标准做法：创建临时 transport WebView 满足 window.open 协议，
        // 在它的 WebViewClient.shouldOverrideUrlLoading 里把首个 URL 转给主 webView。
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final WebView transport = new WebView(view.getContext());
                transport.getSettings().setJavaScriptEnabled(true);
                transport.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView tv, String url) {
                        // ptlogin 登录窗的第一个 URL：在主视图加载它。
                        appendStatus("登录窗口: " + shorten(url));
                        webView.loadUrl(url);
                        return true;
                    }
                });
                WebView.WebViewTransport transportWrapper = (WebView.WebViewTransport) resultMsg.obj;
                transportWrapper.setWebView(transport);
                resultMsg.sendToTarget();
                return true;
            }
        });
    }

    private void appendStatus(String text) {
        setStatus(statusView.getText() + "\n" + text);
    }

    private void loadUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        urlField.setText(url);
        webView.loadUrl(url);
        setStatus("加载中: " + shorten(url));
    }

    /** 收集所有候选域名的 Cookie，回传给 MainActivity。 */
    private void finishWithCookies() {
        CookieManager.getInstance().flush();
        Map<String, String> cookies = collectCookies();

        Intent data = new Intent();
        String[] domains = new String[cookies.size()];
        String[] values = new String[cookies.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            domains[i] = entry.getKey();
            values[i] = entry.getValue();
            i++;
        }
        data.putExtra(EXTRA_COOKIE_DOMAINS, domains);
        data.putExtra(EXTRA_COOKIE_VALUES, values);
        data.putExtra(EXTRA_LAST_URL, webView.getUrl());

        setResult(RESULT_OK, data);
        finish();
    }

    private Map<String, String> collectCookies() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String url : COOKIE_URLS) {
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie == null) {
                cookie = "";
            }
            map.put(Uri.parse(url).getHost(), cookie);
        }
        return map;
    }

    /** 脱敏摘要：只显示各域 Cookie 的字段长度。 */
    private String cookieSummary() {
        StringBuilder builder = new StringBuilder("当前 Cookie：");
        boolean any = false;
        for (String url : COOKIE_URLS) {
            String cookie = CookieManager.getInstance().getCookie(url);
            String host = Uri.parse(url).getHost();
            if (!TextUtils.isEmpty(cookie)) {
                any = true;
                builder.append("\n").append(host).append(": ");
                builder.append(fieldLengthSummary(cookie));
            }
        }
        if (!any) {
            builder.append("\n（暂无，完成登录后会自动刷新）");
        }
        return builder.toString();
    }

    /** 把 "k1=v1; k2=v2" 解析成 "k1(len=N), k2(len=N)"。 */
    private String fieldLengthSummary(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return "(empty)";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            String key = eq >= 0 ? trimmed.substring(0, eq) : trimmed;
            String value = eq >= 0 ? trimmed.substring(eq + 1) : "";
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(key).append("(len=").append(value.length()).append(")");
        }
        return builder.toString();
    }

    private void setStatus(String text) {
        runOnUiThread(() -> statusView.setText(text));
    }

    private String shorten(String url) {
        if (url == null) {
            return "";
        }
        return url.length() > 60 ? url.substring(0, 60) + "…" : url;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
