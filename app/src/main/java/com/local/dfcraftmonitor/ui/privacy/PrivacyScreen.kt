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
                        text = "隐私声明",
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
            // ===================== 总则 =====================
            item(key = "section-overview") {
                SectionHeader("隐私声明", "开发者 misskisserxr@gmail.com")
            }
            item(key = "overview-card") {
                TacticalPanel {
                    Text(
                        text = "三角洲助手（以下简称本应用）由个人开发者维护，开发者邮箱：misskisserxr@gmail.com。\n\n" +
                            "本应用是一款完全本地化运行的辅助工具，不依赖任何业务后端服务器。" +
                            "所有账号信息、Cookie、缓存、Widget 数据均仅保存在用户本机，" +
                            "开发者及任何第三方均无法访问、收集或上传上述数据。",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ===================== 数据存储 =====================
            item(key = "section-storage") { SectionHeader("数据存储") }
            item(key = "storage-card") {
                TacticalPanel {
                    BulletLine("账号 Cookie：仅保存在本机 DataStore 中，用于维持官方页面登录会话。")
                    BulletLine("制造快照：本地缓存最近一次拉取的特勤处数据，仅用于离线浏览与差异计算。")
                    BulletLine("Widget 缓存：用于桌面小组件展示，不与开发者共享。")
                    BulletLine("通知偏好：用于控制是否在后台发送制造完成提醒。")
                }
            }

            // ===================== 权限说明 =====================
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

            // ===================== 第三方依赖 =====================
            item(key = "section-third-party") { SectionHeader("第三方依赖") }
            item(key = "third-party-card") {
                TacticalPanel {
                    BulletLine("OkHttp / Retrofit：网络请求（仅与官方页面交互）")
                    BulletLine("Hilt：依赖注入（编译时）")
                    BulletLine("WorkManager：后台任务调度（仅在本机执行）")
                    BulletLine("Jetpack Compose / Material 3：UI 框架")
                }
            }

            // ===================== 免责声明 =====================
            item(key = "section-disclaimer") { SectionHeader("免责声明") }
            item(key = "disclaimer-card") {
                TacticalPanel {
                    Text(
                        text = "1. 本应用仅作为官方页面的本地化辅助工具，所有数据来源于官方公开接口，" +
                            "开发者不对数据准确性、完整性、及时性做任何承诺。\n\n" +
                            "2. 本应用与三角洲行动官方无任何合作关系，游戏中任何政策、规则调整" +
                            "可能导致本应用部分功能失效，开发者不承担由此产生的任何损失。\n\n" +
                            "3. 用户应自行妥善保管本机账号凭证，因设备丢失、未授权访问等" +
                            "造成的任何后果由用户本人承担。\n\n" +
                            "4. 使用本应用即视为同意以上条款。如不同意，请卸载本应用并清除所有本地数据。",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ===================== 联系开发者 =====================
            item(key = "section-contact") { SectionHeader("联系开发者") }
            item(key = "contact-card") {
                TacticalPanel {
                    Text(
                        text = "如有问题、建议或合作意向，请通过以下方式联系：",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "邮箱：misskisserxr@gmail.com",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "本声明最终解释权归开发者所有。",
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
