package com.local.dfcraftmonitor.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.dfcraftmonitor.ui.settings.SettingsViewModel

/**
 * 隐私说明页（spec 10.3）。
 *
 * 轻量级：仅在设置页展示，不做首屏强制弹窗。
 * 进入时标记 welcomeShown = true（UserPreferences）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    // 进入隐私页即标记已读
    LaunchedEffect(Unit) {
        settingsViewModel.setWelcomeShown(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私说明") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PrivacySection("数据收集")
            PrivacyBody(
                "本应用仅通过腾讯 AMS 接口获取您在《三角洲行动》游戏中的特勤处制造数据" +
                    "（工位状态、物品名称、剩余时间等），不收集任何其他个人信息。",
            )

            Spacer(modifier = Modifier.height(16.dp))
            PrivacySection("数据存储")
            PrivacyBody(
                "所有数据仅存储在您的设备本地（DataStore），不会上传至任何第三方服务器。" +
                    "应用重启后登录凭据不会持久化，需重新登录。",
            )

            Spacer(modifier = Modifier.height(16.dp))
            PrivacySection("数据传输")
            PrivacyBody(
                "网络请求仅发往腾讯官方域名（pvp.qq.com），使用 HTTPS 加密传输。" +
                    "Cookie 仅用于接口鉴权，不会分享给任何第三方。",
            )

            Spacer(modifier = Modifier.height(16.dp))
            PrivacySection("后台行为")
            PrivacyBody(
                "应用使用 WorkManager 周期性（约 15 分钟）轮询制造状态，" +
                    "用于在制造完成时发送本地通知提醒。此行为可在设置中关闭。",
            )

            Spacer(modifier = Modifier.height(16.dp))
            PrivacySection("权限使用")
            PrivacyBody(
                "• 互联网权限：访问腾讯 AMS 接口\n" +
                    "• 通知权限：发送制造完成提醒（Android 13+ 需用户授权）\n" +
                    "• 开机启动权限：设备重启后恢复周期轮询",
            )

            Spacer(modifier = Modifier.height(16.dp))
            PrivacySection("数据删除")
            PrivacyBody(
                "您可随时在设置页点击「清除数据」删除所有本地缓存和偏好。" +
                    "卸载应用将自动清除全部数据。",
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "最后更新：2026-06",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacySection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PrivacyBody(content: String) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}
