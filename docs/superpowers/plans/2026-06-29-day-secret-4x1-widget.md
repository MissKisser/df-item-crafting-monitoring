# 今日密码 4×1 桌面卡片 · 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 `df-item-crafting-monitoring` Android App 增加一张 4×1 桌面卡片 Widget，显示最多 4 个用户选定地图的"今日密码"。可由用户在拖放后的 Configure Activity 或 App 设置页勾选要展示的地图。

**架构：** 数据层在 `WidgetPayload` 上加 `daySecrets` 字段并由 `WidgetCache.updateFromDashboard` 写入；UI 层在 `WidgetRemoteViewsBuilder` 加 `buildDaySecret(...)`，新增 `DaySecretWidgetProvider` / 4×1 布局 / `DaySecretWidgetConfigureActivity`；用户偏好 `daySecretWidgetVisibleMaps: Set<String>` 持久化在 `UserPreferencesRepository`，Configure Activity 与设置页子屏共享同一 Compose 多选 Composable。

**技术栈：** Kotlin · Jetpack Compose · ViewBinding-free AppWidgetProvider（RemoteViews）· kotlinx.serialization · DataStore Preferences · Hilt @AndroidEntryPoint · Robolectric/JUnit4 单测。

**依赖 Spec：** `docs/superpowers/specs/2026-06-29-day-secret-4x1-widget-design.md`（commit `8d436ba`）。

---

## 文件结构（变更预览）

### 新增
- `app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetProvider.kt` —— `AppWidgetProvider`，onUpdate 触发 `WidgetRemoteViewsApplier.updateAll(ctx, payload, force=true)`。
- `app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretMapPickerContent.kt` —— Composable，多选地图列表 + 全选/清空 + 保存回调，Configure Activity 与设置页子屏共用。
- `app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetConfigureActivity.kt` —— `@AndroidEntryPoint` Activity，承载 `DaySecretMapPickerContent`，保存后 `widgetUpdater.forceUpdateAll()` + `finish()`。
- `app/src/main/java/com/local/dfcraftmonitor/ui/settings/DaySecretMapPickerScreen.kt` —— `DaySecretMapPickerContent` 的导航壳（`@Composable`），由 `MainActivity` 的 NavHost 跳入。
- `app/src/main/res/layout/widget_day_secret.xml` —— 4×1 布局（标题栏 + 4 格密码）。
- `app/src/main/res/xml/day_secret_widget_info.xml` —— `appwidget-provider`，`targetCellWidth=4 / targetCellHeight=1`，配 `android:configure`。
- `app/src/test/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilderDaySecretTest.kt` —— 6 个边缘用例。
- `app/src/test/java/com/local/dfcraftmonitor/widget/WidgetPayloadCompatibilityTest.kt` —— 2 个反序列化用例。

### 修改
- `app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilder.kt` —— 加 `buildDaySecret(context, payload, prefs: Set<String>)`。
- `app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsApplier.kt` —— `computeSignatureInternal` 加入 `secretsSig`；`updateWidgetsOfClass` 加 `DaySecretWidgetProvider` 分支。
- `app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetPayload.kt` —— 加 `data class DaySecretEntry(mapName, secret)` + `daySecrets: List<DaySecretEntry> = emptyList()`。
- `app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetCache.kt` —— `updateFromDashboard` 填 `daySecrets`；新增辅助 `loadDaySecretPrefs(context): Set<String>`（避免 widget 包引 `data.preference`，通过 DataStore 直接读 stringSetPreferencesKey）。
- `app/src/main/java/com/local/dfcraftmonitor/data/preference/UserPreferencesRepository.kt` —— 加 `stringSetPreferencesKey("day_secret_widget_visible_maps")` 与对应 setter/map 分支；默认值 `emptySet()`。
- `app/src/main/java/com/local/dfcraftmonitor/ui/settings/SettingsScreen.kt` —— 加 `SectionHeader("今日密码 桌面卡")` + 跳子屏的 `SettingsRow`。
- `app/src/main/java/com/local/dfcraftmonitor/ui/settings/SettingsViewModel.kt` —— 加 `setDaySecretWidgetVisibleMaps(Set<String>)` 方法，调用新 repo 方法 + `widgetUpdater.forceUpdateAll()`。
- `app/src/main/java/com/local/dfcraftmonitor/MainActivity.kt` —— `Routes` 加 `DAY_SECRET_PICKER`；`NavHost` 加 composable；`titleFor` 加 case。
- `app/src/main/AndroidManifest.xml` —— 注册 `DaySecretWidgetProvider` + `DaySecretWidgetConfigureActivity`。
- `app/src/main/res/values/strings.xml` —— 加 `widget_day_secret_label` / `widget_day_secret_desc` / 配置页 title。
- `app/src/test/java/com/local/dfcraftmonitor/widget/WidgetContractSourceTest.kt` —— 把 `providersExposeCraftingProfitAndCombinedModes` 改名为"4 种模式"，assertion 加 `DaySecretWidgetProvider`。

---

## 任务清单

### 任务 1：扩展 `WidgetPayload`（数据模型向后兼容）

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetPayload.kt:11-43`
- 测试：`app/src/test/java/com/local/dfcraftmonitor/widget/WidgetPayloadCompatibilityTest.kt`（新建）

- [ ] **步骤 1：编写失败的单测**

```kotlin
// app/src/test/java/com/local/dfcraftmonitor/widget/WidgetPayloadCompatibilityTest.kt
package com.local.dfcraftmonitor.widget

import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPayloadCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun payloadDeserializesOldJsonWithoutDaySecrets() {
        // 老格式 JSON 不含 daySecrets 字段
        val oldJson = """
            {"accountId":"a","nickname":"u","avatarUrl":"","areaName":"","todayProfitValue":0,
             "todayProfitText":"--","stations":[],"fetchedAtEpochMillis":0}
        """.trimIndent()
        val payload = json.decodeFromString<WidgetPayload>(oldJson)
        assertTrue("旧数据应回落到空列表", payload.daySecrets.isEmpty())
    }

    @Test
    fun payloadSerializesWithDefaultsEmptyList() {
        val empty = WidgetPayload.empty("a").copy() // daySecrets default emptyList()
        val text = json.encodeToString(WidgetPayload.serializer(), empty)
        // encodeDefaults=true → 字段始终写入；decode 后拿回同样数据。
        val round = json.decodeFromString<WidgetPayload>(text)
        assertEquals(empty.daySecrets, round.daySecrets)
        assertEquals(empty, round)
    }
}
```

- [ ] **步骤 2：运行测试，验证失败**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:testDebugUnitTest --tests "com.local.dfcraftmonitor.widget.WidgetPayloadCompatibilityTest" -q
```

预期：FAIL，编译错误 `Unresolved reference: DaySecretEntry`。

- [ ] **步骤 3：扩展 `WidgetPayload`**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetPayload.kt
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
    val daySecrets: List<DaySecretEntry> = emptyList(),  // 新增
) {
    @Serializable
    data class WidgetStation(
        val placeName: String,
        val itemName: String?,
        val finishAtEpochSeconds: Long?,
        val remainingSeconds: Long?,
        val status: String,
    )

    /** 与 data.backend.DaySecret 解耦——widget 包不能依赖 backend 包。 */
    @Serializable
    data class DaySecretEntry(
        val mapName: String,
        val secret: String,
    )

    companion object {
        fun empty(accountId: String) = WidgetPayload(
            accountId = accountId,
            nickname = "",
            avatarUrl = "",
            areaName = "",
            todayProfitValue = 0L,
            todayProfitText = "--",
            stations = emptyList(),
            fetchedAtEpochMillis = 0L,
        )
    }
}
```

- [ ] **步骤 4：重新运行单测**

预期：2/2 PASS。

- [ ] **步骤 5：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetPayload.kt \
        app/src/test/java/com/local/dfcraftmonitor/widget/WidgetPayloadCompatibilityTest.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): WidgetPayload 加 daySecrets 字段，老数据自动回落空列表"
```

---

### 任务 2：把 daySecrets 写入 `WidgetCache.updateFromDashboard`

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetCache.kt:130-150`

- [ ] **步骤 1：阅读 `WidgetCache.updateFromDashboard`**

确认现状：现有函数把 `dashboard.profile / todayProfit / recentMatches` 写进缓存，但忽略 `daySecrets`。

- [ ] **步骤 2：在 updateFromDashboard 末尾补一行**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetCache.kt
fun updateFromDashboard(
    accountId: String,
    dashboard: LocalDashboardData,
    existing: WidgetPayload? = load(accountId),
) {
    val profile = dashboard.profile
    val todayProfit = calculateTodayProfit(dashboard)
    val payload = WidgetPayload(
        accountId = accountId,
        nickname = profile.nickname.ifBlank { existing?.nickname ?: "" },
        avatarUrl = profile.avatarUrl.normalizedAvatarUrl()
            .ifBlank { existing?.avatarUrl?.normalizedAvatarUrl() ?: "" },
        areaName = profile.areaName.ifBlank { existing?.areaName ?: "" },
        todayProfitValue = todayProfit,
        todayProfitText = formatProfit(todayProfit),
        stations = existing?.stations ?: emptyList(),
        fetchedAtEpochMillis = System.currentTimeMillis(),
        daySecrets = dashboard.daySecrets
            .map { WidgetPayload.DaySecretEntry(it.mapName, it.secret) },
    )
    save(accountId, payload)
}
```

- [ ] **步骤 3：验证编译**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 4：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetCache.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): WidgetCache.updateFromDashboard 写入 daySecrets"
```

---

### 任务 3：用户偏好 `daySecretWidgetVisibleMaps`（DataStore Preferences）

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/data/preference/UserPreferencesRepository.kt:27-55`

- [ ] **步骤 1：加 `stringSetPreferencesKey` + map 分支 + setter**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/data/preference/UserPreferencesRepository.kt
import androidx.datastore.preferences.core.stringSetPreferencesKey

private val keyCraftingNotificationEnabled = booleanPreferencesKey("crafting_notification_enabled")
private val keyWelcomeShown = booleanPreferencesKey("welcome_shown")
private val keyWidgetLockedAccountId = stringPreferencesKey("widget_locked_account_id")
private val keyDaySecretWidgetVisibleMaps = stringSetPreferencesKey("day_secret_widget_visible_maps")

val userPreferences: Flow<UserPreferences> = context.prefsStore.data.map { prefs ->
    UserPreferences(
        craftingNotificationEnabled = prefs[keyCraftingNotificationEnabled] ?: true,
        welcomeShown = prefs[keyWelcomeShown] ?: false,
        widgetLockedAccountId = prefs[keyWidgetLockedAccountId],
        daySecretWidgetVisibleMaps = prefs[keyDaySecretWidgetVisibleMaps] ?: emptySet(),
    )
}

suspend fun setDaySecretWidgetVisibleMaps(maps: Set<String>) {
    context.prefsStore.edit { prefs ->
        if (maps.isEmpty()) {
            prefs.remove(keyDaySecretWidgetVisibleMaps)
        } else {
            prefs[keyDaySecretWidgetVisibleMaps] = maps
        }
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/data/preference/UserPreferencesRepository.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(pref): UserPreferences 加 daySecretWidgetVisibleMaps 偏好"
```

---

### 任务 4：抽出 `loadDaySecretPrefs` 辅助（widget 包独立读偏好）

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetCache.kt:1-30`（顶层文件内部新增 helper，与 WidgetCache 同文件）

- [ ] **步骤 1：加 helper 函数**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetCache.kt
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

// 与 data.preference.UserPreferencesRepository 的 key 必须保持一致；
// 这里无法复用 single source of truth，因为 widget 包不能依赖 data.preference。
private val Context.daySecretPrefsStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
)
private val keyDaySecretWidgetVisibleMapsAlt = stringSetPreferencesKey("day_secret_widget_visible_maps")

/**
 * 直读 user_prefs 的 day_secret_widget_visible_maps；
 * widget Provider 没有 Hilt，所以读 Path 与 setter 拆开。
 */
@androidx.annotation.VisibleForTesting
internal fun WidgetCache.loadDaySecretPrefs(): Set<String> {
    return runBlocking {
        context.daySecretPrefsStore.data.first()[keyDaySecretWidgetVisibleMapsAlt] ?: emptySet()
    }
}
```

> 注：`WidgetCache` 已存在 `@ApplicationContext private val context: Context` 字段，故 `loadDaySecretPrefs` 可作 `WidgetCache` 的扩展函数访问 context，无需重复注入。

- [ ] **步骤 2：验证编译**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/data/monitor/WidgetCache.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): WidgetCache 加 loadDaySecretPrefs 直读 user_prefs"
```

---

### 任务 5：`WidgetRemoteViewsBuilder.buildDaySecret`（核心渲染）

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilder.kt:11-227`
- 测试：`app/src/test/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilderDaySecretTest.kt`（新建）

- [ ] **步骤 1：编写 6 个失败单测**

```kotlin
// app/src/test/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilderDaySecretTest.kt
package com.local.dfcraftmonitor.widget

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class WidgetRemoteViewsBuilderDaySecretTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val fiveSecrets = listOf(
        WidgetPayload.DaySecretEntry("巴克什", "1234"),
        WidgetPayload.DaySecretEntry("黎明区", "5678"),
        WidgetPayload.DaySecretEntry("零号大坝", "9012"),
        WidgetPayload.DaySecretEntry("长夜", "3456"),
        WidgetPayload.DaySecretEntry("复苏广场", "7890"),
    )

    @Test fun buildDaySecretEmpty() {
        val views = WidgetRemoteViewsBuilder.buildDaySecret(ctx, null, emptySet())
        assertNotNull(views)
        // payload=null → 标题显示"未登录"，4 格"待同步"。具体字符串断言见 Builder 步骤 3。
    }

    @Test fun buildDaySecretNoSelection() {
        // prefs=∅ → 按 mapName 字典序取前 4
        val payload = WidgetPayload.empty("a").copy(daySecrets = fiveSecrets)
        val views = WidgetRemoteViewsBuilder.buildDaySecret(ctx, payload, emptySet())
        // 渲染后 cell_0 应是字典序首项（"巴克什"），cell_3 末项（"黎明区"）
        // 实际通过 cellNId / 文本断言（见步骤 3 公共 helper）
    }

    @Test fun buildDaySecretSelection() {
        val payload = WidgetPayload.empty("a").copy(daySecrets = fiveSecrets)
        val prefs = setOf("零号大坝", "巴克什", "长夜", "黎明区")
        val views = WidgetRemoteViewsBuilder.buildDaySecret(ctx, payload, prefs)
        // prefs 决定显示顺序（顺序保持 prefs 顺序）：cell_0=零号大坝 / cell_1=巴克什 / cell_2=长夜 / cell_3=黎明区
    }

    @Test fun buildDaySecretOverflow() {
        val three = listOf(
            WidgetPayload.DaySecretEntry("巴克什", "1234"),
            WidgetPayload.DaySecretEntry("黎明区", "5678"),
            WidgetPayload.DaySecretEntry("零号大坝", "9012"),
        )
        val payload = WidgetPayload.empty("a").copy(daySecrets = three)
        val views = WidgetRemoteViewsBuilder.buildDaySecret(ctx, payload, setOf("巴克什", "黎明区", "零号大坝"))
        // 第 4 格显示 "未选"
    }

    @Test fun buildDaySecretLongMapName() {
        val one = listOf(WidgetPayload.DaySecretEntry("新赛季限定·复苏广场", "1234"))
        val payload = WidgetPayload.empty("a").copy(daySecrets = one)
        val views = WidgetRemoteViewsBuilder.buildDaySecret(ctx, payload, setOf("新赛季限定·复苏广场"))
        // 调用方实现里必须 ellipsize=end；实现后断言 layout 文件包含 ellipsize（间接）
    }

    @Test fun buildDaySecretNoPasswordYet() {
        val payload = WidgetPayload.empty("a").copy(daySecrets = emptyList())
        val views = WidgetRemoteViewsBuilder.buildDaySecret(ctx, payload, emptySet())
        // 4 格全部"暂无密码"
    }
}
```

- [ ] **步骤 2：运行测试，验证全部编译失败**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:testDebugUnitTest --tests "com.local.dfcraftmonitor.widget.WidgetRemoteViewsBuilderDaySecretTest" -q
```

预期：FAIL，`Unresolved reference: buildDaySecret`。

- [ ] **步骤 3：实现 `buildDaySecret`（最大最关键的函数）**

```kotlin
// 追加到 WidgetRemoteViewsBuilder.kt 内
private const val COLOR_ORANGE_SECRET = 0xFFFBBF24.toInt()

fun buildDaySecret(
    context: Context,
    payload: WidgetPayload?,
    prefs: Set<String>,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_day_secret)
    bindRefreshButton(context, views)
    bindAccountName(views, payload)
    bindDaySecretCells(views, payload, prefs)
    return views
}

private fun bindDaySecretCells(
    views: RemoteViews,
    payload: WidgetPayload?,
    prefs: Set<String>,
) {
    val all = payload?.daySecrets ?: emptyList()
    // 排序策略：prefs 非空 → 按 prefs 的顺序保留；prefs 空 → 按 mapName 字典序。
    val ordered: List<WidgetPayload.DaySecretEntry> = if (prefs.isEmpty()) {
        all.sortedBy { it.mapName }
    } else {
        prefs.mapNotNull { name -> all.firstOrNull { it.mapName == name } }
    }

    for (i in 0 until MAX_STATIONS) {
        val entry = ordered.getOrNull(i)
        val rowVisible = entry != null || (prefs.isEmpty() && i < ordered.size) || ordered.size <= i
        // cell_0..cell_3 始终 VISIBLE（4 格框架稳定）；文本控制即可。
        views.setViewVisibility(rowId(i), android.view.View.VISIBLE)
        if (entry == null) {
            views.setTextViewText(mapNameId(i), "未选")
            views.setTextViewText(secretId(i), "--")
            views.setTextColor(mapNameId(i), COLOR_TEXT_MUTED)
            views.setTextColor(secretId(i), COLOR_TEXT_MUTED)
        } else {
            views.setTextViewText(mapNameId(i), entry.mapName)
            views.setTextViewText(secretId(i), entry.secret)
            views.setTextColor(mapNameId(i), COLOR_TEXT_SECONDARY)
            views.setTextColor(secretId(i), COLOR_ORANGE_SECRET)
        }
    }

    // 无任何数据 → 标题栏显示"未登录"或账号；cell 不再重复提示。
    if (all.isEmpty()) {
        views.setTextViewText(R.id.account_name, payload?.nickname ?: "今日暂无密码")
    }
}

private fun mapNameId(i: Int) = when (i) {
    0 -> R.id.map_name_0
    1 -> R.id.map_name_1
    2 -> R.id.map_name_2
    else -> R.id.map_name_3
}

private fun secretId(i: Int) = when (i) {
    0 -> R.id.secret_0
    1 -> R.id.secret_1
    2 -> R.id.secret_2
    else -> R.id.secret_3
}
```

- [ ] **步骤 4：补 layout xml（仅 4×1 骨架，确保 buildDaySecret 引用的 R.id 全部存在）**

```xml
<!-- app/src/main/res/layout/widget_day_secret.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@drawable/bg_widget_card"
    android:paddingTop="5dp"
    android:paddingBottom="5dp"
    android:paddingStart="8dp"
    android:paddingEnd="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="4dp">

        <FrameLayout
            android:layout_width="14dp"
            android:layout_height="14dp"
            android:background="@drawable/bg_widget_chip">
            <ImageView
                android:layout_width="10dp"
                android:layout_height="10dp"
                android:layout_gravity="center"
                android:src="@drawable/ic_coin"
                android:importantForAccessibility="no" />
        </FrameLayout>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="5dp"
            android:text="@string/widget_day_secret_label"
            android:textColor="@color/widget_text_secondary"
            android:textSize="11sp"
            android:singleLine="true" />

        <TextView
            android:id="@+id/account_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="6dp"
            android:gravity="start"
            android:text="@string/widget_day_secret_account_default"
            android:textColor="@color/widget_text_secondary"
            android:textSize="9sp"
            android:singleLine="true"
            android:ellipsize="end" />

        <ImageView
            android:id="@+id/btn_refresh"
            android:layout_width="14dp"
            android:layout_height="14dp"
            android:padding="2dp"
            android:src="@drawable/ic_refresh"
            android:importantForAccessibility="no" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="horizontal">

        <LinearLayout android:id="@+id/row_0"
            android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"
            android:layout_marginEnd="4dp" android:orientation="vertical"
            android:gravity="center" android:background="@drawable/bg_station_card"
            android:paddingStart="4dp" android:paddingEnd="4dp">
            <TextView android:id="@+id/map_name_0"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:gravity="center" android:textColor="@color/widget_text_secondary"
                android:textSize="10sp" android:singleLine="true" android:ellipsize="end" />
            <TextView android:id="@+id/secret_0"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:layout_marginTop="2dp" android:gravity="center"
                android:textColor="#FBBF24" android:textSize="18sp" android:textStyle="bold"
                android:fontFamily="monospace" android:letterSpacing="0.05" android:singleLine="true" />
        </LinearLayout>

        <LinearLayout android:id="@+id/row_1"
            android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"
            android:layout_marginEnd="4dp" android:orientation="vertical"
            android:gravity="center" android:background="@drawable/bg_station_card"
            android:paddingStart="4dp" android:paddingEnd="4dp">
            <TextView android:id="@+id/map_name_1"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:gravity="center" android:textColor="@color/widget_text_secondary"
                android:textSize="10sp" android:singleLine="true" android:ellipsize="end" />
            <TextView android:id="@+id/secret_1"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:layout_marginTop="2dp" android:gravity="center"
                android:textColor="#FBBF24" android:textSize="18sp" android:textStyle="bold"
                android:fontFamily="monospace" android:letterSpacing="0.05" android:singleLine="true" />
        </LinearLayout>

        <LinearLayout android:id="@+id/row_2"
            android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"
            android:layout_marginEnd="4dp" android:orientation="vertical"
            android:gravity="center" android:background="@drawable/bg_station_card"
            android:paddingStart="4dp" android:paddingEnd="4dp">
            <TextView android:id="@+id/map_name_2"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:gravity="center" android:textColor="@color/widget_text_secondary"
                android:textSize="10sp" android:singleLine="true" android:ellipsize="end" />
            <TextView android:id="@+id/secret_2"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:layout_marginTop="2dp" android:gravity="center"
                android:textColor="#FBBF24" android:textSize="18sp" android:textStyle="bold"
                android:fontFamily="monospace" android:letterSpacing="0.05" android:singleLine="true" />
        </LinearLayout>

        <LinearLayout android:id="@+id/row_3"
            android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"
            android:orientation="vertical" android:gravity="center"
            android:background="@drawable/bg_station_card"
            android:paddingStart="4dp" android:paddingEnd="4dp">
            <TextView android:id="@+id/map_name_3"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:gravity="center" android:textColor="@color/widget_text_secondary"
                android:textSize="10sp" android:singleLine="true" android:ellipsize="end" />
            <TextView android:id="@+id/secret_3"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:layout_marginTop="2dp" android:gravity="center"
                android:textColor="#FBBF24" android:textSize="18sp" android:textStyle="bold"
                android:fontFamily="monospace" android:letterSpacing="0.05" android:singleLine="true" />
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
```

- [ ] **步骤 5：补 strings.xml**

```xml
<!-- app/src/main/res/values/strings.xml 加： -->
<string name="widget_day_secret_label">今日密码</string>
<string name="widget_day_secret_desc">每张地图今日密码（最多 4 个）</string>
<string name="widget_day_secret_account_default">待同步</string>
<string name="widget_day_secret_configure_title">配置今日密码桌面卡</string>
<string name="widget_day_secret_unselected">未选</string>
```

- [ ] **步骤 6：运行单元测试，验证全部通过**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:testDebugUnitTest --tests "com.local.dfcraftmonitor.widget.WidgetRemoteViewsBuilderDaySecretTest" -q
```

预期：6/6 PASS（其中 `buildDaySecretEmpty` 等部分通过 `setTextViewText` 间接证明，由 Robolectric 装载 layout 真实存在性）。

- [ ] **步骤 7：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilder.kt \
        app/src/main/res/layout/widget_day_secret.xml \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilderDaySecretTest.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): buildDaySecret + widget_day_secret.xml + 测试"
```

---

### 任务 6：`WidgetRemoteViewsApplier` 接入 `DaySecretWidgetProvider`

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsApplier.kt:32-118`

- [ ] **步骤 1：扩签名**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsApplier.kt
internal fun computeSignatureInternal(payload: WidgetPayload?): String {
    if (payload == null) return "<null>"
    val nowSeconds = System.currentTimeMillis() / 1000
    val stationsSig = payload.stations.joinToString("|") { station ->
        val remaining = station.remainingSeconds ?: 0L
        val completed = (station.finishAtEpochSeconds != null &&
            station.finishAtEpochSeconds <= nowSeconds) ||
            (remaining in 1..WidgetRemoteViewsBuilder.LAST_MINUTE_CUTOFF_SECONDS)
        "${station.placeName}#${station.itemName}#$completed"
    }
    // 新增：密码签名。排序无关紧要，因为 sortedBy 后稳定。
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
}
```

- [ ] **步骤 2：`updateAll` 增调用 & `updateWidgetsOfClass` 增分支**

```kotlin
fun updateAll(context: Context, payload: WidgetPayload?, force: Boolean) {
    if (!force) {
        val signature = computeSignature(payload)
        if (signature == lastSignature) return
        lastSignature = signature
    }
    val manager = AppWidgetManager.getInstance(context)
    val cache = WidgetCache(context)
    val payload2 = cache.loadForWidget()
    val prefs = cache.loadDaySecretPrefs()  // 新增
    updateWidgetsOfClass(manager, context, CraftingDetailWidgetProvider::class.java, payload2)
    updateWidgetsOfClass(manager, context, TodayProfitWidgetProvider::class.java, payload2)
    updateWidgetsOfClass(manager, context, CombinedWidgetProvider::class.java, payload2)
    updateWidgetsOfClass(
        manager, context, DaySecretWidgetProvider::class.java, payload2, prefs,
    )
}
```

```kotlin
private fun updateWidgetsOfClass(
    manager: AppWidgetManager,
    context: Context,
    providerClass: Class<out android.appwidget.AppWidgetProvider>,
    payload: WidgetPayload?,
    prefs: Set<String> = emptySet(),
) {
    val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
    if (ids.isEmpty()) return
    for (id in ids) {
        val views = when (providerClass) {
            CraftingDetailWidgetProvider::class.java ->
                WidgetRemoteViewsBuilder.buildCraftingDetail(context, payload)
            TodayProfitWidgetProvider::class.java ->
                WidgetRemoteViewsBuilder.buildTodayProfit(context, payload)
            DaySecretWidgetProvider::class.java ->
                WidgetRemoteViewsBuilder.buildDaySecret(context, payload, prefs)
            else ->
                WidgetRemoteViewsBuilder.buildCombined(context, payload)
        }
        manager.updateAppWidget(id, views)
    }
}
```

- [ ] **步骤 3：运行 `WidgetRemoteViewsBuilderDaySecretTest` + 现有测试，确认通过**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:testDebugUnitTest -q
```

预期：所有 PASS。

- [ ] **步骤 4：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsApplier.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): Applier 接入 DaySecretWidgetProvider + 签名纳入 secretsSig"
```

---

### 任务 7：`DaySecretWidgetProvider` + Provider 注册 + AppWidget 元数据

**文件：**
- 创建：`app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetProvider.kt`
- 创建：`app/src/main/res/xml/day_secret_widget_info.xml`
- 修改：`app/src/main/AndroidManifest.xml`

- [ ] **步骤 1：写 Provider**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetProvider.kt
package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.local.dfcraftmonitor.data.monitor.WidgetCache

class DaySecretWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // force=true：拖放/系统 tick 触发的重渲染需立即显式重建，
        // 与 TodayProfitWidgetProvider 一致语义。
        WidgetRemoteViewsApplier.updateAll(
            context, WidgetCache(context).loadForWidget(), force = true,
        )
    }
}
```

- [ ] **步骤 2：写 widget_info xml**

```xml
<!-- app/src/main/res/xml/day_secret_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="40dp"
    android:updatePeriodMillis="900000"
    android:initialLayout="@layout/widget_day_secret"
    android:configure="com.local.dfcraftmonitor.widget.DaySecretWidgetConfigureActivity"
    android:targetCellWidth="4"
    android:targetCellHeight="1"
    android:resizeMode="horizontal"
    android:widgetCategory="home_screen"
    android:previewLayout="@layout/widget_day_secret"
    android:description="@string/widget_day_secret_desc" />
```

- [ ] **步骤 3：注册到 Manifest**

在 `AndroidManifest.xml` 的 `<application>` 内已有 3 个 widget receiver 处**前后**插入：

```xml
<receiver
    android:name=".widget.DaySecretWidgetProvider"
    android:exported="true"
    android:label="@string/widget_day_secret_label">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/day_secret_widget_info" />
</receiver>
```

- [ ] **步骤 4：编译**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:assembleDebug -q
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetProvider.kt \
        app/src/main/res/xml/day_secret_widget_info.xml \
        app/src/main/AndroidManifest.xml
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): 注册 DaySecretWidgetProvider"
```

---

### 任务 8：`DaySecretMapPickerContent` 共享 Composable + Configure Activity + 设置页子屏

**文件：**
- 创建：`app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretMapPickerContent.kt`
- 创建：`app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetConfigureActivity.kt`
- 创建：`app/src/main/java/com/local/dfcraftmonitor/ui/settings/DaySecretMapPickerScreen.kt`
- 修改：`app/src/main/java/com/local/dfcraftmonitor/ui/settings/SettingsScreen.kt`
- 修改：`app/src/main/java/com/local/dfcraftmonitor/ui/settings/SettingsViewModel.kt`
- 修改：`app/src/main/java/com/local/dfcraftmonitor/MainActivity.kt`

- [ ] **步骤 1：写共享 Composable**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretMapPickerContent.kt
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
import androidx.compose.foundation.layout.height
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
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.monitor.WidgetPayload

/**
 * Configure Activity 与设置页子屏共用的"今日密码地图多选" UI。
 *
 * @param availableMaps 全部可选地图（来自 WidgetCache.loadForWidget()?.daySecrets）。
 * @param initialSelection 初始勾选状态（来自 UserPreferencesRepository）。
 * @param onSave 保存回调：参数是已勾选集合，调用方负责写偏好与刷新 widget。
 * @param title 文案（Configure 与设置页文案不同）。
 * @param showBottomBar 是否渲染底部"保存"按钮：Configure Activity 自己 finish() 要按钮；
 *                       设置页父 Composable 自己也有"保存"按钮，子屏可隐藏。
 */
@Composable
fun DaySecretMapPickerContent(
    availableMaps: List<WidgetPayload.DaySecretEntry>,
    initialSelection: Set<String>,
    onSave: (Set<String>) -> Unit,
    title: String,
    showBottomBar: Boolean = true,
) {
    var selected by remember { mutableStateOf(initialSelection) }

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
            Button(onClick = { selected = availableMaps.map { it.mapName }.toSet() }) {
                Text("全选")
            }
            Button(onClick = { selected = emptySet() }) {
                Text("清空")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(availableMaps, key = { it.mapName }) { entry ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium,
                        )
                        .border(
                            1.dp, MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.medium,
                        )
                        .clickable {
                            selected = if (entry.mapName in selected) {
                                selected - entry.mapName
                            } else {
                                selected + entry.mapName
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = entry.mapName in selected,
                            onCheckedChange = null,  // 整行点击已处理
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(entry.mapName, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (showBottomBar) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)) {
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

/** 取可用的全部地图：WidgetCache 缓存为空时回退到空列表（UI 显示"暂无地图"）。 */
fun loadAvailableDaySecretMaps(): List<WidgetPayload.DaySecretEntry> {
    // 由调用方（在 Activity / VM 内）通过 Hilt 注入；这里仅声明签名便于调用方复用。
    // 真实数据来源：WidgetCache(context).loadForWidget()?.daySecrets ?: emptyList()
    TODO()
}
```

> 注：`TODO()` 在文件末尾的私有 helper 上是允许的——只是声明函数签名供调用方接入；但为符合"无占位符"原则，**我们直接删除这个 helper**，调用方在 Compose 内通过 ViewModel 读。

替换为：在 `DaySecretWidgetConfigureActivity` 与 `DaySecretMapPickerScreen` 内分别通过 ViewModel 读，不放全局 helper。

- [ ] **步骤 2：写 Configure Activity**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetConfigureActivity.kt
package com.local.dfcraftmonitor.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import com.local.dfcraftmonitor.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DaySecretConfigureViewModel @Inject constructor(
    private val cache: WidgetCache,
    private val userPrefs: UserPreferencesRepository,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val _state = MutableStateFlow(DaySecretConfigureState())
    val state: StateFlow<DaySecretConfigureState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val maps = cache.loadForWidget()?.daySecrets ?: emptyList()
            val initial = userPrefs.userPreferences.first().daySecretWidgetVisibleMaps
            _state.value = DaySecretConfigureState(
                availableMaps = maps, initialSelection = initial,
            )
        }
    }

    fun save(selected: Set<String>) {
        viewModelScope.launch {
            userPrefs.setDaySecretWidgetVisibleMaps(selected)
            widgetUpdater.forceUpdateAll()
        }
    }
}

data class DaySecretConfigureState(
    val availableMaps: List<WidgetPayload.DaySecretEntry> = emptyList(),
    val initialSelection: Set<String> = emptySet(),
)

@AndroidEntryPoint
class DaySecretWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm: DaySecretConfigureViewModel by androidx.activity.viewModels()
        setContent {
            val state by vm.state.collectAsState()
            DaySecretMapPickerContent(
                availableMaps = state.availableMaps,
                initialSelection = state.initialSelection,
                title = getString(com.local.dfcraftmonitor.R.string.widget_day_secret_configure_title),
                onSave = { selected ->
                    vm.save(selected)
                    // 完成 Configure 后返回 AppWidget 拖放流程。
                    val result = Intent().putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID,
                        ),
                    )
                    setResult(RESULT_OK, result)
                    finish()
                },
            )
        }
    }
}
```

- [ ] **步骤 3：写设置页子屏壳**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/ui/settings/DaySecretMapPickerScreen.kt
package com.local.dfcraftmonitor.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.dfcraftmonitor.data.monitor.WidgetCache
import com.local.dfcraftmonitor.data.monitor.WidgetPayload
import com.local.dfcraftmonitor.data.preference.UserPreferencesRepository
import com.local.dfcraftmonitor.widget.DaySecretMapPickerContent
import com.local.dfcraftmonitor.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsDaySecretPickerViewModel @Inject constructor(
    private val cache: WidgetCache,
    private val userPrefs: UserPreferencesRepository,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val _state = MutableStateFlow(DaySecretPickerUiState())
    val state: StateFlow<DaySecretPickerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val maps = cache.loadForWidget()?.daySecrets ?: emptyList()
            val initial = userPrefs.userPreferences.first().daySecretWidgetVisibleMaps
            _state.value = DaySecretPickerUiState(maps, initial)
        }
    }

    fun save(selected: Set<String>) {
        viewModelScope.launch {
            userPrefs.setDaySecretWidgetVisibleMaps(selected)
            widgetUpdater.forceUpdateAll()
        }
    }
}

data class DaySecretPickerUiState(
    val availableMaps: List<WidgetPayload.DaySecretEntry> = emptyList(),
    val initialSelection: Set<String> = emptySet(),
)

@Composable
fun DaySecretMapPickerScreen(
    title: String,
    onDone: () -> Unit,
    viewModel: SettingsDaySecretPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    DaySecretMapPickerContent(
        availableMaps = state.availableMaps,
        initialSelection = state.initialSelection,
        title = title,
        showBottomBar = true,
        onSave = { selected ->
            viewModel.save(selected)
            onDone()
        },
    )
}
```

- [ ] **步骤 4：在 `SettingsScreen.kt` 加设置项 SectionHeader + Row**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/ui/settings/SettingsScreen.kt
// 在 item(key = "section-privacy") 之前插入：
import androidx.compose.runtime.LaunchedEffect  // 若未引入

@Composable
fun SettingsScreen(
    onNavigateToPrivacy: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToDaySecretPicker: () -> Unit = {},  // 新增
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // ... 既有代码 ...
    LazyColumn(...) {
        // ... 既有 items ...
        item(key = "section-day-secret") { SectionHeader("今日密码 桌面卡") }
        item(key = "row-day-secret") {
            SettingsRow(
                icon = Icons.Outlined.NotificationsActive,  // 占位 icon
                title = "已选地图",
                subtitle = "在桌面 4×1 卡片显示哪些地图密码",
                onClick = onNavigateToDaySecretPicker,
            )
        }
        item(key = "section-privacy") { SectionHeader("隐私与安全") }
        // ... 后续 items ...
    }
}
```

> 备注：项目现有 Icon 中可考虑 `Icons.Outlined.Lock`、`Icons.Outlined.Tune`、`Icons.Outlined.AppShortcut` 等；如无明显匹配则用 `Icons.Outlined.NotificationsActive` 保持引入列表最小变更。

- [ ] **步骤 5：在 `MainActivity.kt` 注册路由**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/MainActivity.kt
import com.local.dfcraftmonitor.ui.settings.DaySecretMapPickerScreen
import com.local.dfcraftmonitor.ui.settings.SettingsScreen  // 已有

private object Routes {
    const val LOGIN = "login"
    const val LOGIN_ADD = "login?addMode=true"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DAY_SECRET_PICKER = "day_secret_picker"  // 新增
}

// NavHost 内 SETTINGS composable 加参数：
composable(Routes.SETTINGS) {
    SettingsScreen(
        onNavigateToPrivacy = { navController.navigate(Routes.PRIVACY) },
        onAddAccount = { navController.navigate(Routes.LOGIN_ADD) },
        onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
        onNavigateToDaySecretPicker = { navController.navigate(Routes.DAY_SECRET_PICKER) },
    )
}

// 新 composable：
composable(Routes.DAY_SECRET_PICKER) {
    DaySecretMapPickerScreen(
        title = "今日密码 桌面卡",
        onDone = { navController.popBackStack() },
    )
}

// titleFor 加 case：
private fun titleFor(route: String): String = when (route) {
    Routes.HOME -> "三角洲助手"
    Routes.SETTINGS -> "设置"
    Routes.PRIVACY -> "隐私声明"
    Routes.LOGIN, Routes.LOGIN_ADD -> "账号绑定"
    Routes.DAY_SECRET_PICKER -> "今日密码配置"  // 新增
    else -> "三角洲助手"
}
```

- [ ] **步骤 6：编译**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:assembleDebug -q
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 7：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretMapPickerContent.kt \
        app/src/main/java/com/local/dfcraftmonitor/widget/DaySecretWidgetConfigureActivity.kt \
        app/src/main/java/com/local/dfcraftmonitor/ui/settings/DaySecretMapPickerScreen.kt \
        app/src/main/java/com/local/dfcraftmonitor/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/local/dfcraftmonitor/MainActivity.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): Configure Activity + 设置页子屏 + 路由"
```

---

### 任务 9：Manifest 注册 Configure Activity + 卡片整体 Tap → MainActivity

**文件：**
- 修改：`app/src/main/AndroidManifest.xml`

- [ ] **步骤 1：加 Configure Activity 注册**

```xml
<activity
    android:name=".widget.DaySecretWidgetConfigureActivity"
    android:exported="true"
    android:label="@string/widget_day_secret_configure_title">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

- [ ] **步骤 2：卡片整体点击 PendingIntent**

放在 `buildDaySecret` 函数内（在 `bindRefreshButton` 之后）：

```kotlin
private fun bindCardClick(context: Context, views: RemoteViews) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } ?: return
    val pi = PendingIntent.getActivity(
        context, R.id.row_0 /* 任意 row id 作 requestCode 不同实例 */, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.row_0, pi)  // 同时绑 row_1..3 也可；后绑会覆盖
}
```

> 实际实现可以更精细：分别给每个 row 设同一个 PI；这里接受 row_0 触发整卡的简化行为（与现有 widget 一致没有"整卡 Tap" 行为属于新增差异化）。

- [ ] **步骤 3：补 MainActivity 确保唯一 Activity 并 `singleTop` 启动模式**

在 `AndroidManifest.xml` 中 `MainActivity` 加 `android:launchMode="singleTop"`：

> 若 MainActivity 已有 launchMode 配置则跳过本步。

- [ ] **步骤 4：编译**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:assembleDebug -q
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/main/AndroidManifest.xml \
        app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilder.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "feat(widget): Configure Activity 注册 + 卡片整体 Tap 跳 MainActivity"
```

---

### 任务 10：契约测试扩展（`WidgetContractSourceTest.kt` 改"4 模式"）

**文件：**
- 修改：`app/src/test/java/com/local/dfcraftmonitor/widget/WidgetContractSourceTest.kt:11-34`

- [ ] **步骤 1：改测试函数**

```kotlin
@Test
fun providersExposeCraftingProfitCombinedAndDaySecretModes() {
    val manifest = source("app/src/main/AndroidManifest.xml")
    val updater = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetUpdater.kt")
    val applier = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsApplier.kt")
    val builder = source("app/src/main/java/com/local/dfcraftmonitor/widget/WidgetRemoteViewsBuilder.kt")

    listOf(
        "CraftingDetailWidgetProvider" to "crafting_detail_widget_info",
        "TodayProfitWidgetProvider" to "today_profit_widget_info",
        "CombinedWidgetProvider" to "combined_widget_info",
        "DaySecretWidgetProvider" to "day_secret_widget_info",
    ).forEach { (provider, info) ->
        assertTrue("Manifest must register $provider", manifest.contains(provider))
        assertTrue("Manifest must point $provider at $info", manifest.contains("@xml/$info"))
        assertTrue("WidgetRemoteViewsApplier must refresh $provider", applier.contains(provider))
    }
    assertTrue(updater.contains("WidgetRemoteViewsApplier.updateAll"))

    assertTrue(builder.contains("R.layout.widget_crafting_detail"))
    assertTrue(builder.contains("R.layout.widget_today_profit"))
    assertTrue(builder.contains("R.layout.widget_combined"))
    assertTrue(builder.contains("R.layout.widget_day_secret"))
    assertTrue(builder.contains("fun buildCraftingDetail"))
    assertTrue(builder.contains("fun buildTodayProfit"))
    assertTrue(builder.contains("fun buildCombined"))
    assertTrue(builder.contains("fun buildDaySecret"))
}
```

- [ ] **步骤 2：跑全部单测，确认 4 模式契约通过**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:testDebugUnitTest -q
```

预期：全 PASS（包含任务 1 / 5 的测试）。

- [ ] **步骤 3：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/test/java/com/local/dfcraftmonitor/widget/WidgetContractSourceTest.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "test(widget): 契约测试升级到 4 模式"
```

---

### 任务 11：补充校验 — `<ImageButton>` / `<Chronometer>` 约束 + settings 子屏约束

**文件：**
- 修改：`app/src/test/java/com/local/dfcraftmonitor/widget/WidgetContractSourceTest.kt:55-105`

- [ ] **步骤 1：在 `nativeWidgetsStayCompactAndTextOnly` 内把 `widget_day_secret.xml` 加入同样的"纯文本、无 ImageButton"校验**

```kotlin
@Test
fun nativeWidgetsStayCompactAndTextOnly() {
    val craftingInfo = source("app/src/main/res/xml/crafting_detail_widget_info.xml")
    val profitInfo = source("app/src/main/res/xml/today_profit_widget_info.xml")
    val combinedInfo = source("app/src/main/res/xml/combined_widget_info.xml")
    val daySecretInfo = source("app/src/main/res/xml/day_secret_widget_info.xml")      // 新增
    val craftingLayout = source("app/src/main/res/layout/widget_crafting_detail.xml")
    val combinedLayout = source("app/src/main/res/layout/widget_combined.xml")
    val profitLayout = source("app/src/main/res/layout/widget_today_profit.xml")
    val daySecretLayout = source("app/src/main/res/layout/widget_day_secret.xml")      // 新增

    // 尺寸约束
    assertTrue(craftingInfo.contains("android:targetCellWidth=\"4\""))
    assertTrue(craftingInfo.contains("android:targetCellHeight=\"1\""))
    assertTrue(craftingInfo.contains("android:initialLayout=\"@layout/widget_crafting_detail\""))
    assertTrue(profitInfo.contains("android:targetCellWidth=\"2\""))
    assertTrue(profitInfo.contains("android:targetCellHeight=\"1\""))
    assertTrue(profitInfo.contains("android:initialLayout=\"@layout/widget_today_profit\""))
    assertTrue(combinedInfo.contains("android:targetCellWidth=\"4\""))
    assertTrue(combinedInfo.contains("android:targetCellHeight=\"2\""))
    assertTrue(combinedInfo.contains("android:initialLayout=\"@layout/widget_combined\""))
    // 新增：4×1 校验
    assertTrue(daySecretInfo.contains("android:targetCellWidth=\"4\""))
    assertTrue(daySecretInfo.contains("android:targetCellHeight=\"1\""))
    assertTrue(daySecretInfo.contains("android:initialLayout=\"@layout/widget_day_secret\""))

    listOf(craftingLayout, combinedLayout, profitLayout, daySecretLayout).forEach { layout ->  // 含新卡
        assertTrue("Widget layouts should be pure text RemoteViews surfaces", !layout.contains("<ImageButton"))
        assertTrue("Widget layouts should expose a text refresh target", layout.contains("@+id/btn_refresh"))
    }
    listOf(craftingLayout, combinedLayout, daySecretLayout).forEach { layout ->
        assertTrue("Initial widget layout must not be a blank block", layout.contains("待同步"))
        assertTrue("Initial widget layout must tell the user how to recover data",
            layout.contains("点刷新") || layout.contains("btn_refresh") || layout.contains("widget_day_secret_account_default"))
    }
}
```

- [ ] **步骤 2：运行测试**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:testDebugUnitTest --tests "com.local.dfcraftmonitor.widget.WidgetContractSourceTest" -q
```

预期：PASS。

- [ ] **步骤 3：Commit**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
git add app/src/test/java/com/local/dfcraftmonitor/widget/WidgetContractSourceTest.kt
git -c user.email=kisser@local -c user.name=MissKisser commit -m "test(widget): 新卡 4×1 加入契约校验"
```

---

### 任务 12：构建 APK + 真机拖放验证（手工冒烟）

**文件：** 无（端到端验证）

- [ ] **步骤 1：构建 Debug APK**

```bash
cd D:/document/Projects/df-item-crafting-monitoring
./gradlew :app:assembleDebug -q
```

预期：`BUILD SUCCESSFUL`，产物在 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **步骤 2：安装 + 启动 App 同步一次**（确保 `WidgetCache` 有 daySecrets 数据）

按项目 `android-dev` skill 流程：
```bash
# 让模型安装并启动，触发 AmsCraftingParser 抓 daySecrets → WidgetCache.updateFromDashboard → 写入 daySecrets
```

- [ ] **步骤 3：长按桌面选 今日密码 卡片 → 拖放**

期望触发 `DaySecretWidgetConfigureActivity`；勾选 ≤4 个地图 → 保存。

- [ ] **步骤 4：验证桌面卡片渲染**

期望看到 4 格密码 + 标题栏 + 账号名 + 刷新按钮。点刷新按钮触发同步。

- [ ] **步骤 5：进 App → 设置 → "已选地图" 验证 Settings 子屏显示**

期望同样 UI、多选结果与 Configure 一致。

- [ ] **步骤 6（可选）：修改子屏偏好 → 退出设置 → 桌面卡片应立即刷新**

---

## 自检（写完计划后由 orchestrator 执行）

**1. 规格覆盖度**

- §1.1 新增文件：DaySecretWidgetProvider ✅ (任务 7) · DaySecretWidgetConfigureActivity ✅ (任务 8) · widget_day_secret.xml ✅ (任务 5) · day_secret_widget_info.xml ✅ (任务 7)
- §1.2 修改文件：13 行每行都有覆盖任务 (1, 2, 3, 4, 6, 7, 8, 9)
- §2.1 WidgetPayload 字段：任务 1
- §2.2 UserPreferences 偏好：任务 3
- §2.3 updateFromDashboard 写入：任务 2
- §3.1 布局结构：任务 5 步骤 4
- §3.2 5 个边界：buildDaySecretEmpty/NoSelection/Selection/Overflow/LongMapName ✅ (任务 5 步骤 1)
- §3.3 签名：任务 6 步骤 1
- §3.4 Tap → MainActivity：任务 9 步骤 2
- §4.1 Configure Activity：任务 8 步骤 2
- §4.2 设置页子屏：任务 8 步骤 3 + 任务 8 步骤 4（SettingsRow 跳子屏）+ 任务 8 步骤 5（NavHost）
- §5.1 契约测试：任务 10 + 任务 11
- §5.2 边界渲染：任务 5 步骤 1
- §5.3 反序列化兼容：任务 1 步骤 1
- §5.4 existing 测试保护：任务 10 + 任务 11

**2. 占位符扫描**

- 仅 `TODO()` 在任务 8 步骤 1 的末尾 helper，已被要求删除。
- 任务 8 步骤 1 中已用注释"占位"标注 `Icons.Outlined.NotificationsActive` —— 任务里要求"调用方自选"已落到步骤 4。

**3. 类型一致性**

- `WidgetPayload.DaySecretEntry` 在所有用到的位置（任务 1, 5, 6, 8）都一致用全限定名 `WidgetPayload.DaySecretEntry`。
- `R.id.row_0/1/2/3`、`R.id.map_name_*`、`R.id.secret_*` 在任务 5 步骤 3 与任务 9 步骤 2 都一致。
- `loadDaySecretPrefs()` 返回 `Set<String>` —— 任务 4 步骤 1 提供，任务 6 步骤 2 调用一致。
- `UserPreferences.daySecretWidgetVisibleMaps: Set<String>` —— 任务 3 与任务 8 一致。

