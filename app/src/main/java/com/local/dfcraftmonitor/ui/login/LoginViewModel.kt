package com.local.dfcraftmonitor.ui.login

import androidx.lifecycle.ViewModel
import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.remote.CookieUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 登录 ViewModel。WebView 自动判定登录成功后回调 [onLoginSuccess]，
 * 这里把 cookies 解析成 [AmsCredential] 存入 [SessionHolder]，并发射
 * [UiState.LoggedIn] 通知上层跳转。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.LoggingIn)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * WebView 自动调用（或未来手动重试入口）。
     * 返回构造好的 credential（同时也存进了 SessionHolder）。
     */
    fun onCookiesHarvested(cookies: Map<String, String>): AmsCredential? {
        // AMS 接口真正认账的是 comm.ams.game.qq.com 域的 qc 四元组
        // （M2 实测路径1 成功链路）。优先取 AMS 域，回退到合并所有域。
        val amsDomainCookie = cookies["comm.ams.game.qq.com"].orEmpty()
        val cookieSource = if (amsDomainCookie.isNotEmpty()) {
            amsDomainCookie
        } else {
            // 合并所有域（仅在 AMS 域为空时 fallback；spike 已知有同名字段覆盖风险，
            // 但对 login 这步只是临时 fallback，主调用只在 AMS 域取）
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
            _state.value = UiState.LoggedIn
            credential
        } else {
            _state.value = UiState.Failed("Cookie 缺少 openid/acctype/appid/access_token 之一")
            null
        }
    }

    sealed interface UiState {
        data object LoggingIn : UiState
        data object LoggedIn : UiState
        data class Failed(val reason: String) : UiState
    }
}
