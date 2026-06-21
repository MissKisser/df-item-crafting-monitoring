package com.local.dfcraftmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.dfcraftmonitor.data.model.AccountEntry
import com.local.dfcraftmonitor.ui.common.SectionHeader
import com.local.dfcraftmonitor.ui.theme.SemanticColors

/**
 * 设置页：通知 / 账号 / 隐私 / 危险操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPrivacy: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onLogout: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
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
            item(key = "section-notify") { SectionHeader("通知") }
            item(key = "row-notify") {
                SettingsRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "制造完成提醒",
                    trailing = {
                        Switch(
                            checked = prefs.craftingNotificationEnabled,
                            onCheckedChange = { viewModel.setCraftingNotificationEnabled(it) },
                        )
                    },
                )
            }
            item(key = "section-accounts") { SectionHeader("账号") }
            items(items = accounts, key = { it.accountId }) { account ->
                AccountRow(
                    account = account,
                    isCurrent = account.accountId == currentAccountId,
                    onSelect = { viewModel.switchAccount(account.accountId) },
                )
            }
            item(key = "row-add-account") {
                SettingsRow(
                    icon = Icons.Default.Add,
                    title = "添加账号",
                    subtitle = "扫码登录新账号（不清空现有数据）",
                    onClick = onAddAccount,
                )
            }
            item(key = "section-privacy") { SectionHeader("隐私与安全") }
            item(key = "row-privacy") {
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "隐私说明",
                    subtitle = "查看本地数据存储与第三方依赖",
                    onClick = onNavigateToPrivacy,
                )
            }
            item(key = "section-danger") { SectionHeader("账号操作") }
            item(key = "row-logout") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出当前账号",
                    subtitle = "清除当前账号与缓存",
                    tint = SemanticColors.warn,
                    onClick = {
                        viewModel.logoutCurrent { remaining ->
                            if (remaining == null) onLogout()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountEntry,
    isCurrent: Boolean,
    onSelect: () -> Unit,
) {
    val checkmark: (@Composable () -> Unit)? = if (isCurrent) {
        {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "当前账号",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else null
    // 头像优先：URL 非空时用 RemoteImage 渲染 40dp 圆框头像；空时回退到 Person 图标。
    val avatarSlot: (@Composable () -> Unit)? = if (account.avatarUrl.isNotBlank()) {
        {
            com.local.dfcraftmonitor.ui.common.RemoteImage(
                url = account.avatarUrl,
                contentDescription = "账号头像",
                modifier = Modifier.fillMaxSize(),
                showContainer = false,
            )
        }
    } else null
    SettingsRow(
        icon = if (avatarSlot == null) Icons.Outlined.Person else null,
        iconContent = avatarSlot,
        title = account.nickname.ifBlank { "未命名账号" },
        subtitle = account.areaName.ifBlank { "区服待同步" },
        trailing = checkmark,
        onClick = onSelect,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector? = null,
    /**
     * 自定义图标内容（可空）。当不为空时，整个 40dp 圆形 avatar slot 由调用方渲染
     * （例如账号头像走 RemoteImage），不再使用 [icon] 的 ImageVector。
     * 注意：与 [icon] 二选一；当两者都不为 null 时以 [iconContent] 优先。
     */
    iconContent: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = clickableModifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.medium,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (iconContent != null) {
                // 头像等自定义内容填满整个 40dp 圆框
                iconContent()
            } else if (icon != null) {
                // 默认 icon 缩小 8dp padding
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = tint,
                style = MaterialTheme.typography.titleMedium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
