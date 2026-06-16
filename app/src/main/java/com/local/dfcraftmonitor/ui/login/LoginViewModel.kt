package com.local.dfcraftmonitor.ui.login

import androidx.lifecycle.ViewModel
import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.remote.CookieUtils
import com.local.dfcraftmonitor.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 登录 ViewModel。WebView 自动判定登录成功后回调 [onLoginSuccess]，
 * 这里把 cookies 解析成 [AmsCredential] 存入 [SessionHolder]，并发射
 * [UiState.LoggedIn] 通知上层跳转。
 *
 * 登录成功时也会启动 WorkManager 周期同步（spec M2）。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.LoggingIn)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * WebView 自动调用（或未来手动重试入口）。
     * 返回构造好的 credential（同时也存进了 SessionHolder）。
     */
    fun onCookiesHarvested(cookies: Map<String, String>): AmsCredential? {
        val amsDomainCookie = cookies["comm.ams.game.qq.com"].orEmpty()
        val cookieSource = if (amsDomainCookie.isNotEmpty()) {
            amsDomainCookie
        } else {
            cookies.values.joinToString("; ")
        }
        val parsed = CookieUtils.parseCookieString(cookieSource)
        val credential = AmsCredential.create(
            openid = parsed["openid"].orEmpty(),
            acctype = parsed["acctype"].orEmpty(),
            appid = parsed["appid"].orEmpty(),
            accessToken = parsed["access_token"].orEmpty(),
        )
        return if (credential.isComplete()) {
            sessionHolder.set(credential)
            // 登录成功 → 启动 WorkManager 周期同步（spec M2）
            workScheduler.start()
            _state.value = UiState.LoggedIn
            credential
        } else {
            // 诊断信息：显示哪些字段缺失 + 哪些域有 Cookie
            val missing = buildList {
                if (parsed["openid"].isNullOrEmpty()) add("openid")
                if (parsed["acctype"].isNullOrEmpty()) add("acctype")
                if (parsed["appid"].isNullOrEmpty()) add("appid")
                if (parsed["access_token"].isNullOrEmpty()) add("access_token")
            }
            val domains = cookies.keys.joinToString(", ")
            _state.value = UiState.Failed(
                "Cookie 缺少字段：${missing.joinToString(", ")}。" +
                    "已获取域：$domains。" +
                    "请确认扫码后等待页面跳转完成"
            )
            null
        }
    }

    sealed interface UiState {
        data object LoggingIn : UiState
        data object LoggedIn : UiState
        data class Failed(val reason: String) : UiState
    }
}
