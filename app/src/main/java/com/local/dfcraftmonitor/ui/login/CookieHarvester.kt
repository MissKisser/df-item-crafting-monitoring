package com.local.dfcraftmonitor.ui.login

import android.net.Uri
import android.webkit.CookieManager

/**
 * 从 Android WebView 的 CookieManager 抓取多域 Cookie。
 *
 * 为什么必须按域名逐个 getCookie：CookieManager 按 host 隔离 Cookie，
 * 一次 getCookie 只能拿到目标 URL 域名匹配的 Cookie。spike 反复验证过的
 * 6 个域一个不能少——少了就抓不全票据。
 *
 * 何时认为"登录成功"：
 *   1. ptlogin2.qq.com 域出现 p_skey（QQ 登录态核心票据），或
 *   2. df.qq.com、iu.qq.com、pvp.qq.com、game.qq.com 或 comm.ams.game.qq.com
 *      域同时出现 openid + access_token（AMS 接口认可的四元组）
 *
 * 纯逻辑：构造接受任意 CookieManager 接口（用接口而不直接持有，便于单测）。
 */
class CookieHarvester(
    private val cookieManager: android.webkit.CookieManager =
        android.webkit.CookieManager.getInstance(),
) {

    fun harvest(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (url in COOKIE_URLS) {
            val cookie = cookieManager.getCookie(url) ?: ""
            val host = Uri.parse(url).host.orEmpty()
            if (host.isNotEmpty()) {
                result[host] = cookie
            }
        }
        return result
    }

    /**
     * 判断 [cookies] 是否代表一次完整登录。
     * 见类注释的判定规则。
     */
    fun isLoginComplete(cookies: Map<String, String>): Boolean {
        val ptlogin = cookies["ptlogin2.qq.com"].orEmpty()
        if ("p_skey" in ptloginParseKeys(ptlogin)) return true

        val amsCookie = cookies["df.qq.com"].orEmpty() +
            ";" + cookies["iu.qq.com"].orEmpty() +
            ";" + cookies["milo.qq.com"].orEmpty() +
            ";" + cookies["apps.game.qq.com"].orEmpty() +
            ";" + cookies["ams.game.qq.com"].orEmpty() +
            ";" + cookies["game.qq.com"].orEmpty() +
            ";" + cookies["comm.ams.game.qq.com"].orEmpty() +
            ";" + cookies["pvp.qq.com"].orEmpty()
        val amsKeys = ptloginParseKeys(amsCookie)
        return "openid" in amsKeys && "access_token" in amsKeys
    }

    /**
     * 从 cookie 串里取出所有字段名（不包含值）用于"是否出现"判断。
     */
    private fun ptloginParseKeys(cookieHeader: String): Set<String> =
        cookieHeader.split(';')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else part.substring(0, eq).trim().takeIf { it.isNotEmpty() }
            }
            .toSet()

    companion object {
        /**
         * 必须逐个 getCookie 的域。spike 验证：
         * df.qq.com 必须（当前三角洲官方 WebMP 登录页）
         * iu.qq.com 必须（官方 WebMP 微信登录 gameDomain）
         * pvp.qq.com 保留（历史 PVP 登录页）
         * comm.ams.game.qq.com 必须（AMS 接口四元组的归属域）
         * ptlogin2.qq.com 必须（p_skey 等登录态票据的归属域）
         * 其他域虽然多数情况为空，但加上避免漏抓。
         */
        val COOKIE_URLS: Array<String> = arrayOf(
            "https://df.qq.com/",
            "https://iu.qq.com/",
            "https://milo.qq.com/",
            "https://apps.game.qq.com/",
            "https://pvp.qq.com/",
            "https://comm.ams.game.qq.com/ide/",
            "https://ams.game.qq.com/",
            "https://graph.qq.com/",
            "https://ptlogin2.qq.com/",
            "https://game.qq.com/",
        )
    }
}
