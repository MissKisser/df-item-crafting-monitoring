package com.local.dfcraftmonitor;

/**
 * QQ g_tk（CSRF token）计算器。
 *
 * 腾讯 IEG/AMS 等 H5 接口用 g_tk 做 CSRF 校验，它由 skey 或 p_skey
 * 经 {@code hash32_s} 算法派生。算法是公开的前端逻辑（qq.com 站点 js 里的 getACSRFToken）。
 *
 * M2 spike 命门验证：用 pvp.qq.com 登录后抓到的 p_skey 算出 g_tk，
 * 配合 Cookie 头调用特勤处接口。
 */
public final class G_tkCalculator {

    private G_tkCalculator() {
    }

    /**
     * 由 skey/p_skey 计算 g_tk。
     *
     * @param skey 登录后 ptlogin 下发的 skey 或 p_skey（不可为 null）
     * @return g_tk 整数值；skey 为空串时返回 54048（QQ 旧算法对空串的兜底值）
     */
    public static int calc(String skey) {
        if (skey == null || skey.isEmpty()) {
            return 54048;
        }
        long hash = 5381;
        for (int i = 0; i < skey.length(); i++) {
            hash += (hash << 5) + skey.charAt(i);
        }
        return (int) (hash & 0x7FFFFFFF);
    }

    /**
     * 从 "k1=v1; k2=v2" 形式的 Cookie 串里优先取 p_skey，缺失时回退到 skey，
     * 再缺失返回空串。
     */
    public static String pickSkeyFromCookie(String cookieHeader) {
        String pSkey = AmsApiClient.parseCookieString(cookieHeader).get("p_skey");
        if (pSkey != null && !pSkey.isEmpty()) {
            return pSkey;
        }
        String skey = AmsApiClient.parseCookieString(cookieHeader).get("skey");
        return skey == null ? "" : skey;
    }
}
