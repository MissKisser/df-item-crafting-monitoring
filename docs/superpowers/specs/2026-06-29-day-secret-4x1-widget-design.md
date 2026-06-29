# 今日密码桌面卡片（4×1）· 设计规格

**日期**：2026-06-29
**状态**：待用户审查
**作者**：brainstorming session

## 背景与目标

App 首页 `HomeTab` 已有"今日密码"模块（横向滚动 LazyRow），但没有桌面卡片 Widget。新增一张 4×1 桌面卡片，让用户无需进 App 即可看到 4 个地图的今日密码。

**关键约束**：

- 与现有 3 张桌面卡片（`CraftingDetailWidgetProvider` / `TodayProfitWidgetProvider` / `CombinedWidgetProvider`）保持同一渲染流水线：数据进 `WidgetPayload` → `WidgetRemoteViewsApplier` 变化检测 → RemoteViews 重建。
- 用户可配置"显示哪 4 个地图"（Configure Activity + App 内偏好双入口）。
- 数据来源已有 `dashboard.daySecrets`（`LocalDashboardModels.kt:3 DaySecret(mapName, secret)`），需补到 widget 缓存。

## 1. 架构

### 1.1 新增文件

| # | 路径 | 说明 |
|---|---|---|
| 1 | `widget/DaySecretWidgetProvider.kt` | 标准 `AppWidgetProvider`；`onUpdate` 只做"从缓存重渲染" |
| 2 | `widget/DaySecretWidgetConfigureActivity.kt` | 拖放后配置：勾选显示哪 4 个地图，写入 `UserPreferencesRepository` |
| 3 | `res/layout/widget_day_secret.xml` | 4×1 布局（标题栏 + 4 格密码） |
| 4 | `res/xml/day_secret_widget_info.xml` | `targetCellWidth=4 / targetCellHeight=1`，引用 Configure Activity |

### 1.2 修改文件

| # | 路径 | 说明 |
|---|---|---|
| 5 | `widget/WidgetRemoteViewsBuilder.kt` | 新增 `fun buildDaySecret(context, payload, prefs)` |
| 6 | `widget/WidgetRemoteViewsApplier.kt` | `computeSignatureInternal` + `updateWidgetsOfClass` 加 `DaySecretWidgetProvider` 分支 |
| 7 | `data/monitor/WidgetPayload.kt` | 加 `daySecrets: List<DaySecretEntry> = emptyList()` 字段 |
| 8 | `data/monitor/WidgetCache.kt` | `updateFromDashboard` 把 `dashboard.daySecrets` 落进缓存 |
| 9 | `data/preference/UserPreferencesRepository.kt` | `UserPreferences.daySecretWidgetVisibleMaps: Set<String> = emptySet()` |
| 10 | `ui/settings/SettingsScreen.kt` + `ui/settings/SettingsViewModel.kt` | 新增"今日密码 桌面卡 已选地图"设置项（复用 Configure UI） |
| 11 | `app/src/main/AndroidManifest.xml` | 注册 Provider（`APPWIDGET_UPDATE`）+ Configure Activity |
| 12 | `res/values/strings.xml` | `widget_day_secret_label` |
| 13 | `app/src/test/.../widget/WidgetContractSourceTest.kt` | 扩契约测试 |

### 1.3 数据流

```
AmsCraftingParser (已存在)
  └─→ HomeViewModel.refreshDashboard (已存在)
        └─→ LocalDashboardData.daySecrets (已存在)
              └─→ WidgetCache.updateFromDashboard (新一行)
                    └─→ WidgetPayload.daySecrets (新字段)
                          └─→ WidgetRemoteViewsBuilder.buildDaySecret
                                └─→ DaySecretWidgetConfigureActivity (拖放后 / 设置页)
                                      └─→ UserPreferences.daySecretWidgetVisibleMaps
                                            (Configure Activity 写完后调 widgetUpdater.forceUpdateAll)

拖放到桌面 → onUpdate → WidgetRemoteViewsApplier.updateAll(force=true)
卡片整体点击 → MainActivity (HOME)
btn_refresh → WidgetRefreshReceiver.ACTION_REFRESH (现有)
```

## 2. 数据模型与兼容性

### 2.1 `WidgetPayload` 加新字段

```kotlin
@Serializable
data class WidgetPayload(
    val accountId: String,
    val nickname: String,
    val avatarUrl: String,
    val areaName: String,
    val todayProfitValue: Long,
    val todayProfitText: String,
    val stations: List<WidgetStation>,
    val fetchedAtEpochMillis: Long,
    val daySecrets: List<DaySecretEntry> = emptyList(),  // 新增；老数据缺这个字段时不报错
) {
    @Serializable
    data class DaySecretEntry(
        val mapName: String,
        val secret: String,
    )
    // ... 既有 WidgetStation ...
}
```

**复用 Json 配置**：`WidgetCache` 已用 `Json { ignoreUnknownKeys = true; encodeDefaults = true }`。新字段带默认值，老 DataStore 反序列化时填 `emptyList()`，零迁移。

**不复用 `data.backend.DaySecret`**：widget 包不能依赖 UI / domain 包（防止循环引用）。新增内部 `DaySecretEntry`。

### 2.2 `UserPreferences` 加新偏好

```kotlin
data class UserPreferences(
    // ... 已有字段 ...
    val daySecretWidgetVisibleMaps: Set<String> = emptySet(),
)
```

**空集语义**：用户没做任何配置 → 显示"全部地图按 `mapName` 字典序排序的前 4 个"。多于 4 取前 4，少于 4 余下格显示"未选"提示（首次拖放用户会被引导去 Configure；后续在 App 设置里也能改）。

### 2.3 `WidgetCache.updateFromDashboard` 改一行

```kotlin
val payload = WidgetPayload(
    // ... 既有字段 ...
    daySecrets = dashboard.daySecrets.map { WidgetPayload.DaySecretEntry(it.mapName, it.secret) },
)
```

排序无关：`buildDaySecret` 内会按 `prefs` + `sortedBy { it.mapName }` 决定显示顺序。

## 3. 渲染与边缘情况

### 3.1 布局结构

```
widget_day_secret.xml (4×1)
├─ header (高 30dp)
│   ├─ ic_day_secret (14dp 圆形 icon, background=@drawable/bg_widget_chip)
│   ├─ "今日密码" 11sp 灰
│   ├─ account_name (weight=1, 9sp 灰, ellipsis=end, 默认 "未登录")
│   └─ btn_refresh (14dp, src=ic_refresh)
├─ body (height=0, weight=1, horizontal)
│   ├─ cell_0 ... cell_3
│   │   ├─ map_name (10sp, singleLine, ellipsis=end, 默认 "待同步")
│   │   └─ secret (18sp, bold, accent #FBBF24, monospace, letterSpacing=1)
```

**共享**：复用 `@drawable/bg_widget_card`、`<ImageView btn_refresh>`、`@color/widget_text_*`、`@id/account_name`。**不引入** `<ImageButton>`（对齐 `WidgetContractSourceTest.nativeWidgetsStayCompactAndTextOnly` 的约束）。

**不引入** Chronometer / `bg_station_card`（密码纯文字，用统一灰底）。

### 3.2 边缘情况

| 边界 | 处理 |
|---|---|
| `payload == null` | 标题"未登录"，4 格"待同步"，点刷新走 `WidgetRefreshReceiver.restoreAndRequestRefresh` |
| `payload.daySecrets` 为空 | 4 格全"暂无密码"，提示"点刷新" |
| 首次拖放（`prefs` 空集） | 按 `mapName` 字典序展示前 4 个，少于 4 余下格"未选" |
| 长地图名 | `map_name` `singleLine=true, ellipsize=end` |
| 地图名含特殊字符 | 与现有 widget 一致：`Html.fromHtml` 安全转义在 builder 内兜底 |

### 3.3 签 名

`WidgetRemoteViewsApplier.computeSignatureInternal` 加：

```kotlin
val secretsSig = payload.daySecrets
    .sortedBy { it.mapName }
    .joinToString("|") { "${it.mapName}#${it.secret}" }

return buildString {
    append(payload.nickname).append('/')
    append(payload.areaName).append('/')
    append(payload.todayProfitValue).append('/')
    append(payload.todayProfitText).append('/')
    append(stationsSig).append('/')
    append(secretsSig)
}
```

**不纳入** `prefs.daySecretWidgetVisibleMaps`：偏好改写通过 Configure Activity 主动调 `widgetUpdater.forceUpdateAll()`。

### 3.4 Tap 行为

- 卡片整体点击 → `PendingIntent.getActivity` 启动 `MainActivity`（HOME）。
- `btn_refresh` → 既有 `WidgetRefreshReceiver.ACTION_REFRESH`。

## 4. Configure Activity 与设置同步

### 4.1 Configure Activity

`widget/DaySecretWidgetConfigureActivity.kt`：

```
Compose 屏
├─ 标题 "配置今日密码桌面卡"
├─ 显示当前账号全部地图（WidgetCache.loadForWidget()?.daySecrets 取）
├─ 每个地图 Checkbox + 地图名
├─ "全选" / "清空" 顶部快捷
└─ "保存" 底部
    ├─ selectedMapNames.toSet() → userPrefs.copy(daySecretWidgetVisibleMaps = ...)
    ├─ userPreferencesRepository.update(newPrefs)
    ├─ widgetUpdater.forceUpdateAll()
    └─ finish()
```

`AndroidManifest.xml` 注册：

```xml
<activity
    android:name=".widget.DaySecretWidgetConfigureActivity"
    android:exported="true"
    android:label="@string/widget_day_secret_configure_label">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

`day_secret_widget_info.xml` 配：

```xml
<appwidget-provider
    android:configure="com.local.dfcraftmonitor.widget.DaySecretWidgetConfigureActivity"
    ... />
```

### 4.2 设置页同步入口

`ui/settings/SettingsScreen.kt` 中增"今日密码 桌面卡" SectionHeader：

```
"今日密码 桌面卡"
  └─ "已选地图 (N/4)"  → 进子屏 → 同款 Compose 多选 UI
```

子屏复用 `DaySecretMapPickerContent` Composable，Configure Activity 与设置页共享。子屏为新文件 `ui/settings/DaySecretMapPickerScreen.kt`，由 `SettingsScreen` 导航跳入（或由现有 NavHost 跳入）。

## 5. 测试策略

### 5.1 契约测试（在 `WidgetContractSourceTest.kt` 新增）

```kotlin
@Test
fun providersExposeCraftingProfitCombinedAndDaySecretModes() {
    // Manifest 注册 DaySecretWidgetProvider + day_secret_widget_info
    // DaySecretWidgetConfigureActivity 在 manifest 注册
    // DaySecretWidgetConfigureActivity 出现在 day_secret_widget_info 的 android:configure
    // WidgetRemoteViewsApplier 含 DaySecretWidgetProvider
    // WidgetRemoteViewsBuilder 含 buildDaySecret + R.layout.widget_day_secret
}
```

### 5.2 边界渲染测试（新增 `WidgetRemoteViewsBuilderDaySecretTest.kt`）

```kotlin
@Test fun buildDaySecretEmpty()        // payload=null → 全部 cell 显示 "待同步" + 标题 "未登录"
@Test fun buildDaySecretNoSelection()  // payload.daySecrets=[5 个], prefs=∅ → 取前 4
@Test fun buildDaySecretSelection()    // payload.daySecrets=[5 个], prefs={a,b,c,d} → 4 格按 prefs 顺序
@Test fun buildDaySecretOverflow()     // payload.daySecrets=[3 个] → 第 4 格 "未选"
@Test fun buildDaySecretLongMapName()  // map_name RemoteViews 路径下断言 ellipsize / 单行（用影子 layout introspect 也行）— 或删除并通过 UI 测试覆盖
@Test fun signatureChangesWhenSecretChanges()  // 改了 secret → 签名变
```

### 5.3 反序列化兼容（新增 `WidgetPayloadCompatibilityTest.kt`）

```kotlin
@Test fun payloadDeserializesOldJsonWithoutDaySecrets()
@Test fun payloadSerializesWithDefaultsEmptyList()
```

### 5.4 现有测试保护

`WidgetContractSourceTest.providersExposeCraftingProfitAndCombinedModes` 期望三种 Provider 名称 + 三种 info xml 路径精确匹配。该测试**必须**在新增 `DaySecretWidgetProvider` 时**改为**新断言"暴露 4 种模式"，避免在循环 `forEach { ... assert... }` 中漏掉新 Provider。

## 6. 文件清单（最终）

### 新增
- `widget/DaySecretWidgetProvider.kt`
- `widget/DaySecretWidgetConfigureActivity.kt`
- `widget/DaySecretMapPickerContent.kt`（共享 Composable，Configure Activity 与设置页复用）
- `ui/settings/DaySecretMapPickerScreen.kt`（设置页子屏壳，导航入）
- `res/layout/widget_day_secret.xml`
- `res/xml/day_secret_widget_info.xml`
- `app/src/test/.../widget/WidgetRemoteViewsBuilderDaySecretTest.kt`
- `app/src/test/.../widget/WidgetPayloadCompatibilityTest.kt`

### 修改
- `widget/WidgetRemoteViewsBuilder.kt`
- `widget/WidgetRemoteViewsApplier.kt`
- `data/monitor/WidgetPayload.kt`
- `data/monitor/WidgetCache.kt`
- `data/preference/UserPreferencesRepository.kt`（`UserPreferences` + 持久化逻辑）
- `ui/settings/SettingsScreen.kt`、`ui/settings/SettingsViewModel.kt`（设置页子屏 + 共享 Composable）
- `app/src/main/AndroidManifest.xml`
- `res/values/strings.xml`
- `app/src/test/.../widget/WidgetContractSourceTest.kt`

## 7. 风险与权衡

- **首版 Configure Activity 必现一次**：拖放时按 Android 系统规则强制弹；用户在设置页改也行。
- **多账号**：`WidgetCache` 已按 `accountId` 隔离，偏好无需按账号分开（同一玩家同一偏好，简化持久化）。
- **`<ImageButton>` 约束**：测试 `assertTrue(!layout.contains("<ImageButton"))` 不能误加。
- **`<Chronometer>` 约束**：本卡片无倒计时，不引入。
- **HomeScreen 路由变更**：卡片整体 Tap 跳 MainActivity，与现有 widget 一致行为（无）。
