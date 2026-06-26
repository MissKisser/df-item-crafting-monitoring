# 三角洲行动 · 制造监控 (Df Crafting Monitor)

一款面向 **三角洲行动** 玩家的 Android 制造工位监控 App，专注于制造倒计时、桌面 Widget 与完成通知。

> 战术、克制、高密度、可信赖 —— 让玩家像看战报一样看到自己的制造进度。

---

## ✨ 功能特性

- 🛠 **制造工位实时倒计时**：首页一览所有进行中的制造任务，剩余时间精确到秒
- 🧩 **桌面 Widget**：把工位倒计时钉在桌面，开屏即看
- 🔔 **完成通知**：制造临近完成（≤ 15 分钟）时通过 WorkManager + 通知主动提醒
- 🔐 **账号绑定**：支持腾讯 QQ 互联登录，多账号切换，数据本地加密存储
- 📊 **物品行情**：技术中心 / 制造台物品价格趋势参考
- 🌙 **强制深色作战面板**：暗色高对比主题，避免游戏风格被系统取色稀释
- 🧱 **零第三方商业 SDK**：仅使用 AndroidX / Jetpack Compose / Hilt / Retrofit / OkHttp

---

## 📱 截图

> 截图随版本更新，请参阅 [Releases](https://github.com/MissKisser/df-item-crafting-monitoring/releases) 页面附件。

---

## 🏗 技术栈

| 维度 | 选型 |
|------|------|
| 语言 | Kotlin 2.x（K2 编译器） |
| UI | Jetpack Compose + Material 3 |
| 异步 | Kotlin Coroutines + Flow |
| 依赖注入 | Hilt |
| 网络 | Retrofit 2 + OkHttp + kotlinx-serialization |
| 后台任务 | WorkManager + Hilt-Work |
| 本地存储 | DataStore Preferences（加密敏感数据） |
| 桌面组件 | AppWidgetProvider + Glance |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 15 (API 35) |
| JDK | 17 |

---

## 🚀 构建

### 环境要求
- JDK 17+
- Android SDK Platform 35 + Build-Tools
- Android Studio Ladybug (2024.2) 或更高版本

### 本地构建
```bash
# 克隆仓库
git clone https://github.com/MissKisser/df-item-crafting-monitoring.git
cd df-item-crafting-monitoring

# 准备 local.properties（参考 local.properties.example）
cp local.properties.example local.properties
# 编辑 local.properties，填入 sdk.dir 与 QQ 互联 AppID

# Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release APK（未签名，需要自行签名）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

> **签名提示**：仓库不包含任何 keystore。正式发布时请使用您自己的签名密钥。

---

## 📦 下载

前往 [Releases 页面](https://github.com/MissKisser/df-item-crafting-monitoring/releases) 下载最新版 APK。

| 版本 | versionName | 更新说明 |
|------|-------------|----------|
| 最新 | 0.2.0 | 见 [CHANGELOG](#-更新日志) |

---

## 🔐 隐私

本应用：
- ✅ **不上传任何账号信息到第三方服务器**（除游戏官方接口外）
- ✅ 敏感凭据使用 Android Keystore + DataStore 加密本地存储
- ✅ 无广告 SDK / 无统计分析 / 无推送服务
- ⚠️ 仅使用一个 QQ 互联 AppID 用于游戏账号绑定（登录页可见）

详细见 App 内「我的 → 隐私声明」。

---

## ⚠️ 免责声明

本项目是一个**非官方第三方工具**，与腾讯 / 三角洲行动官方无关。
所有游戏内数据归三角洲行动官方所有，本项目仅做数据展示，不触发任何写操作。
请遵守游戏用户协议，合理使用。

---

## 🤝 贡献

欢迎 Issue 与 PR！请遵守：
- 不提交真实账号 cookie / token / 抓包原始数据
- 不在 PR 中包含 `local.properties` 或 keystore
- 遵循项目已有 Kotlin / Compose 编码风格

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

## 🙏 致谢

- Material 3 Expressive 设计语言
- Jetpack Compose 社区
- 所有提供反馈与建议的玩家