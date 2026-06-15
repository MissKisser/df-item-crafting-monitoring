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

结论：当前二维码登录返回的 `access_token` 不是 QQ OpenAPI 可用 token，也不能直接换出 AMS credential。当前测试机没有安装 `com.tencent.mobileqq`，普通 `Tencent.login` 只进入“下载 QQ”页面，因此还不能完成普通 QQ 客户端 SSO 登录验证。

下一步硬性前置：

1. 在测试机安装并登录最新版 QQ，再走“QQ 普通登录对照”。
2. 或者替换为自己申请、已绑定当前包名和签名的 QQ 互联 Android AppID。

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

正式公开版应替换为自己申请的 QQ 互联 Android 应用 AppID，并绑定包名与签名。
