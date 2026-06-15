# M0 鉴权可行性验证计划

## 目标

验证独立 Android APP 通过 QQ 互联或微信开放平台官方登录获得的 `openid/access_token`，是否能被三角洲行动特勤处 AMS 接口接受。

当前已定位候选接口：

```text
POST https://comm.ams.game.qq.com/ide/?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2
```

未登录请求已返回：

```json
{"ret":101,"iRet":101,"sMsg":"非常抱歉，请先登录！","sAmsSerial":"AMS-DFM-0615125603-IFlkop-661959-365589"}
```

因此 M0 的核心问题不再是 URL，而是登录态来源。

## 验证假设

### H1：独立 APP 官方授权 token 可用

独立 APP 使用 QQ 互联或微信开放平台完成登录后，拿到本 APP 名下的 `openid/access_token/appid`，构造 AMS Cookie：

```text
openid=<openid>; acctype=<qc|wx>; appid=<appid>; access_token=<access_token>
```

接口返回 `ret=0` 或业务成功数据，且响应中包含 `placeData[]`。

结论：M0 Go，可以进入第一版 Android APP 技术预研。

### H2：只接受三角洲/AMS 官方 appid 下的 token

独立 APP 官方授权 token 仍返回未登录、token 无效、appid 不匹配、账号未绑定等结果。

结论：M0 No-Go 或重新定义产品边界。不能通过提取微信小程序私有 Cookie、绕过签名、破解参数或规避风控来继续。

### H3：还需要额外只读安全参数

接口除了 Cookie 外还要求额外的公开参数、渠道参数或设备参数；这些参数如果可以通过公开接口或普通请求元数据获得，可以继续验证。如果只能通过逆向私有代码、解密、Hook 或抓取用户私密会话获得，则停止。

## 本地探针

脚本：

```text
.omx/m0/invoke-ams-crafting-status.ps1
```

脚本只从环境变量读取 token，避免把敏感值写进命令历史、Markdown、聊天记录或日志。

必需环境变量：

| 变量 | 含义 |
| --- | --- |
| `DF_AMS_OPENID` | QQ/微信官方登录返回的 openid |
| `DF_AMS_ACCESS_TOKEN` | QQ/微信官方登录返回的 access_token |
| `DF_AMS_ACCTYPE` | QQ 用 `qc`，微信用 `wx` |
| `DF_AMS_APPID` | 当前授权应用的 AppID |

运行示例：

```powershell
$env:DF_AMS_OPENID = "<openid>"
$env:DF_AMS_ACCESS_TOKEN = "<access_token>"
$env:DF_AMS_ACCTYPE = "qc"
$env:DF_AMS_APPID = "<qq_appid>"
powershell -ExecutionPolicy Bypass -File .omx/m0/invoke-ams-crafting-status.ps1
```

输出文件：

```text
.omx/m0/ams-crafting-status-auth-probe-response.redacted.json
```

脚本会尽量脱敏 `openid/access_token/appid`，但响应中的游戏制造状态本身仍可能属于个人数据，不应提交到公开仓库。

## 判定标准

### Go

- 返回 HTTP 200；
- AMS 返回业务成功；
- 响应包含四项设施或 `placeData[]`；
- 设施字段能映射到技术中心、工作台、制药台、防具台；
- 不需要获取微信/QQ客户端私有存储、不需要 MITM 解密、不需要 Hook、不需要绕过签名或风控。

### No-Go

- 官方 QQ/微信开放平台 token 被 AMS 拒绝；
- AMS 必须使用三角洲官方 appid、小程序 session、微信私有 Cookie 或游戏内部票据；
- 必须从微信私有目录、内存、TLS 明文、Hook 或反编译签名逻辑中取得登录态；
- 必须模拟会导致账号风控的设备指纹、签名、加密或行为轨迹。

## 下一步

1. 准备 QQ 互联测试应用，跑 QQ 登录链路，验证 `acctype=qc`。
2. 准备微信开放平台移动应用，跑微信登录链路，验证 `acctype=wx`。
3. 将验证结果记录到 M0 决策文档。
4. 只有 M0 Go 后，再开始 Android APP 的登录、后台轮询、通知和桌面卡片实现。
