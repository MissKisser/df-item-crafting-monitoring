# M2 命门验证 Spike：pvp.qq.com Cookie → AMS 凭证

## 背景

M1 验证结论：QQ SDK 二维码登录拿到的 32 位 `access_token` 不是 QQ OpenAPI 可用 token，`userLoginSvr` 交换和直接构造 `qc` Cookie 两条链路均未通过。

调研 `Entropy-Increase-Team/delta-force-plugin` 后端发现：它真正能拉到特勤处数据，靠的是 **pvp.qq.com H5 登录后产出的 ptlogin Cookie（p_skey/skey）→ 算出 g_tk → 调腾讯 IEG/AMS 接口** 这条链路。其后端文档 `/login/qq/cookie-exchange` 接口明确指出：**p_skey 是用于计算 g_tk 的关键 Cookie**。

本 spike 验证这条链路在原生 Android 端能否复现。**这是路线 B（彻底自研后端）的命门。**

## 验证链路

```text
WebView 加载 https://pvp.qq.com/cp/a20161115tyf/page1.shtml
-> 用户 QQ 登录
-> CookieManager 抓 ptlogin2.qq.com 域 Cookie（p_skey/skey/uin）
-> G_tkCalculator.calc(p_skey)  =>  g_tk
-> GET comm.ams.game.qq.com/ide/...&g_tk=<gtk>
   带 桌面UA + Referer(pvp.qq.com) + Origin + X-Requested-With + Cookie
-> AmsProbeResult
```

对照保留 M1 两条旧路径：原始 Cookie（POST）、解析 openid/appid/access_token 四元组。

## 已完成

- `local.properties` 新增 `df.webLoginUrl=https://pvp.qq.com/cp/a20161115tyf/page1.shtml`，WebView 默认登录页改为插件同款老活动页。
- 新增 `G_tkCalculator`：实现 QQ 公开的 `hash32_s` g_tk 算法（`hash=5381; hash += (hash<<5) + char; & 0x7FFFFFFF`），并提供 `pickSkeyFromCookie`（优先 p_skey，回退 skey）。
- 配套单测 `G_tkCalculatorTest`：7 个用例（空串兜底、确定性、非负、参考实现一致性、skey 选取优先级），全绿。
- `AmsApiClient` 新增 `fetchCraftingWithCookieAndGtk(rawCookie, gtk)`：GET + 完整浏览器请求头 + Cookie + g_tk query 参数。桌面 UA 提取为公共常量 `DESKTOP_UA`。
- 新增私有 `getWithCookie` 通道（带 Cookie 的 GET），补 Referer/Origin/X-Requested-With/Accept/Language 头，关闭自动重定向。
- `MainActivity.runWebCookieProbe` 接入**路径3（命门）**：优先取 ptlogin2.qq.com 单域 Cookie（绕开 mergeAllCookies 同名字段覆盖坑），算 g_tk，调新通道；**先 dump 完整原始响应体**（截断 400 字符），再走 `appendCraftingResult`，失败时人眼可判断是鉴权问题还是参数问题。

## 测试与构建

```text
gradlew.bat :app:testDebugUnitTest   // 全绿：G_tkCalculatorTest(7) + QqLoginSessionTest(3)
gradlew.bat :app:assembleDebug        // BUILD SUCCESSFUL，app-debug.apk 936KB
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.local.dfcraftmonitor/.MainActivity
```

已在测试机（3bbf38b8）安装并启动。

## 待实机验证（需人工操作）

代码链已完整，但命门验证的最后一步——**真实登录 pvp.qq.com 抓 Cookie 调 AMS**——需要测试机上有人工 QQ 账号扫码/登录，无法自动化。

操作步骤：
1. APP 内点「网页登录(WebView) 验证」
2. WebView 加载 pvp.qq.com 活动页，完成 QQ 登录
3. 点「抓取 Cookie 并验证 AMS」
4. 观察日志，重点关注**路径3**输出：
   - 是否抓到 `ptlogin2.qq.com` 域 Cookie，其中是否含 `p_skey`/`skey`
   - g_tk 计算值
   - 特勤处接口的 `ret` / `iRet` / 原始响应体

## 成功判据（go / no-go）

- **✅ GO**：路径3 `ret=0 && iRet=0` 且解析出 `placeData`（≥1 工位）→ 命门通过，路线 B 进入工程化。
- **⚠️ 部分 GO**：路径3 `ret=0` 但 `placeData` 为空 → 鉴权通了但缺 body 参数（如 roleId/iPage），需补一次"抓官方小程序实际请求 body"实验。
- **❌ NO-GO**：路径3仍 `ret!=0` 或 `请先登录` → p_skey→g_tk→AMS 在原生端走不通，需改走 connect.qq.com OAuth code 那条线评估。

## 实测结果（2026-06-16）

### 最终结论：✅ GO —— 路线 B 命门验证通过

特勤处制造数据已成功获取。但**通过的是路径1（原始 Cookie POST），不是预期的路径3（g_tk）**。

### 实测日志（完整，脱敏长度）

```text
[07:20:30] 网页登录返回，最后 URL: https://pvp.qq.com/cp/a20161115tyf/page1.shtml
[07:20:30] [comm.ams.game.qq.com] ..., acctype(len=2), openid(len=32),
           access_token(len=32), appid(len=9)
[07:20:30] [graph.qq.com] ..., p_skey(len=44), pt4_token(len=44), pt_oauth_token(len=76)
[07:20:30] [ptlogin2.qq.com] ..., qrsig(len=128), pt2gguin(len=11), superkey(len=44)
[07:20:30] 路径1：用原始 Cookie 请求特勤处接口（不改写）。
[07:20:31] web raw cookie: ret=0 iRet=0 msg=succ
[07:20:31] web raw cookie placeData=4
[07:20:31] - 技术中心 | 骨架狙击枪托 | 剩余 20429s | 完成 06-16 13:01:01
[07:20:31] - 工作台 | 5.56*45mm M855A1 APC+ | 剩余 24020s | 完成 06-16 14:00:52
[07:20:31] - 防具台 | 精英防弹背心 | 剩余 27586s | 完成 06-16 15:00:18
[07:20:31] - 制药台 | 精密护甲维修包 | 剩余 27596s | 完成 06-16 15:00:28
```

### 关键发现：比预期简单

| 路径 | 状态 | 说明 |
|------|------|------|
| 路径1 原始 Cookie（POST） | ✅ **成功** | AMS 域 Cookie 原样 POST 即可拿到数据 |
| 路径2 qc 四元组 | ⏭ 未执行 | 代码在路径1 成功后 `return` |
| 路径3 Cookie+g_tk | ⏭ 未执行 | 同上 |

- **不需要 g_tk / p_skey / ptlogin 换算**。真正让接口认账的是 `comm.ams.game.qq.com` 域下发的 `openid`+`access_token`+`appid`+`acctype=qc` 四元组——恰好就是 `AmsCredential.cookieHeader()` 拼的 `qc` Cookie 形态。
- `graph.qq.com` 的 `p_skey(len=44)`、`pt4_token`、`pt_oauth_token` 虽然抓到了，但**不是**特勤处接口的鉴权票据。
- `G_tkCalculator` 和 `fetchCraftingWithCookieAndGtk` 当前链路用不到，作为持久资产保留（单测全绿，算法正确）。

### 为什么 M1 失败、M2 成功

差别在 **token 来源**，不在算法：
- M1：QQ SDK `loginServerSide` 的 32 位 `access_token` 是不可用 token，AMS 不认。
- M2：pvp.qq.com H5 登录后，AMS 域自己下发的 32 位 `access_token` 是有效 session 票据，AMS 认。

两种 token 都是 32 位，但内容与归属完全不同。

### 遗留：g_tk 链路未实测

路径3（g_tk）未跑到。如需完整闭环，去掉 `runWebCookieProbe` 里路径1 成功后的 `return`，让三条路径都执行一遍。当前不影响路线 B 的可行性结论。

## 当前状态

命门已过，路线 B（自研后端）可行。下一步进入工程化：
- 把 pvp.qq.com WebView 登录 + Cookie 抓取做成产品级流程
- 处理 Cookie 续期/刷新（当前是单次有效）
- 数据持久化与制造完成推送

## 配置说明

`local.properties`（未跟踪）：

```properties
sdk.dir=C\:\\Users\\missk\\AppData\\Local\\Android\\Sdk
qq.appId=1110543085
df.webLoginUrl=https://pvp.qq.com/cp/a20161115tyf/page1.shtml
```
