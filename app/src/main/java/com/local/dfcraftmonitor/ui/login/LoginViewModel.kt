package com.local.dfcraftmonitor.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.WebSessionCleaner
import com.local.dfcraftmonitor.data.backend.LocalDashboardData
import com.local.dfcraftmonitor.data.model.AmsCredential
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.remote.AmsCraftingParser
import com.local.dfcraftmonitor.data.remote.CookieUtils
import com.local.dfcraftmonitor.data.repository.CraftingRepository
import com.local.dfcraftmonitor.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录 ViewModel。支持多账号模式。
 *
 * - 首次登录（addMode=false）：解析 Cookie → 保存账号 → 启动同步 → 拉取资料
 * - 新增账号（addMode=true）：先调用 [prepareFreshLogin] 清空 WebView Cookie → 扫码 →
 *   检查 openid 去重（已存在则停留在登录页提示用户） → 保存 → 拉取资料
 *
 * 登录成功后异步拉取仪表盘数据填充昵称/头像/大区，更新 AccountEntry 和 WidgetCache。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val workScheduler: WorkScheduler,
    private val craftingRepository: CraftingRepository,
    private val widgetCache: WidgetCache,
    private val webSessionCleaner: WebSessionCleaner,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.LoggingIn)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _freshLoginReady = MutableStateFlow(false)
    /** addMode 下 Cookie 清空完成、可以挂载 WebView 的信号。 */
    val freshLoginReady: StateFlow<Boolean> = _freshLoginReady.asStateFlow()

    /** 新增账号模式标记，由导航设置。 */
    var isAddingAccount: Boolean = false

    /**
     * addMode 下进入页面时调用：清空 WebView Cookie / 缓存 / DOM Storage，
     * 防止 WebView 用上一账号的缓存自动登录、跳过扫码。
     * 清空完成后 [freshLoginReady] 置 true，UI 此时再挂载 WebView。
     */
    fun prepareFreshLogin() {
        if (_freshLoginReady.value) return
        viewModelScope.launch {
            runCatching { webSessionCleaner.clear() }
            _state.value = UiState.LoggingIn
            _freshLoginReady.value = true
        }
    }

    fun onCookiesHarvested(cookies: Map<String, String>): AmsCredential? {
        Log.d("LoginViewModel", "onCookiesHarvested called, domains=${cookies.keys}")
        val cookieSource = listOf(
            cookies["pvp.qq.com"].orEmpty(),
            cookies["comm.ams.game.qq.com"].orEmpty(),
            cookies["game.qq.com"].orEmpty(),
            cookies.values.joinToString("; "),
        ).firstOrNull { source ->
            val parsed = CookieUtils.parseCookieString(source)
            parsed["openid"].orEmpty().isNotBlank() &&
                parsed["acctype"].orEmpty().isNotBlank() &&
                parsed["appid"].orEmpty().isNotBlank() &&
                parsed["access_token"].orEmpty().isNotBlank()
        }.orEmpty()
        val parsed = CookieUtils.parseCookieString(cookieSource)
        val credential = AmsCredential.create(
            openid = parsed["openid"].orEmpty(),
            acctype = parsed["acctype"].orEmpty(),
            appid = parsed["appid"].orEmpty(),
            accessToken = parsed["access_token"].orEmpty(),
        )
        Log.d("LoginViewModel", "credential.isComplete()=${credential.isComplete()}, openid=${parsed["openid"].orEmpty().take(8)}, acctype=${parsed["acctype"].orEmpty()}, appid=${parsed["appid"].orEmpty().take(4)}, token=${parsed["access_token"].orEmpty().take(4)}")
        return if (credential.isComplete()) {
            val entry = sessionHolder.set(credential)
            widgetCache.setCurrentAccountId(entry.accountId)
            workScheduler.start()
            _state.value = UiState.LoggedIn
            Log.d("LoginViewModel", "State set to LoggedIn")
            fetchProfileInBackground(credential, entry.accountId)
            credential
        } else {
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

    private fun fetchProfileInBackground(credential: AmsCredential, accountId: String) {
        viewModelScope.launch {
            craftingRepository.fetchDashboard(credential)
                .onSuccess { dashboard ->
                    val profile = dashboard.profile
                    sessionHolder.updateCurrentProfile(
                        nickname = profile.nickname,
                        avatarUrl = profile.avatarUrl,
                        areaName = profile.areaName,
                    )
                    widgetCache.updateFromDashboard(accountId, dashboard)
                }
        }
    }

    sealed interface UiState {
        data object LoggingIn : UiState
        data object LoggedIn : UiState
        data class Failed(val reason: String) : UiState
    }
}
