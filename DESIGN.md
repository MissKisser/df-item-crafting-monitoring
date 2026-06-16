# Design

## Source of truth
- Status: Active
- Last refreshed: 2026-06-16
- Primary product surfaces: Android Compose 首页、工具、我的、物品详情弹窗、价格趋势弹窗、登录/失效状态
- Evidence reviewed: `.omx/interface-capture/current-screen.png`, `.omx/interface-capture/app-home.png`, `.omx/interface-capture/app-tools.png`, `.omx/interface-capture/app-mine.png`, `app/src/main/java/com/local/dfcraftmonitor/ui/home/HomeScreen.kt`, `app/src/main/java/com/local/dfcraftmonitor/ui/theme/Theme.kt`, `.omx/specs/df-crafting-monitor-v2-full-upgrade-spec.md`

## Brand
- Personality: 战术、克制、高密度、可信赖，接近三角洲行动微信小程序的信息终端质感。
- Trust signals: 游戏内物品图、暗色作战面板、绿色状态光、倒计时和价格数字的清晰对齐。
- Avoid: 开发术语、接口名、路由、域名、token 字段、说明书式文案、营销式大空白、廉价渐变和泛 SaaS 卡片风。

## Product goals
- Goals: 让玩家打开后马上看到今日情报、制造进度、物价和账号状态；未登录也能浏览公开看板；登录后补全个人数据。
- Non-goals: 不做攻略主入口，不触发点赞、收藏、订阅、领奖、反馈等写操作，不展示任何远程接口或凭据细节。
- Success signals: 首页首屏像游戏小程序而非工程 demo；三页无开发术语；关键图、卡片、弹窗在实机截图中完整可读。

## Personas and jobs
- Primary personas: 三角洲行动玩家、材料/制造/市场价格观察者。
- User jobs: 查看今日密码和奖励线索；判断制造剩余时间；查看物品价格趋势；确认账号绑定状态。
- Key contexts of use: 手机竖屏、碎片时间、弱网络、未登录和登录失效状态。

## Information architecture
- Primary navigation: 首页、工具、我的。
- Core routes/screens: 首页战报和制造看板；工具页物品与地图；我的页身份、绑定、安全和提醒设置。
- Content hierarchy: 游戏信息优先，技术状态后置为用户可理解的“同步状态/账号保护/可用状态”。

## Design principles
- Principle 1: 所有可见文案面向玩家，不面向开发者。
- Principle 2: 用游戏终端视觉组织信息，少用默认 Material 卡片感。
- Tradeoffs: 允许内部保留本地后端和接口目录，但 UI 只能展示玩家语言；未拿到实时数据时可用可信的降级内容，不暴露底层失败细节。

## Visual language
- Color: 背景近黑 `#050908`，面板深绿黑，主高亮电光绿，辅助金色用于价格和重点，红色只用于风险或下跌。
- Typography: 标题厚重，数字用等宽字体；正文短句，不出现长说明。
- Spacing/layout rhythm: 12-16dp 紧凑节奏，首屏高信息密度，分区标题和操作入口稳定对齐。
- Shape/radius/elevation: 半径 2-6dp，边框和内发光表达层级，避免圆润 App 卡片感。
- Motion: 倒计时按秒更新；其余动效克制。
- Imagery/iconography: 优先使用游戏静态图、物品图和简洁图标；图片失败也必须有稳定占位。

## Components
- Existing components to reuse: Compose Material3 基础组件、现有远程图片加载、现有 ViewModel 数据。
- New/changed components: 战报面板、快捷功能网格、制造工位卡、战术分段控件、物品行情卡、玩家档案卡、同步状态条。
- Variants and states: 未登录、登录失效、加载、空数据、图片失败、价格上涨/下跌、制造完成。
- Token/component ownership: `HomeScreen.kt` 可先承载页面级组件；颜色和 typography 逐步上移到 theme。

## Accessibility
- Target standard: 手机竖屏可读，重点文本对比度高。
- Keyboard/focus behavior: 当前移动端触控优先，按钮和卡片保持足够点击面积。
- Contrast/readability: 深色背景上正文不低于中灰，关键数字使用高亮色。
- Screen-reader semantics: 图像和按钮保留中文 contentDescription。
- Reduced motion and sensory considerations: 不使用闪烁动画。

## Responsive behavior
- Supported breakpoints/devices: 1080x2400 实机为主，兼容常见 Android 竖屏。
- Layout adaptations: 使用 LazyColumn/LazyRow，卡片稳定宽高，不靠 viewport 字体缩放。
- Touch/hover differences: 仅触控。

## Interaction states
- Loading: 玩家语言，如“同步中”“正在更新战报”。
- Empty: 说明可浏览内容和登录后可解锁内容，不说接口。
- Error: 显示“同步异常/登录失效”，不显示服务器原文或路由。
- Success: 显示更新时间、状态和业务数据。
- Disabled: 写操作入口不出现。
- Offline/slow network: 图片占位和上次内容仍能撑住布局。

## Content voice
- Tone: 像游戏终端，不像调试台。
- Terminology: 使用“战报、行动、特勤处、物资、行情、同步、账号保护、绑定状态”。
- Microcopy rules: 禁止出现“接口、本地后端、/api、dfm、AMS、OpenID、AppID、token、Cookie、域名、路由、写接口”。

## Implementation constraints
- Framework/styling system: Android Jetpack Compose + Material3。
- Design-token constraints: 不新增依赖；优先用现有 Material3、icons-extended 和自定义 Composable。
- Performance constraints: 图片异步加载，列表保持轻量；实机首屏不能空白。
- Compatibility constraints: 保留现有登录、设置、隐私、后台同步路径。
- Test/screenshot expectations: UI 源码测试禁止开发术语；`testDebugUnitTest`、`assembleDebug`、adb 实机截图必须通过。

## Open questions
- [ ] 是否需要后续继续追小程序更多子页面截图作为工具/我的页视觉基准；当前按已抓首页截图和现有功能自主推断。
