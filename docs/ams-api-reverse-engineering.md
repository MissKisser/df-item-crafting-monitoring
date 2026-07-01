# 三角洲行动 AMS 接口逆向工程笔记

> 记录 2026-07-01 排查"战绩不显示"时，对腾讯 AMS（Activity Management System）接口体系的完整逆向过程。
> 目的：沉淀方法论，让未来遇到"小程序接口变更导致 App 失效"时能快速定位。

## 一、问题背景

App 战绩页一直空白，但微信小程序正常。App 用 `iChartId=450526 / sIdeToken=PHq59Y` 直连 AMS，
返回 `ret=100 服务不可用`。需要搞清楚小程序到底怎么请求的。

## 二、逆向方法论（核心经验）

### 原则：找"正常工作的参考实现"，而非盲猜

AMS 这类腾讯接口，**前端代码就是最权威的请求文档**。优先级：

1. **小程序 wxapkg 缓存**（最有效）—— 微信会把小程序代码缓存到本地，root 可读
2. **官方 H5 工具站 JS**（如 `df.qq.com/cp/a20241230webmp/`）—— 但可能不直连 AMS
3. **第三方项目源码**（Koishi/Yunzai 插件）—— 可能用了不同的 API 体系
4. **接口文档**（Apifox）—— 容易过时

### 排查路径决策树

```
接口返回错误
├─ ret=100 "服务不可用"  → chartId 下线 或 调用形式错误
├─ ret=101 "请先登录"    → appid/登录态不对
├─ ret=190001 "系统繁忙" → 服务在线，参数错误（参数名/格式问题）
└─ ret=107 "活动太火爆"  → chartId 不存在
```

**关键区分**：`190001`（参数错，服务正常）vs `100`（服务下线）vs `101`（登录态）。
这是定位问题的第一手信号，不要混为一谈。

## 三、本次实战过程

### 阶段 1：确认问题在数据层（非 UI）

- 加诊断日志到 `fetchRecentMatches`，打印 raw body
- 发现 `bodyLen=269`、`ret=100`、`data=""` —— **接口空返回，不是解析问题**
- 用真凭证（cookie）离线 curl 复刻，确认 `ret=100`

### 阶段 2：排除凭证问题（分水岭证据）

同一 cookie：
- 制造接口 `365589` → `ret=0 succ` ✅（凭证有效）
- 战绩接口 `450526` → `ret=100` ❌（接口本身问题）

**这一步至关重要**：用已知能成功的接口做对照，立即排除"凭证失效/网络/风控"等所有其它可能。

### 阶段 3：从 wxapkg 提取小程序真实代码（决定性突破）

**wxapkg 位置（root 可读）**：
```
/data/data/com.tencent.mm/MicroMsg/appbrand/pkg/
├── general/    # 普通小程序包
├── commLib/    # 通用库
└── firstParty/ # 第一方
```

文件名格式：`_<appidHash>_<version>.wxapkg`

**提取步骤**：
```bash
# 1. 找最近修改的包（刚打开过的小程序）—— 按时间排序
adb shell "su -c 'find /data/data/com.tencent.mm/MicroMsg/appbrand/pkg -name \"*.wxapkg\" -exec ls -la {} +' | sort -k6,7"

# 2. cp 到 /sdcard 再 pull（注意 Git Bash 路径转换坑）
adb shell "su -c 'cp <src> /sdcard/wx.pkg; chmod 644 /sdcard/wx.pkg'"
# pull 时禁用 MSYS 路径转换，否则 /sdcard 会被改成 D:/Git/sdcard
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" adb pull /sdcard/wx.pkg ./
```

**wxapkg 不需要专门解包工具就能搜**（V8 字节异或只加密部分，业务 JS 多为明文）：
```javascript
// unwxapkg.js —— 直接 toString 搜关键字
const s = fs.readFileSync("wx.pkg", "latin1");
// 搜 token 表、doFlowEmit 调用、URL 拼装
```

### 阶段 4：理解 AMS Flow SDK 架构

从 wxapkg 里挖出的关键代码揭示了完整架构：

**1. token→chartId 是动态映射，不硬编码**：
```js
// 小程序先调配置接口拿映射表
p("https://comm.ams.game.qq.com/ide/page/" + activityId, {}, !1)
// 返回: { tokens: {PHq59Y: "450526", ...}, flows: {...} }
```

**activityId 是固定的**：`21_3LXYAj`（三角洲特勤处）。
任何人都能调这个接口拿当前映射（甚至不需要登录态太严格）。

**2. 业务调用通过 SDK 封装**：
```js
// token 表（showid 函数）
case 1: return "21_3LXYAj"  // activity 本身
case 7: return "zMemOt"     // 查询类（金币/基金）
case ... return "PHq59Y"    // 战绩

// 调用
doFlowEmit("PHq59Y", {type:4, item:"0,0,0,2201,0,0,0,75", page:1}, callback)
// 内部: {activityId:"21_3LXYAj", flowToken:"PHq59Y", sData:{...}}
// 然后 a.emit(r) —— AMS SDK 实例发请求
```

**3. 实际 HTTP 请求组装（postIDEData）**：
```js
var r = {
  iChartId: t.chartId,      // 从映射表拿，如 450526
  iSubChartId: t.chartId,
  sIdeToken: t.flowToken,   // PHq59Y
  eas_url: v()              // EAS 埋点 URL（非鉴权）
};
Object.assign(r, t.sData);  // 合并 type/item/page
// 删除 SDK 内部字段: activityId, flowToken, ieg_ams_session_token 等
// POST 到 https://comm.ams.game.qq.com/ide/ (或 flow 配置的 sIdeUrl)
```

### 阶段 5：sAMSTrusteeship 跨 appid 信任机制

战绩 flow 配置有 `sAMSTrusteeship:1` + `targetQQAppId:1110543085`（不同于制造的 101491592）。
milo SDK 的处理（QC 渠道分支）：

```js
if (c.sAMSTrusteeship) {
  // QC 分支
  d.sAMSAcctype = "qq";
  d.sAMSAccessToken = access_token;
  d.sAMSAppOpenId = openid;
  d.sAMSTargetAppId = "1110543085";
  d.sAMSSourceAppId = source_appid;
  d.openid = "<sAMSGameOpenId>";  // 占位符，服务端解析
  d.iUin = "<sAMSGameOpenId>";
  d.uin = "<sAMSGameOpenId>";
}
```

**实测**：直接带这些参数仍返 100。真正的鉴权依赖 AMS SDK 实例 `a` 在初始化时建立的**会话 token**（`ieg_ams_session_token`），它存在于 SDK 内存状态中，每次 `emit` 复用。

### 阶段 6：最终根因（决定性突破）

#### 真相：App 登录的是错误的 appid

从微信 MMKV 存储读到小程序的真实凭证：
```
小程序凭证（战绩能用）：
  appid:        1112438254    ← 三角洲小程序自己的 appid
  openid:       7053B0093C60CFCE2283C3DD84AC49C3
  access_token: DA8567E0C303A66C7A4180267ADF6560

App 凭证（战绩 ret=100）：
  appid:        101491592     ← 特勤处制造 H5 的 appid（df.qq.com 登录拿到）
  openid:       CB1FEA2C16981857ABBAB2A9696A384D
```

用小程序的凭证(appid=1112438254)请求 450526 → **`ret=0 succ`，返回今日对局数据**！

#### 为什么"制造能显示、战绩不能"

- 制造接口(365589)：对 appid 宽容，101491592 和 1112438254 都放行
- 战绩接口(450526)：**严格校验 appid**，必须是三角洲小程序 appid(1112438254)
- App 的登录流程走 `df.qq.com`（特勤处制造 H5），拿到的是 101491592 的凭证
- 微信小程序走自己的登录，拿到的是 1112438254 的凭证

#### MMKV 读取（root 提取小程序凭证）

```
文件: /data/data/com.tencent.mm/files/mmkv/AppBrandMMKVStorage<uin>
含:  wx<appid>__qq_ieg_ams_session_token / openid / access_token / 角色(area/roleid/nickname)
```

```bash
adb shell "su -c 'cat /data/data/com.tencent.mm/files/mmkv/AppBrandMMKVStorage4093052208 | strings | grep -iE \"ieg_ams|access_token|openid\"'"
```

## 四、关键技术细节备忘

### wxapkg 提取的坑

| 坑 | 解法 |
|----|------|
| `adb pull /sdcard/x` → `D:/Git/sdcard/x`（Git Bash 路径转换） | `MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"` |
| root 文件 `cp` 后权限不对 | `chmod 644` |
| base64 经 adb shell 换行导致截断 | 改用 cp 到 /sdcard 再 pull |
| wxapkg 看似二进制 | `latin1` toString 直接搜，业务 JS 多为明文 |

### AMS 接口调用三要素

```
POST https://(comm\|dfm).ams.game.qq.com/ide/?iChartId=X&iSubChartId=X&sIdeToken=T&source=2
Content-Type: application/x-www-form-urlencoded
Cookie: openid=...; acctype=qc; appid=...; access_token=...
Body: openid=...&acctype=qc&appid=...&access_token=...&source=2&<业务参数>
```

- **chartId/token 映射**：动态，调 `/ide/page/{activityId}` 获取
- **activityId（三角洲）**：`21_3LXYAj`
- **域名**：`comm.ams.game.qq.com`（通用）或 `dfm.ams.game.qq.com`（部分 flow 指定）

### 关键 chartId 清单（2026-07 时点）

| 功能 | chartId | token | sName | 状态 |
|------|---------|-------|-------|------|
| 特勤处制造 | 365589 | bQaMCQ | 查询制造处状态 | ✅ 可用 |
| 物资列表 | 316969 | NoOapI | - | ✅ |
| 配置 | 316968 | KfXJwH | - | ✅ |
| **战绩列表v2** | **450526** | **PHq59Y** | 查询流水信息\|战绩v2 | ❌ 下线(ret=100) |
| **战绩列表v1** | **319386** | **zMemOt** | 查询流水信息\|战绩 | ❌ 下线(ret=100) |
| 战绩详情v2 | 450471 | ylP3eG | 查询流水信息\|战绩详情v2 | ❓ |
| 单局评语 | 468605 | 8NBbh4 | 单局评语 | ✅ 在线(190001=参数错) |
| 今日密码 | 316969 | NoOapI | dfm/center.day.secret | ✅ |

## 五、可行的修复方向

### 根因总结
App 登录走 `df.qq.com`（特勤处制造 H5，appid=101491592），但战绩接口要求
三角洲小程序 appid(1112438254)。制造接口对 appid 宽容所以能过，战绩严格校验所以失败。

### 方向 A：App 登录时同时获取两个 appid 的凭证（推荐）

三角洲相关 appid 至少两个：
- `101491592` —— 特勤处制造（df.qq.com，App 当前登录目标）
- `1112438254` —— 三角洲小程序（战绩/今日密码等需要）

腾讯登录体系里，同一 QQ 的不同 appid 有各自的 access_token。
修复思路：登录后用 cookie 调用三角洲小程序的登录接口，换取 1112438254 的凭证，
战绩相关请求改用这个 appid 的凭证。

需调研：df.qq.com 登录后能否免密换取 1112438254 的 token（通常可以，同源 QQ 登录态）。

### 方向 B：root 设备直接读微信 MMKV（仅限 root 用户）
从 `/data/data/com.tencent.mm/files/mmkv/AppBrandMMKVStorage<uin>` 读取小程序凭证。
缺点：只适用 root 设备，普通用户不可用。

### 方向 C：接口分凭证调用（最小改动）
保留 101491592 用于制造，新增 1112438254 凭证用于战绩/今日密码等严格校验的接口。
关键是解决"如何获取 1112438254 的 access_token"。

## 六、方法论总结（最重要）

1. **先加诊断日志打印 raw response** —— 90% 的问题靠看原始返回就能定性
2. **用已知成功的接口做对照** —— 排除凭证/网络等公共因素
3. **wxapkg 是腾讯小程序逆向的金矿** —— root 可读，业务 JS 多明文
4. **区分错误码语义** —— 100/101/190001/107 各代表不同含义
5. **找动态配置接口** —— `/ide/page/{activityId}` 这类接口会暴露完整 token/chartId 映射
6. **不要盲改请求参数** —— 先理解 SDK 的完整请求链路，否则只是在猜
7. **Git Bash on Windows 的路径转换是隐形杀手** —— adb 操作务必 `MSYS_NO_PATHCONV=1`
