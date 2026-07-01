# 今日密码 4×1 桌面卡片 · 闭环审查报告

> 审查范围：`dee75df..ce806e1`（任务 1–12，共 12 个 commit）
> 涉及文件 20 个（新增 12 / 修改 8）

---

## 一、闭环链路追踪

按用户视角从"数据从哪来"到"屏幕上看到什么"，逐节点确认闭合：

| # | 闭环节点 | 关键代码 | ✅/❌ |
|---|---------|---------|------|
| 1 | 远端 API | `DefaultLocalBackend.fetchDaySecrets` → `dfm/center.day.secret` | ✅ |
| 2 | 解析 | `AmsDashboardParser.parseDaySecrets` → `List<DaySecret>` | ✅ |
| 3 | 载体 | `LocalDashboardData.daySecrets` | ✅ |
| 4 | 缓存写入 | `WidgetCache.updateFromDashboard` → `DaySecret→DaySecretEntry` 映射 | ✅ |
| 5 | 缓存读取 | `WidgetCache.loadForWidget()` → payload 含 daySecrets | ✅ |
| 6 | Manifest 注册 | `<receiver .DaySecretWidgetProvider>` + `<activity .DaySecretWidgetConfigureActivity>` | ✅ |
| 7 | Widget 声明 | `day_secret_widget_info.xml` 4×1 + configure + 900s 周期 | ✅ |
| 8 | onUpdate | `DaySecretWidgetProvider` → `Applier.updateAll(force=true)` | ✅ |
| 9 | Builder | `buildDaySecret(ctx, payload, prefs)` → 4 格 RemoteViews | ✅ |
| 10 | 选格逻辑 | `pickDaySecretCells` 纯函数（prefs 空→字典序前4 / 非空→按 prefs 顺序） | ✅ |
| 11 | 偏好写入 | `UserPreferencesRepository.setDaySecretWidgetVisibleMaps` | ✅ |
| 12 | 偏好读取 | `WidgetCache.loadDaySecretPrefs()` 直读 `user_prefs` | ✅ |
| 13 | Applier 分发 | `updateWidgetsOfClass(DaySecretWidgetProvider, payload, prefs)` | ✅ |
| 14 | 签名检测 | `computeSignatureInternal` 纳入 `secretsSig` | ✅ |
| 15 | Configure Activity | 拖放后弹出 picker → save prefs → `forceUpdateAll()` → 返回 RESULT_OK | ✅ |
| 16 | 设置页入口 | Settings → "今日密码 桌面卡" → NavHost 路由 → `DaySecretMapPickerScreen` | ✅ |
| 17 | 布局 | `widget_day_secret.xml`：顶部标题+账号+刷新 / 底部 4 格 map_name+secret | ✅ |
| 18 | 整卡点击 | `bindCardClick` → `getLaunchIntentForPackage` → 打开首页 | ✅ |
| 19 | 刷新按钮 | `bindRefreshButton` → `WidgetRefreshReceiver` → restore+sync | ✅ |
| 20 | 旧数据兼容 | `daySecrets = emptyList()` 默认值 + `ignoreUnknownKeys=true` + 契约测试 | ✅ |

**闭环判定：✅ 20/20 节点全部闭合，无断链。**

---

## 二、发现的问题

### 🔴 P0 — DataStore `user_prefs` 双实例冲突（运行时崩溃风险）

**位置**：`WidgetCache.kt:45` vs `UserPreferencesRepository.kt:24-26`

```kotlin
// WidgetCache.kt — 文件顶层
private val Context.userPrefsStore by preferencesDataStore(name = "user_prefs")

// UserPreferencesRepository.kt — 类成员
private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
)
```

两个文件各自声明了 `name = "user_prefs"` 的 DataStore 委托。DataStore 的契约是**同名文件只能有一个活跃实例**，否则抛：

> `"There are multiple DataStores active for the same file"`

`WidgetCache.kt` 自身的注释（L25-31）已认识到此问题——`widgetStore` 放文件顶层就是为了避免多实例——但新增的 `userPrefsStore` 又在另一个文件开了同名委托。

**触发路径**：
1. Hilt 注入 `UserPreferencesRepository` → 初始化 `prefsStore`
2. `WidgetRefreshReceiver.restoreCachedViews` → `new WidgetCache(context)` → `loadDaySecretPrefs()` → 初始化 `userPrefsStore`
3. 两个实例同时存在 → **崩溃**

**为何当前可能没崩**：委托是惰性初始化，时序上可能错开。但这是时序依赖的侥幸，不是可靠设计。

**建议修复**：在 `WidgetCache` 中注入 `UserPreferencesRepository`（只读方法），删除 `userPrefsStore` 委托和 `keyDaySecretWidgetVisibleMapsAlt`。

---

### 🟡 P1 — 后台周期同步不刷新 daySecrets（数据过时）

**位置**：`WidgetCache.kt:135-151` `updateFromSync()`

同步链路：

```
CraftingCheckWorker (15min)  → SyncCoordinator.syncOnce() → handleSuccess()
WidgetRefreshWorker (点刷新) → 同上
```

`handleSuccess` 调用的是 `widgetCache.updateFromSync()`，该方法**只写入 stations，不写入 daySecrets**：

```kotlin
fun updateFromSync(accountId, snapshot, existing) {
    val payload = WidgetPayload(
        ...
        stations = snapshot.stations.map { it.toWidgetStation() },
        // ← 没有 daySecrets！
    )
}
```

而 `daySecrets` 仅在 `updateFromDashboard()` 中写入，该方法只被以下场景调用：
- `HomeViewModel.refreshDashboard()` — 用户打开 App 或手动下拉
- `LoginViewModel` — 登录成功后

**影响**：用户如果当天不打开 App，仅依赖后台 15 分钟周期同步，今日密码卡片**永远不会更新**。零点密码刷新后，卡片将持续显示旧密码。

**建议修复**：`updateFromSync` 合并 existing 的 daySecrets（与 nickname/areaName 一致）：

```kotlin
daySecrets = existing?.daySecrets ?: emptyList(),
```

更彻底的方案：在 `SyncCoordinator` 或周期 Worker 中也触发一次 dashboard 拉取。

---

### 🟡 P2 — `WidgetPayload.empty()` 缺少 daySecrets 显式参数

**位置**：`WidgetPayload.kt:44-53`

```kotlin
fun empty(accountId: String) = WidgetPayload(
    accountId = accountId,
    nickname = "",
    ...
    fetchedAtEpochMillis = 0L,
    // daySecrets 靠默认值 = emptyList()，未显式写出
)
```

当前不是 bug（默认值兜底），但与其他字段显式写 `""` / `0L` 的风格不一致。若将来默认值被移除，这里会编译报错。

**建议**：补一行 `daySecrets = emptyList(),`

---

### 🟢 P3 — LinkedHashSet 类型强转依赖实现细节

**位置**：`DaySecretMapPickerContent.kt:109-112`

```kotlin
selected = (selected - entry.mapName) as LinkedHashSet<String>
selected = (selected + entry.mapName) as LinkedHashSet<String>
```

`Set.minus()` / `Set.plus()` 返回 `Set`，不保证是 `LinkedHashSet`。当前 Kotlin 标准库实现恰好返回 `LinkedHashSet`，但这是实现细节而非契约。

**建议**：显式构建：

```kotlin
selected = linkedSetOf<String>().apply { addAll(selected); add(entry.mapName) }
```

---

### 🟢 P4 — 设置页"已选地图"图标语义不符

**位置**：`SettingsScreen.kt:105`

```kotlin
icon = Icons.Outlined.NotificationsActive,  // 通知铃铛
title = "已选地图",
```

"已选地图"与通知无关。建议换成 `Icons.Outlined.Map` 或 `Icons.Outlined.GridView`。

---

## 三、测试覆盖评估

| 测试文件 | 覆盖 | 判定 |
|---------|------|------|
| `WidgetDaySecretPickerTest` | `pickDaySecretCells` 7 个场景（null / 空 / 字典序 / prefs 顺序 / 不存在名 / 溢出 / 长名） | ✅ 充分 |
| `WidgetPayloadCompatibilityTest` | 旧 JSON 无 daySecrets 回落 + round-trip | ✅ 充分 |
| `WidgetContractSourceTest` | 4 模式 Provider/Applier/Builder/Manifest 契约 + DaySecret 数据契约 + 4×1 尺寸校验 | ✅ 充分 |
| Gradle 构建 | BUILD SUCCESSFUL, 33 tasks | ✅ 通过 |

**盲区**：
- 无 DataStore 双实例冲突的集成测试
- 无 `updateFromSync` 保留 daySecrets 的回归测试
- Configure Activity 端到端测试（需 Robolectric/Instrumentation，当前项目不强制）

---

## 四、问题汇总与建议

| 优先级 | 问题 | 影响 | 阻塞发布？ |
|-------|------|------|----------|
| 🔴 P0 | DataStore `user_prefs` 双实例 | 运行时崩溃 | **建议修复后发布** |
| 🟡 P1 | 周期同步不刷新 daySecrets | 密码数据过时 | 建议修复 |
| 🟡 P2 | `WidgetPayload.empty()` 缺显式参数 | 风格/未来兼容 | 可选 |
| 🟢 P3 | LinkedHashSet 强转 | 潜在 ClassCastException | 可选 |
| 🟢 P4 | 设置页图标语义 | UI 细节 | 可选 |

---

**结论**：功能闭环完整，20 个节点全部闭合，测试覆盖充分。**P0 DataStore 双实例是唯一阻塞项**，修复后即可安全发布。P1 影响数据时效性，建议同版本修复。P2–P4 为代码质量改进，可延后处理。
