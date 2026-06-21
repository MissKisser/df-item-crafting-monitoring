package com.local.dfcraftmonitor.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.dfcraftmonitor.ui.common.SectionHeader
import com.local.dfcraftmonitor.ui.common.TacticalPanel
import com.local.dfcraftmonitor.ui.settings.SettingsViewModel

/**
 * 隐私说明页。
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
    LaunchedEffect(Unit) {
        settingsViewModel.setWelcomeShown(true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "隐私说明",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "intro") {
                SectionHeader("数据与权限", "三角洲助手承诺本地优先")
            }
            item(key = "intro-card") {
                TacticalPanel {
                    Text(
                        text = "本应用所有账号数据、Cookie、缓存均存储在设备本地，" +
                            "我们不收集任何账号信息、不上传任何业务数据。",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item(key = "section-storage") { SectionHeader("本地存储") }
            item(key = "storage-card") {
                TacticalPanel {
                    BulletLine("账号 Cookie：仅用于维持官方页面登录会话。")
                    BulletLine("制造快照：缓存最近一次拉取数据，用于离线浏览与差异。")
                    BulletLine("Widget 缓存：用于桌面小组件展示。")
                    BulletLine("通知偏好：用于控制是否在后台发送完成提醒。")
                }
            }

            item(key = "section-permission") { SectionHeader("权限说明") }
            item(key = "perm-internet") {
                BulletCard("网络权限", "用于登录页 WebView 加载与制造数据同步。")
            }
            item(key = "perm-notif") {
                BulletCard("通知权限", "可选；用于在制造完成时发送本地提醒。")
            }
            item(key = "perm-boot") {
                BulletCard("开机启动", "用于在设备重启后恢复后台同步。")
            }

            item(key = "section-third-party") { SectionHeader("第三方依赖") }
            item(key = "third-party-card") {
                TacticalPanel {
                    BulletLine("OkHttp / Retrofit：网络请求")
                    BulletLine("Hilt：依赖注入")
                    BulletLine("WorkManager：后台任务调度")
                    BulletLine("Jetpack Compose / Material 3：UI 框架")
                }
            }

            item(key = "section-clear") { SectionHeader("清空数据") }
            item(key = "clear-tip") {
                TacticalPanel {
                    Text(
                        text = "如需清除所有本地数据（账号、Cookie、缓存、Widget），" +
                            "请在「设置 → 账号操作 → 清空所有本地数据」中操作。",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "操作不可恢复，请确认后执行。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Text(
        text = "· $text",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun BulletCard(title: String, body: String) {
    TacticalPanel {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
