package com.local.dfcraftmonitor.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.dfcraftmonitor.data.monitor.WidgetPayload

/**
 * Configure Activity 与设置页子屏共用的"今日密码地图多选" UI。
 *
 * @param availableMaps 全部可选地图（来自 WidgetCache.loadForWidget()?.daySecrets）。
 * @param initialSelection 初始勾选状态（来自 UserPreferencesRepository）。
 * @param title 文案（Configure 与设置页文案不同）。
 * @param onSave 保存回调：参数是已勾选集合，调用方负责写偏好与刷新 widget。
 * @param showBottomBar 是否渲染底部"保存"按钮：通常为 true；子屏父 Composable
 *                     已有"保存"时设为 false（本实现下两者都保留，子屏可设置 false
 *                     让外层决定何时触发回调）。
 */
@Composable
fun DaySecretMapPickerContent(
    availableMaps: List<WidgetPayload.DaySecretEntry>,
    initialSelection: Set<String>,
    title: String,
    onSave: (Set<String>) -> Unit,
    showBottomBar: Boolean = true,
) {
    // 保留 LinkedHashSet 顺序：用户操作顺序即为最终 prefs 顺序。
    var selected by remember {
        mutableStateOf(linkedSetOf<String>().apply { addAll(initialSelection) })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { selected = linkedSetOf<String>().apply { addAll(availableMaps.map { it.mapName }) } }) {
                Text("全选")
            }
            Button(onClick = { selected = linkedSetOf() }) {
                Text("清空")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (availableMaps.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "暂无地图数据，请先在 App 内同步一次仪表盘",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(items = availableMaps, key = { it.mapName }) { entry ->
                    Row(
                        modifier = Modifier
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
                            .clickable {
                                if (entry.mapName in selected) {
                                    selected = linkedSetOf<String>().apply { addAll(selected); remove(entry.mapName) }
                                } else {
                                    selected = linkedSetOf<String>().apply { addAll(selected); add(entry.mapName) }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = entry.mapName in selected,
                            onCheckedChange = null, // 整行点击已处理
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = entry.mapName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSave(selected) },
                ) {
                    Text("保存")
                }
            }
        }
    }
}
