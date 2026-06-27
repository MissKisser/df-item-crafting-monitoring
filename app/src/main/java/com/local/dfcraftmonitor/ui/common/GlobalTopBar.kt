package com.local.dfcraftmonitor.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.local.dfcraftmonitor.data.monitor.RefreshState

/**
 * 全局顶部 AppBar（spec "全局刷新"）。
 *
 * 由 [com.local.dfcraftmonitor.MainActivity] 挂在 NavHost 之上，
 * 因此**任何路由**（Home / Settings / Privacy / Login-Add）都能看到一致的标题
 * 与右上角刷新按钮。子页面若需要返回按钮或额外 action，通过 [navigationIcon] /
 * [actions] 传入。
 *
 * 刷新按钮行为：
 *  - 点击 → 调 [onRefresh]
 *  - 状态为 [RefreshState.Running] → 图标旋转动画 + 按钮禁用
 *  - 状态为 [RefreshState.Failed] → 图标变红，1.5s 后自动复位（由调用方决定是否 toast）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalTopBar(
    title: String,
    refreshState: RefreshState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        navigationIcon = {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = {
            RefreshIconButton(state = refreshState, onClick = onRefresh)
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        windowInsets = WindowInsets.statusBars,
    )
}

/**
 * 刷新图标按钮：状态驱动旋转动画与颜色。
 *
 * 动画细节：
 *  - Running：使用 InfiniteTransition 以 LinearEasing 0→360° 无限循环，800ms 一圈。
 *    LinearEasing + 稳定时长让旋转看起来"稳定运转"而非加速抖动。
 *  - Idle：rotation=0，无动画开销。
 *  - Failed：rotation=0，颜色切换到 error 色；3s 后状态机会被 GlobalRefreshController
 *    复位为 Idle（一次失败不会永久红）。
 */
@Composable
fun RefreshIconButton(
    state: RefreshState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "refresh-spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
        ),
        label = "refresh-rotation",
    )

    val isRunning = state is RefreshState.Running
    val tint = when (state) {
        is RefreshState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    IconButton(onClick = onClick, enabled = !isRunning, modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = when (state) {
                is RefreshState.Failed -> "刷新失败：${state.reason}"
                RefreshState.Running -> "正在刷新"
                RefreshState.Idle -> "刷新"
            },
            tint = tint,
            modifier = if (isRunning) Modifier.rotate(rotation) else modifier,
        )
    }
}