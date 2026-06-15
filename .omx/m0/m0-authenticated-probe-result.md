# M0 已登录态接口闭环结果

## 结论

已完成「找到接口 + 使用已登录鉴权拿到特勤处制造数据」闭环。

已验证接口：

```text
POST https://comm.ams.game.qq.com/ide/?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2
```

认证请求结果：

```text
HTTP status: 200
AMS ret: 0
AMS iRet: 0
AMS sMsg: succ
Business code: 0
Business msg: ok
placeData count: 4
```

脱敏响应文件：

```text
.omx/m0/ams-crafting-status-authenticated.redacted.json
```

摘要文件：

```text
.omx/m0/ams-crafting-status-authenticated-summary.md
```

## 设备与 Root 状态

设备：

```text
serial: 3bbf38b8
model: 23049RAD8C
Android: 15
WeChat package: com.tencent.mm
foreground: com.tencent.mm/com.tencent.mm.plugin.appbrand.ui.AppBrandUI
```

Root 验证：

```text
adb shell su -c id
uid=0(root) gid=0(root)
```

## 小程序与鉴权链路

三角洲行动微信小程序 appid：

```text
wx1c36464bbea2507a
```

代码缓存命中：

```text
/data/data/com.tencent.mm/cache/appbrand/jscache/appservice.app.js_wx1c36464bbea2507a/42db18f6271a4bccec0e09ca217dcda8
/data/data/com.tencent.mm/MicroMsg/appbrand/pkg/general/_-1446544219_188.wxapkg
```

小程序请求封装确认：

- `postIDEData()` 调用 `https://comm.ams.game.qq.com/ide/`；
- 请求参数包含 `iChartId`、`iSubChartId`、`sIdeToken`、`eas_url`；
- `ret/iRet=101` 时触发小程序自己的 `login()` 并重试；
- Cookie 由小程序 storage 里的登录态动态拼接。

关键 Cookie 形态：

```text
QQ 路线：
openid=<qq_openid>; acctype=qc; appid=<qq_ieg_ams_appid>; access_token=<qq_ieg_ams_session_token>

微信小程序路线：
openid=<openid>; acctype=mini; appid=wx1c36464bbea2507a; ieg_ams_session_token=<...>; ieg_ams_token=<...>; ieg_ams_token_time=<...>; ieg_ams_token_v2=<...>; unionid=<...>
```

本次设备实际可用 Cookie 字段：

```text
access_token: present, length=32
acctype: present, length=2
appid: present, length=10
openid: present, length=32
```

字段长度显示本次命中的是 QQ 路线，即 `acctype=qc`，但敏感值未落文档。

## 本地 Storage 落点

Root 只读命中的小程序 storage 文件：

```text
/data/data/com.tencent.mm/files/mmkv/AppBrandMMKVStorage4093052208
```

该文件包含：

```text
wx1c36464bbea2507a__cookie
wx1c36464bbea2507a__openid
wx1c36464bbea2507a__acctype
wx1c36464bbea2507a__qq_openid
wx1c36464bbea2507a__unionid
```

原始 MMKV 文件曾临时复制到 `.omx/m0/private/` 用于本地解析；接口验证完成后已删除原始副本，只保留脱敏响应和摘要。

## 已拿到的四项制造数据

```text
技术中心
objectId=13040000185
objectName=骨架狙击枪托
leftTime=20291
pushTime=1781520493

工作台
objectId=37120500001
objectName=5.45x39mm BS
leftTime=4921
pushTime=1781505123

防具台
objectId=11010005002
objectName=H09 防暴头盔
leftTime=17161
pushTime=1781517363

制药台
objectId=14060000002
objectName=精密护甲维修包
leftTime=9138
pushTime=1781509340
```

响应同时包含 `relateMap`，可得到物品名、图片、品级、均价等字段。第一版只需要物品名、图片、剩余时间和完成时间；价格字段可留到最终版优化。

## 对公开发布版本的影响

M0 已证明接口和数据结构可用，但这次闭环依赖 root 读取微信本地小程序 storage。公开发布版不能默认依赖 root，也不应要求用户授权读取微信私有目录。

因此后续有两个分支：

1. 工程验证版：继续用 root/local-only collector 验证轮询、通知、桌面卡片和低功耗策略。
2. 公开发布版：必须实现可公开分发的 QQ/微信授权链路，拿到 AMS 接受的 `qc` 或 `mini` 登录态；如果独立 APP 官方 SDK 无法拿到同等票据，公开版需要重新定义登录方案。

## 本次验证脚本

```text
.omx/m0/extract-and-call-ams-from-mmkv.py
.omx/m0/invoke-ams-crafting-status.ps1
```

`extract-and-call-ams-from-mmkv.py` 做了三件事：

1. 通过 root 直接从设备读取 MMKV 到内存，或从本地 MMKV 副本解析 `wx1c36464bbea2507a__cookie`；
2. 构造 AMS Cookie 并调用特勤处状态接口；
3. 输出脱敏响应和制造状态摘要。
