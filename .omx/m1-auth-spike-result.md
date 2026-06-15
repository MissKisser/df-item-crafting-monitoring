# M1 QQ 登录交换验证原型

## 当前目标

构建一个最小 Android 调试版，用 QQ 授权登录后验证是否能拿到三角洲行动特勤处制造数据。

验证链路：

```text
QQ loginServerSide
-> QQ 回调 openid + code
-> https://ams.game.qq.com/ams/userLoginSvr
-> 尝试提取 AMS credential
-> https://comm.ams.game.qq.com/ide/?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2
-> CraftingSnapshot
```

同时保留对照链路：

```text
QQ 回调 openid + access_token/code
-> 直接构造 qc Cookie
-> 特勤处接口
```

## 已完成

- 初始化 Git 仓库。
- 下载并接入 QQ 互联 Android SDK `3.5.19`。
- 创建 Android 原型工程 `com.local.dfcraftmonitor`。
- 实现本地模型：
  - `AmsCredential`
  - `CraftingSnapshot`
  - `CraftingStation`
  - `StationType`
- 实现 AMS 响应解析：
  - `AmsCraftingParser`
  - `AmsProbeResult`
- 实现网络探针：
  - `AmsApiClient.fetchCrafting`
  - `AmsApiClient.exchangeQqServerSideCode`
- 实现原型页面：
  - QQ server-side 登录；
  - 强制二维码登录；
  - 登录回调后自动验证 AMS；
  - 页面只显示脱敏 token 长度和接口结果。

## 测试与构建

已通过：

```text
gradle testDebugUnitTest
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.local.dfcraftmonitor/.MainActivity
```

## AppID 探针结果

### `1112438254`

来源：小程序代码中的 QQ mini appid。

QQ OAuth 页面返回：

```text
client id is illegal, not exist [appid: 1112438254] (100008)
```

结论：该 ID 不能作为普通 QQ 互联 Android SDK 的 `client_id` 使用。

### `1110543085`

来源：小程序代码中的 target QQ appid。

QQ SDK 可进入授权二维码页面：

```text
QQ 授权登录
使用 QQ 手机版扫码授权登录
```

结论：该 ID 至少能触发 QQ 授权流程，下一步需要扫码授权后看回调和 AMS 交换结果。

扫码回调结果：

```text
QQ 回调字段:
access_token(len=32), expires_in(len=7), pay_token(len=32), ret(len=1),
pf(len=35), pfkey(len=32), auth_time(len=13), page_type(len=1)

openid length=0
access_token/code length=32
```

后续验证：

```text
graph.qq.com/oauth2.0/me:
callback( {"error":100013,"error_description":"access token is illegal"} );

ams.game.qq.com/ams/userLoginSvr:
{"errorCode":"-1","errorStr":"您还没有登录","isLogin":0,...}

comm.ams.game.qq.com/ide:
ret=101, iRet=101, sMsg=非常抱歉，请先登录！
```

当前二维码登录返回的 `access_token` 不是 QQ OpenAPI 可用 token；按现有请求方式调用 `userLoginSvr` 后，未换出 AMS credential。

安装并登录最新版 QQ 后，普通客户端授权路径进入 QQ 客户端授权页。用户点击「同意」后，页面返回以下错误：

```text
登录失败
该应用非官方正版应用，请去相应官网下载正版后进行QQ登录。
```

本地调试包 `com.local.dfcraftmonitor` 未收到该路径的授权回调。

清理 QQ 客户端状态后，`loginServerSide(..., qrcode=true)` 可进入 APP 内 WebView 二维码页：

```text
QQ 授权登录
使用QQ手机版扫码授权登录
```

同意授权前，本地 WebView 观测到以下 QQ 二维码登录会话 cookie：

```text
.ptlogin2.qq.com | qrsig | len=128
.ptlogin2.qq.com | pt_login_sig | len=64
.ptlogin2.qq.com | uikey | len=64
.ptlogin2.qq.com | pt_local_token | len=11
```

本地 WebView 存储中未发现 `openid`、`access_token`、`code`、`pay_token`、`pfkey` 等可提交给 AMS 或 `userLoginSvr` 的票据字段。

## 当前状态

1. 已定位特勤处制造状态接口，并已用小程序现有登录态验证接口可返回四项制造数据。
2. Android 原型可进入 QQ WebView 二维码授权页。
3. 普通 QQ 客户端授权在「同意」后返回「该应用非官方正版应用」错误，本地 APP 未收到授权回调。
4. WebView 二维码页当前仅观测到 QQ 二维码会话 cookie，未观测到 AMS 可用票据字段。
5. `userLoginSvr` 交换和直接构造 `qc` Cookie 两条链路当前均未通过，AMS 仍返回未登录。

## 配置说明

本机调试配置在未跟踪文件：

```text
local.properties
```

格式：

```properties
sdk.dir=C\:\\Users\\missk\\AppData\\Local\\Android\\Sdk
qq.appId=1110543085
```
