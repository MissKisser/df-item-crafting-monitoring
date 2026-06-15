# M0 ADB 观察记录：特勤处制作详情

## 采集范围

- 设备：`3bbf38b8`
- 状态：ADB authorized / device
- 前台容器：微信小程序容器
- 采集方式：ADB 只读窗口信息、进程列表、UIAutomator 层级、截图
- 未执行：私有目录读取、Token/Cookie 抽取、内存 dump、抓包、代理改写、写入操作

## 前台窗口

```text
mCurrentFocus=Window{c496fa8 u0 com.tencent.mm/com.tencent.mm.plugin.appbrand.ui.AppBrandUI}
mFocusedApp=ActivityRecord{7c2cc2b u0 com.tencent.mm/.plugin.appbrand.ui.AppBrandUI t98}
mTopFullscreenOpaqueWindowState=Window{c496fa8 u0 com.tencent.mm/com.tencent.mm.plugin.appbrand.ui.AppBrandUI}
```

## 相关进程

```text
u0_a307       2876  1459   18837860 386008 0                   0 S com.tencent.mm:push
u0_a117       6900  1459   15071112  58708 0                   0 S com.tencent.soter.soterserver
u0_a307       8401  1459  149671024 927924 0                   0 S com.tencent.mm
u0_a307      27611  1459  243044020 1193000 0                  0 S com.tencent.mm:appbrand0
u0_a307      27624  1459  129149468 548312 0                   0 S com.tencent.mm:appbrand1
u0_a307      28799  1459  379481440 683536 0                   0 S com.tencent.mm:appbrand2
```

## 微信版本

```text
versionCode=3120
minSdk=24
targetSdk=34
versionName=8.0.74
```

## 当前页面

- 路径：小程序首页 → 特勤处 → 制作详情
- 截图：`.omx/m0/df_m0_special_ops_entry_screen.png`
- UIAutomator：`.omx/m0/df_m0_special_ops_window.xml`

UIAutomator 结果只有顶层 `android.widget.FrameLayout` 和导航栏背景，没有可见文本节点。说明小程序主体内容对 UIAutomator 不透明，可能由 Canvas、WebView 或自绘层渲染。

## 截图识别到的制作详情

| 制作设施 | 状态/物品 | 剩余时间 |
| --- | --- | --- |
| 技术中心 | 空闲中 | 无 |
| 工作台 | `5.45x39mm BS` | `01:53:50` |
| 制药台 | `精密护甲维修包` | `03:04:07` |
| 防具台 | `H09 防暴头盔` | `05:17:50` |

## 直接结论

1. ADB 可以稳定识别当前处于微信小程序 `AppBrandUI`。
2. 小程序运行进程包括主进程、push 进程和多个 `appbrand` 进程。
3. 特勤处制作详情页面能在截图层看到四项制造状态。
4. UIAutomator 无法直接读取制造状态文本，因此单靠 Accessibility/UI XML 不足以稳定获取数据。
5. 后续 M0 需要在两个路线中选择：
   - 截图/OCR 路线：低侵入，但可靠性受 UI 和字体影响；
   - 网络接口路线：更适合作为产品数据源，但必须继续遵守只读、不抓取凭据、不绕过校验的安全红线。
