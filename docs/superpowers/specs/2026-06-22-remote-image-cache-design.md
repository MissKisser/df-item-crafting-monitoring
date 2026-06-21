# RemoteImage 二级缓存（内存 + 磁盘）设计

日期：2026-06-22
作者：ZCode
状态：草案 → 待用户确认

---

## 背景

`app/src/main/java/com/local/dfcraftmonitor/ui/common/RemoteImage.kt` 是当前唯一的图片加载 Composable，
已经接管了首页（制造详情、收藏品、密码）、战绩（干员头像、对局图）、我的（头像、段位）、设置（账号头像）等共 **13 处** 调用点。
原版只用了 `URL().openStream()` 同步下载，重复打开页面会重发请求 + 重新解码。

之前的 `ImageCache`（24MB LruCache<ImageBitmap>）只解决了**内存层面**的重复解码，没解决：

1. 杀进程后冷启动 → 全部重新拉（无磁盘缓存）
2. 同一 URL 在多个 Composable 同时进入屏幕 → 多次并发网络请求
3. 退出账号时旧账号的头像/段位图残留在磁盘
4. 磁盘上限 / 监控缺失（理论可无限增长）
5. 失败无重试（网络抖动直接 fail）

加上用户日常反馈的"卡顿"，主要原因是冷启动后首屏同时发起 5+ 个网络请求（头像 + 4 张制造位图 + 收藏品 + 密码图）。

---

## 目标

1. **冷启动首屏 0 网络请求**（所有图命中磁盘 + 内存）
2. **重复进入同一页面 0 网络请求**（命中内存 LRU）
3. **跨账号不残留**（清空本地数据时同步清理图片缓存目录）
4. **不引入新三方依赖**（与现有 K2 选型一致；K1 范围内）
5. **APK 体积 0 增长**

---

## 不在范围

- Widget（RemoteViews）通道：已用 Glide，独立优化
- WebP/HEIF 解码：服务端不返，主要图均为 PNG/JPG
- HTTP ETag / 304：服务端不一定支持；如未来需要可作为独立优化

---

## 设计

### 总体结构（二级缓存）

```
RemoteImage (Composable)
    │
    │ LaunchedEffect(url)
    ▼
ImageLoader (新) ── 单例
    │
    ├── 1) 内存 LRU  (LruCache<url, ImageBitmap>, 24MB)        ← 已有 ImageCache
    ├── 2) in-flight Map<url, Deferred<ImageBitmap>>           ← 新增：去重并发
    ├── 3) 磁盘 LRU  (LruDiskCache<url, byte[]>, 80MB)         ← 新增：冷启动命中
    └── 4) 网络      (URL().openStream + BitmapFactory)        ← 走 OKHttp 替换 URL？
```

### 关键组件

#### 1. `LruDiskCache`（新文件 `app/src/main/java/com/local/dfcraftmonitor/ui/common/LruDiskCache.kt`）

- 路径：`context.cacheDir/image_cache/`（不放在 filesDir，避免被备份）
- 文件名：URL 的 SHA-1（取前 32 字符），避免 URL 过长 / 特殊字符
- 元数据：单独存 `index.json`（暂时先实现 LRU 简化版：每条文件记录 `{url, size, lastAccessMs}`）
- LRU 淘汰：总大小 > 80MB 时按 `lastAccessMs` 升序删
- 接口：
  ```kotlin
  class LruDiskCache(context: Context, maxBytes: Long = 80L * 1024 * 1024) {
      fun get(url: String): ByteArray?     // null = 磁盘无
      fun put(url: String, bytes: ByteArray)
      fun remove(url: String)
      fun clear()                            // 退出账号 / 设置清空时调用
      fun size(): Long                       // 调试用
  }
  ```
- 线程模型：所有公开方法内部加 `synchronized` 保护元数据写

#### 2. `ImageLoader`（新文件 `app/src/main/java/com/local/dfcraftmonitor/ui/common/ImageLoader.kt`）

把 RemoteImage 当前内联的 `LaunchedEffect + Dispatchers.IO` 抽出来：

```kotlin
object ImageLoader {
    private val memCache = ImageCache                 // 已有
    private var diskCache: LruDiskCache? = null       // Context 注入
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap>>()

    suspend fun load(
        url: String,
        targetMaxSizePx: Int = 512,
    ): ImageBitmap? {
        val normalized = normalizeUrl(url) ?: return null
        // 1) 内存
        memCache.get(normalized)?.let { return it }
        // 2) in-flight 去重
        inFlight[normalized]?.let { return it.await() }
        // 3) 启动新加载
        val deferred = CoroutineScope(Dispatchers.IO).async {
            // 3a) 磁盘
            val bytes = diskCache?.get(normalized)
            val bmp: ImageBitmap? = if (bytes != null) {
                decodeBytes(bytes, targetMaxSizePx)
            } else {
                // 3b) 网络
                val fresh = downloadOnce(normalized) ?: return@async null
                diskCache?.put(normalized, fresh)
                decodeBytes(fresh, targetMaxSizePx)
            }
            if (bmp != null) memCache.put(normalized, bmp)
            bmp
        }
        inFlight[normalized] = deferred
        try {
            return deferred.await()
        } finally {
            inFlight.remove(normalized)
        }
    }

    fun init(context: Context) { ... }   // Application onCreate 调用
    fun clear() { ... }                   // 退出账号
}
```

- **in-flight 去重**：解决"同一 URL 多个 Composable 同时进入屏幕"问题
- **三段式优先**：内存 → 磁盘 → 网络
- **失败不缓存**：网络 fail 返回 null，不入 mem/disk

#### 3. `RemoteImage` 重构（修改现有文件）

```kotlin
@Composable
fun RemoteImage(url: String?, contentDescription: String, modifier: Modifier = Modifier, ...) {
    val normalized = remember(url) { normalizeUrl(url) }
    var bitmap by remember(normalized) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(normalized) { mutableStateOf(false) }

    // 命中即显：从 mem cache 同步取一次（避免首帧 placeholder 闪烁）
    LaunchedEffect(normalized) {
        if (normalized == null) {
            bitmap = null; failed = false; return@LaunchedEffect
        }
        ImageLoader.memCache.get(normalized)?.let { bitmap = it; return@LaunchedEffect }
        val loaded = ImageLoader.load(normalized, targetMaxSizePx)
        if (loaded != null) bitmap = loaded else failed = true
    }
    // 其余渲染逻辑保持不变
}
```

#### 4. `DfApp` 初始化（修改 `app/src/main/java/com/local/dfcraftmonitor/DfApp.kt`）

```kotlin
@HiltAndroidApp
class DfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ImageLoader.init(this)
    }
}
```

#### 5. 退出账号清理（修改 `app/src/main/java/com/local/dfcraftmonitor/data/AppDataCleaner.kt`）

在 `clearAll()` / `logoutCurrent()` 末尾追加 `ImageLoader.clear()`。

---

## 数据流

### 冷启动首屏（之前 5+ 个并发网络 → 现在 0 网络）

```
[App 启动] ImageLoader.init() → LruDiskCache(context, 80MB) → 扫描 cacheDir/image_cache/
[首页打开] 8 个 RemoteImage 并发 LaunchedEffect
    ├─ 头像 URL          → in-flight 1st: 查 disk (命中) → decode → 写 mem → 1st bitmap
    ├─ 头像框 URL        → 同上
    ├─ 4 张制造位图       → 同上，全部命中磁盘，0 网络
    └─ 收藏品图 2 张      → 同上
[结果] 0 网络请求，0 ANR，1~2 帧内全部显示
```

### 重复进入同一页面（之前 0 复用 → 现在 100% 命中内存）

```
[HomeScreen 重组] 4 个 StationCard RemoteImage 触发 LaunchedEffect
    └─ ImageLoader.load() → 内存 LRU 命中 → 直接返回 ImageBitmap
[结果] 0 网络、0 解码、0 磁盘 IO
```

### 退出账号（之前图片残留 → 现在彻底清空）

```
[用户点退出] SettingsViewModel.logoutCurrent()
    → AppDataCleaner.logoutCurrent()
        → ImageLoader.clear()
            → LruDiskCache.clear()  (delete cacheDir/image_cache/)
            → LruCache<ImageBitmap>.evictAll()  (in-memory)
        → DataStore / WebView cookie / etc (已有逻辑)
[结果] 磁盘 / 内存均无残留
```

---

## 容量与淘汰

| 层级 | 上限 | 淘汰策略 | 失效 |
|------|------|---------|------|
| 内存 | 24MB（已有） | LruCache 按 byte count | 进程退出 / 显式 clear() |
| 磁盘 | **80MB**（新） | LRU 按 lastAccessMs | 显式 clear() / 用户清空数据 |
| 网络 | 无 | — | — |

> 80MB 估算：1080p 屏幕，列表约 30 张图，单张平均 ~2MB（远程 PNG 全尺寸）。即使全部驻留也只占 60MB，留 20MB buffer。

---

## 错误处理

- **网络失败**：返回 null，不入 mem / disk；UI 显示"IMG" 占位（已有）
- **磁盘损坏**（如 SD 卡被拔出）：`LruDiskCache.get()` 抛 IOException → 内部 catch → 返回 null → 走网络
- **磁盘满**：`put()` 失败 → 默默忽略（下次重启会自动重新下）
- **URL 为 null / 空**：占位文案 "DF"
- **Bitmap 解码失败**（格式异常）：返回 null，不入 mem；不入 disk

---

## 测试

### 单元测试（新增）
- `LruDiskCacheTest`：put/get、超过上限淘汰、LRU 顺序、clear、文件损坏
- `ImageLoaderTest`：mock 网络 + 磁盘 → 三层优先级
- 现有 `ImageCache` 测试（如有）继续通过

### 手工验证（设备）
1. **冷启动**：清空应用数据 → 打开 APP → 关掉网络 → 杀进程 → 重开 → 首页所有图应正常显示（命中磁盘）
2. **重复进入**：首页 → 切到「战绩」→ 切回「首页」→ 不应再发起任何网络请求（logcat 过滤 `ImageLoader.load`）
3. **退出账号**：设置 → 退出当前账号 → 重进设置/首页 → 图重新拉（缓存已清）
4. **内存压力**：连续打开多个页面 → OOM 不应发生（mem 24MB 上限由 LruCache 保证）
5. **并发去重**：快速点 Home/Mine 切来切去 → 同时 5+ 个相同头像/段位图 RemoteImage 入屏 → in-flight map 只触发 1 次网络（其余走 deferred await）

---

## 影响范围

- **新增文件**：
  - `ui/common/LruDiskCache.kt`（~120 行）
  - `ui/common/ImageLoader.kt`（~100 行）
- **修改文件**：
  - `ui/common/RemoteImage.kt`（内联 LaunchedEffect 改为 ImageLoader.load）
  - `DfApp.kt`（onCreate 初始化）
  - `data/AppDataCleaner.kt`（退出账号 / 清空数据时清缓存）
- **测试文件**：
  - `LruDiskCacheTest.kt`（~80 行）
  - `ImageLoaderTest.kt`（~100 行）
- **完全不动**：HomeTab / BattleTab / MineTab / SettingsScreen 等 13 个调用点
- **APK 体积**：+0
- **依赖**：+0

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| 磁盘 IO 在主线程 | `LruDiskCache` 全部用 `Dispatchers.IO` 调用，调用方走 Coroutine |
| 首次冷启动构建磁盘 cache 索引慢 | 启动时不扫描，元数据按需加载；80MB 内可接受 |
| 多账号 / 多设备共享磁盘目录 | 目录在 app private cacheDir，OS 自动按 user 隔离 |
| SHA-1 碰撞 | 32 字符已足够（生日悖论：2^80 量级），URL 命名空间远小于此 |
| 缓存命中率低（如用户清空） | 1 次冷启动后所有图再次进内存/磁盘，下次 100% 命中 |

---

## 不做的项（YAGNI）

- ❌ HTTP ETag / 304
- ❌ Bitmap 复用 inBitmap（当前 ARGB_8888 + 24MB LRU 内存够用）
- ❌ WebP / HEIF 解码
- ❌ 图片变换（圆角、灰度）
- ❌ 进度回调
- ❌ 占位 / 错误图占位资源
- ❌ Widget 通道改造
