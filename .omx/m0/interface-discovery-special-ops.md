# M0 接口发现：特勤处制造状态

## 结论摘要

已定位到特勤处制造状态的候选接口：

```text
POST https://comm.ams.game.qq.com/ide/
```

特勤处状态参数：

```text
iChartId=365589
iSubChartId=365589
sIdeToken=bQaMCQ
source=2
```

无 Cookie 请求已经验证接口存活，并返回未登录：

```json
{"ret":101,"iRet":101,"sMsg":"非常抱歉，请先登录！","sAmsSerial":"AMS-DFM-0615125603-IFlkop-661959-365589"}
```

已登录态请求也已完成闭环，返回 `ret=0`、`iRet=0`、`sMsg=succ`，并拿到四项 `placeData`。详见：

- `.omx/m0/m0-authenticated-probe-result.md`
- `.omx/m0/ams-crafting-status-authenticated-summary.md`

## 证据来源

### 1. 本机 CONNECT 代理元数据

在不安装证书、不解密 TLS、不读取请求头/Body 的情况下，临时设置 Android 系统代理并触发小程序页面切换，记录到以下域名：

```text
CONNECT rumt-zh.com:443
CONNECT comm.ams.game.qq.com:443
CONNECT ams.game.qq.com:443
```

代理完成后已清理：

```text
adb shell settings get global http_proxy
:0
```

日志文件：

- `.omx/m0/proxy-hosts.log`

### 2. 公开 Apifox 文档

公开文档页面：

- https://df-api.apifox.cn/311774952e0

文档记录该接口为「特勤处状态」，并标注：

```text
POST https://comm.ams.game.qq.com/ide/
iChartId：365589
sIdeToken：bQaMCQ
source=2（必须）
ck 中 type：QQ 为 qc；微信为 wx
```

文档示例 Cookie 字段：

```text
openid=<openid>
acctype=<qc|wx>
appid=<appid>
access_token=<access_token>
```

注意：Apifox 文档是第三方公开资料，不等同官方文档。它已被本机代理域名和无 Cookie 响应部分交叉验证，但仍需要 M0 继续验证真实授权链路。

### 3. 无 Cookie 请求验证

执行：

```powershell
curl.exe -sS -X POST "https://comm.ams.game.qq.com/ide/?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2" -H "content-type: application/x-www-form-urlencoded"
```

结果：

```json
{
  "ret": 101,
  "iRet": 101,
  "sMsg": "非常抱歉，请先登录！",
  "sAmsSerial": "AMS-DFM-0615125603-IFlkop-661959-365589"
}
```

这证明：

- endpoint 当前可访问；
- 参数组合至少能进入 AMS 业务逻辑；
- 该接口确实需要登录态 Cookie；
- 当前还没有证明独立 APP 可以获得可用登录态。

## 响应结构草案

成功响应核心字段：

```text
ret
iRet
sMsg
jData.data.code
jData.data.msg
jData.data.data.placeData[]
jData.data.data.relateMap
jData.data.data.nowTime
sAmsSerial
```

`placeData[]` 关键字段：

| 字段 | 含义 |
| --- | --- |
| `Id` | 设施 ID |
| `Status` | 设施状态文本 |
| `Level` | 设施等级 |
| `Name` | 本地化 key |
| `placeType` | 设施类型 |
| `placeName` | 中文设施名 |
| `leftTime` | 剩余秒数，制造中才有 |
| `pushTime` | 预计完成时间戳，制造中才有 |
| `objectId` | 制造物品 ID，制造中才有 |

设施类型映射：

| `placeType` | 中文名 | APP 模型 |
| --- | --- | --- |
| `tech` | 技术中心 | `technology_center` |
| `workbench` | 工作台 | `workbench` |
| `pharmacy` | 制药台 | `medicine_station` |
| `armory` | 防具台 | `armor_station` |

`relateMap` 可用于把 `objectId` 映射到物品名称、图片、品级、均价等信息。

## 鉴权问题

当前接口需要 AMS Cookie：

```text
openid
acctype
appid
access_token
```

关键未决问题不是接口本身，而是独立 APP 如何获得 AMS 接受的这组登录态。

风险点：

- QQ/微信开放平台登录能返回当前 APP 自己的 openid/access_token；
- AMS 示例中的 `appid` 可能属于三角洲行动或腾讯游戏活动体系；
- 独立 APP 自己申请的 QQ/微信 AppID 返回的 token 不一定被 `comm.ams.game.qq.com` 接受；
- 如果必须取得小程序内部 Cookie 或三角洲官方 appid 下的 token，V1 需要重新评估可行性。

## 下一步验证

M0 继续做「鉴权可行性验证」，不要进入完整 APP 开发。

本地验证计划与探针：

- `.omx/m0/m0-auth-verification-plan.md`
- `.omx/m0/invoke-ams-crafting-status.ps1`

建议顺序：

1. 明确 QQ 路线：
   - 申请/准备 QQ 互联测试应用；
   - 用官方 SDK 获取 `openid/access_token`；
   - 构造 Cookie：`openid=<...>; acctype=qc; appid=<测试应用 AppID>; access_token=<...>`；
   - 调用特勤处状态接口；
   - 记录是否被 AMS 接受。

2. 明确微信路线：
   - 申请/准备微信开放平台移动应用；
   - 用官方 SDK 获取 code；
   - 服务端用 code 换取 `openid/access_token`；
   - 构造 Cookie：`openid=<...>; acctype=wx; appid=<测试应用 AppID>; access_token=<...>`；
   - 调用特勤处状态接口；
   - 记录是否被 AMS 接受。

3. 判定：
   - 如果独立 APP 的官方授权 token 可用，M0 进入 Go；
   - 如果只接受三角洲/小程序官方 appid 下的 token，M0 进入 No-Go 或需要重新定义授权边界；
   - 如果接口需要额外安全参数，且只能通过绕过/破解获得，M0 进入 No-Go。
