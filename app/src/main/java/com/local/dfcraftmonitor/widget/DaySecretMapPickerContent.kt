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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import kotlinx.coroutines.launch

/**
 * "今日密码 4×1 桌面卡"地图多选 picker 的纯状态机。
 *
 * 从 [DaySecretMapPickerContent] 抽出来：UI 渲染时只需要 `state` 和
 * `onToggle/onSelectAll/onClear/onSave` 几个不变量；这样 cap 行为可以
 * 走纯 JUnit 测，不依赖 Robolectric/Compose。
 *
 * 设计要点：
 * - 用 [LinkedHashSet] 保留用户操作顺序（prefs 端按这个顺序决定卡片 4 格的填充顺序）。
 * - 选满 [maxSelection] 时**只能取消已有项**——新加项被 [toggle] 忽略。这是"达到上限后
 *   禁用未选项"行为的纯函数版本；UI 层再额外禁用未勾选项的 Checkbox/Row 视觉反馈。
 * - [onSaveSnapshot] 返回的是 `take(maxSelection)` 后的"实际写入集合"——双保险，
 *   即使 UI 误传也保证落盘不超限。
 *
 * @param maxSelection 最大可选数，必须与渲染层 [WidgetRemoteViewsBuilder.MAX_STATIONS]
 *                     保持一致（4）。该参数是 picker 上限的真实来源：UI 禁用、状态机
 *                     拒绝、save 截断，三道闸共用同一个常量。
 */
class DaySecretPickerState(
    private val maxSelection: Int,
    initial: Set<String> = emptySet(),
) {
    init {
        // 状态机自洽：构造时就拒掉 ≤ 0 的上限。Compose 层也会再 require 一次作为
        // 早期失败（不进入 Composable 渲染），但状态机单独使用时仍要保证不变量。
        require(maxSelection > 0) { "maxSelection must be > 0, was $maxSelection" }
    }

    /** 当前已选集合，保留用户操作顺序。 */
    var selected: LinkedHashSet<String> = linkedSetOf<String>().apply { addAll(initial) }
        private set

    val isAtCap: Boolean get() = selected.size >= maxSelection

    /**
     * 切换某项的勾选状态的结果。UI 层根据这个区分三种情形：
     * - [Toggled]：状态真的变了（添加或取消）
     * - [RejectedAtCap]：未勾选 + 已满——状态没变，UI 应弹 Snackbar 提示用户
     *   "已选满 N 个，请先取消一个"
     * - [NoOp]：已勾选 + 已满但用户点的是已勾选项（理论上 at cap 不会发生，
     *   但分支保留以保证 toggle 语义对称）
     */
    sealed interface ToggleResult {
        data object Toggled : ToggleResult
        data object RejectedAtCap : ToggleResult
        data object NoOp : ToggleResult
    }

    /**
     * 切换某项的勾选状态。
     * - 已勾选 → 取消（[ToggleResult.Toggled]）
     * - 未勾选 + 未满 → 勾选（[ToggleResult.Toggled]）
     * - 未勾选 + 已满 → 拒绝（[ToggleResult.RejectedAtCap]），UI 应弹提示
     *
     * 注：用户**必须先手动取消一个**才能勾新的——这是产品决策（不允许自动踢旧的塞新的，
     * 避免"我点哪个就动哪个"的不可预测感）。
     */
    fun toggle(mapName: String): ToggleResult {
        if (mapName in selected) {
            selected.remove(mapName)
            return ToggleResult.Toggled
        }
        if (isAtCap) return ToggleResult.RejectedAtCap
        selected.add(mapName)
        return ToggleResult.Toggled
    }

    /**
     * 全选：[available] 数量 ≤ 上限时全选；超过上限时按 [available] 的入参顺序取前
     * [maxSelection] 个填入。**不会因为超过上限而拒绝执行**——这与 toggle 的拒绝策略不同：
     * 全选是一次性把"可见列表的前 N 个"作为初始选择的便利操作，不应被 disable 掉。
     *
     * @return 实际被选中的数量（≤ [maxSelection]），UI 可用于统计反馈。
     */
    fun selectAll(available: Collection<String>): Int {
        val picked = available.take(maxSelection)
        selected = linkedSetOf<String>().apply { addAll(picked) }
        return picked.size
    }

    /** 清空。 */
    fun clear() {
        selected = linkedSetOf()
    }

    /**
     * 取本次"提交保存"时真正写入的集合。
     *
     * 理论上前两道闸后 `selected.size <= maxSelection`；这里再 `take` 一次是防御性
     * 兜底（万一以后改了 maxSelection 逻辑但 save 端没跟上）。
     */
    fun snapshotForSave(): Set<String> = selected.take(maxSelection).toSet()
}

/**
 * Configure Activity 与设置页子屏共用的"今日密码地图多选" UI。
 *
 * @param availableMaps 全部可选地图（来自 WidgetCache.loadForWidget()?.daySecrets）。
 * @param initialSelection 初始勾选状态（来自 UserPreferencesRepository）。
 * @param title 文案（Configure 与设置页文案不同）。
 * @param onSave 保存回调：参数是已勾选集合，调用方负责写偏好与刷新 widget。
 * @param maxSelection 上限（默认 4，与 4×1 卡片固定 4 格对齐）。
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
    maxSelection: Int = DEFAULT_MAX_SELECTION,
    showBottomBar: Boolean = true,
) {
    // maxSelection 必须与 widget 渲染层 WidgetRemoteViewsBuilder.MAX_STATIONS 保持一致；
    // 这里是 picker 上限的真实来源（状态机拒绝、UI 禁用、save 截断 三道闸共用）。
    require(maxSelection > 0) { "maxSelection must be > 0, was $maxSelection" }

    val state = remember { DaySecretPickerState(maxSelection, initialSelection) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val atCapMessage = "已选满 $maxSelection 个，请先取消一个"
    // isAtCap 在 Composable 内随时变化；用 derivedStateOf 让 Text 在状态变化时重组。
    val atCap = remember { derivedStateOf { state.isAtCap } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )

            // 顶部 banner：达到上限时高亮"最多 N 个地图"，未达到时只显示柔和提示。
            // 这是产品要求——给用户**先验**信号（看到 banner 知道有上限），而不只是
            // 事后通过"点击被拒绝"才能感知。
            CapBanner(
                maxSelection = maxSelection,
                atCap = atCap.value,
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 全选：即使 availableMaps.size > maxSelection 也**不禁用**——直接取
                // availableMaps 的前 maxSelection 个填入。语义是"把可见列表前 N 个快速
                // 填上"，不应被拒绝。
                Button(
                    onClick = {
                        state.selectAll(availableMaps.map { it.mapName })
                    },
                ) {
                    Text("全选")
                }
                Button(onClick = { state.clear() }) {
                    Text("清空")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "已选 ${state.selected.size} / $maxSelection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (atCap.value) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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
                        val isSelected = entry.mapName in state.selected
                        // 选满时未勾选项禁用：视觉上 Checkbox 灰显、整行点击响应时弹 Snackbar。
                        val isDisabled = !isSelected && atCap.value
                        DaySecretMapRow(
                            entry = entry,
                            selected = isSelected,
                            disabled = isDisabled,
                            onToggle = {
                                when (state.toggle(entry.mapName)) {
                                    DaySecretPickerState.ToggleResult.RejectedAtCap -> {
                                        // disabled 行本应不响应 onToggle，但有些
                                        // accessibility（TalkBack）仍会触发 click；
                                        // 这里兜底：弹 Snackbar 解释原因。
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(atCapMessage)
                                        }
                                    }
                                    else -> Unit
                                }
                            },
                        )
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
                        onClick = { onSave(state.snapshotForSave()) },
                    ) {
                        Text("保存")
                    }
                }
            }
        }

        // Snackbar 挂在外层 Box 的底部，覆盖在 LazyColumn + 按钮之上，
        // 这样 disabled 行被 click 时的反馈会浮在内容之上而不是被列表遮住。
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CapBanner(maxSelection: Int, atCap: Boolean) {
    val color = if (atCap) {
        // 达到上限时用 primary（品牌绿/琥珀）高亮，配合下方 N/M 计数同色。
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                if (atCap) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "桌面 4×1 卡片固定 4 格，每张卡片最多展示 $maxSelection 个地图密码",
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun DaySecretMapRow(
    entry: WidgetPayload.DaySecretEntry,
    selected: Boolean,
    disabled: Boolean,
    onToggle: () -> Unit,
) {
    // Bug 3 修复：原来 Row.clickable(enabled = true, onClick = onToggle) +
    // Checkbox(onCheckedChange = null, enabled = !disabled) 的组合——
    // Material3 Checkbox 即使 onCheckedChange = null，其内部的 toggleable 仍会
    // 消费 touch 事件但不回调，导致整行的 clickable 收不到点击，用户视觉上的
    // 主要交互目标（方形勾选框本体）点了没反应。
    //
    // 新实现：
    //  - Checkbox 自己回调 onCheckedChange = { onToggle() }，让勾选框本体点击生效
    //  - Row.clickable(enabled = !disabled) 也保留，覆盖 Checkbox 之外的区域
    //    （地图名 Text 区域），让用户点文字也能切换
    //  - disabled 行视觉上仍可点，onToggle 会被 DaySecretPickerState.toggle 拒绝为
    //    RejectedAtCap，UI 层会弹 Snackbar 解释（已有逻辑）
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.shapes.medium,
        )
        .border(
            1.dp,
            if (disabled) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outline,
            MaterialTheme.shapes.medium,
        )
        .clickable(enabled = !disabled, onClick = onToggle)
        .padding(horizontal = 12.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = !disabled,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = entry.mapName,
            style = MaterialTheme.typography.titleMedium,
            color = if (disabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * picker 上限的默认值。必须与 [WidgetRemoteViewsBuilder.MAX_STATIONS] 保持一致；
 * 两边任一改动都需要同步另一处（4×1 卡片物理上只有 4 格）。
 */
const val DEFAULT_MAX_SELECTION: Int = 4
