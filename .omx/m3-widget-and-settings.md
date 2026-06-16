# M3 里程碑：桌面卡片与用户控制

**完成日期**：2026-06-16
**分支**：feature/m3-rewrite
**提交**：9ba8c42

## 交付内容

### 1. Glance 桌面卡片（spec 8.1 / 8.2）

| 文件 | 说明 |
|------|------|
| `widget/CraftingWidget.kt` | Glance AppWidget，4×2 卡片 |
| `widget/CraftingWidgetReceiver.kt` | BroadcastReceiver，Glance 框架自动注册 |
| `widget/WidgetState.kt` | Widget 渲染状态 + `fromStations()` 转换 + 新鲜度规则 |
| `widget/WidgetRefresher.kt` | 统一刷新入口，调 `updateAll()` |
| `widget/ui/CraftingWidgetContent.kt` | Compose 渲染：4 工位 + 剩余时间 + 过时提示 |
| `res/xml/crafting_widget_info.xml` | Widget 声明（4×2, 30min 更新周期） |
| `res/layout/crafting_widget_loading.xml` | 初始加载占位布局 |
| `res/layout/crafting_widget_preview.xml` | Widget 挑选器预览布局 |

**新鲜度规则**（spec 8.2）：数据超过 15 分钟显示「⚠ 数据可能过时」。

**刷新触发**：
- `SyncCoordinator.handleSuccess()` → `widgetRefresher.refresh()`
- `HomeViewModel.refresh()` 成功 → `widgetRefresher.refresh()`

### 2. 用户偏好（spec 6.2）

| 文件 | 说明 |
|------|------|
| `data/preference/UserPreferences.kt` | 数据类：MonitoringMode / lowPowerPolicyEnabled / welcomeShown |
| `data/preference/UserPreferencesRepository.kt` | DataStore Preferences（`user_prefs`），Flow + suspend 写方法 |

**设计决策**：
- 单 DataStore 文件（用户确认）
- Preferences 而非 Proto（3 字段，无嵌套）
- 默认值定义在 data class 上

### 3. 设置页

| 文件 | 说明 |
|------|------|
| `ui/settings/SettingsScreen.kt` | 监控模式切换 + 低电量策略开关 + 隐私说明入口 + 清除数据并退出 |
| `ui/settings/SettingsViewModel.kt` | 暴露 UserPreferences Flow + 写方法 + clearDataAndLogout |

### 4. 隐私说明页（spec 10.3）

| 文件 | 说明 |
|------|------|
| `ui/privacy/PrivacyScreen.kt` | 数据收集/存储/传输/后台行为/权限/删除 6 节说明 |

**设计决策**：轻量级——仅在设置页展示，不做首屏强制弹窗。进入时标记 `welcomeShown = true`。

### 5. 数据清除

| 文件 | 说明 |
|------|------|
| `data/AppDataCleaner.kt` | 统一清除入口：停 Worker → 清 Session → 清 Cache → 清 Prefs |

### 6. 导航扩展

- `HomeScreen` TopAppBar 添加设置图标
- 路由：`home` → `settings` → `privacy`
- 退出登录：`settings` → `login`（popUpTo(0) 清全部回退栈）

### 7. 单测

| 测试类 | 数量 | 覆盖 |
|--------|------|------|
| `UserPreferencesTest` | 4 | 默认值、自定义值、枚举遍历、valueOf |
| `WidgetStateTest` | 7 | fromStations 转换、新鲜度规则（<15min / =15min / >15min）、空列表、阈值常量 |
| `AppDataCleanerTest` | 4 | 清除顺序、4 步完整性、cancel 优先、prefs 最后 |

**全部 62 测试通过**（M1+M2 原有 48 + M3 新增 14）。

## 依赖变更

`app/build.gradle` 新增：
- `androidx.glance:glance-appwidget:1.1.1`
- `androidx.glance:glance-material3:1.1.1`

## 已知限制 / V1.1 待改进

1. **Widget 刷新**：`updatePeriodMillis=1800000`（30min），实际刷新依赖 Worker 同步触发；Widget 自身 `provideGlance` 每次重新读 SnapshotCache
2. **低电量策略**：`lowPowerPolicyEnabled` 已写入偏好，但 WorkScheduler 尚未读取该偏好动态调整约束（V1.1 实现）
3. **MonitoringMode**：UI 可切换，但 WorkScheduler 尚未根据模式调整轮询间隔（V1.1 实现）
4. **无 mock 框架**：AppDataCleaner 测试用纯函数验证设计意图，集成验证靠实机
5. **Widget 点击**：暂未实现点击跳转到 App（V1.1 加 action callback）

## spec M3 对照

| spec 条目 | 状态 |
|-----------|------|
| 8.1 桌面卡片 4×2 | ✅ |
| 8.2 新鲜度规则 15min | ✅ |
| 6.2 低电量策略开关 | ✅ UI 已有，Worker 动态调整待 V1.1 |
| 10.3 隐私说明 | ✅ |
| 用户控制（设置页） | ✅ |
| 清除数据 | ✅ |
