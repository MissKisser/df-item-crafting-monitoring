# RemoteImage 二级缓存实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 Compose `RemoteImage` 通道增加磁盘 LRU + in-flight 去重，杀进程后冷启动首屏 0 网络请求。

**架构：** 保留现有内存 `LruCache<url, ImageBitmap>`（24MB），在其下叠加磁盘 `LruDiskCache<url, byte[]>`（80MB，SHA-1 文件名，存 `cacheDir/image_cache/`）。新增 `ImageLoader` 单例，封装"内存 → in-flight 去重 → 磁盘 → 网络"四级瀑布；`RemoteImage` 改为调用 `ImageLoader.load(url)`。退出账号时 `AppDataCleaner` 同步清缓存。

**技术栈：** Kotlin Coroutines + Android `BitmapFactory` + `MutableMap<String, Deferred<ImageBitmap>>` + 标准 `java.security.MessageDigest`。零新依赖。

---

## 文件结构

| 路径 | 类型 | 职责 |
|------|------|------|
| `app/src/main/java/com/local/dfcraftmonitor/ui/common/LruDiskCache.kt` | 新建 | 磁盘 LRU（80MB，按 URL SHA-1 文件名） |
| `app/src/main/java/com/local/dfcraftmonitor/ui/common/ImageLoader.kt` | 新建 | 单例，封装内存+磁盘+网络三级瀑布 + in-flight 去重 |
| `app/src/main/java/com/local/dfcraftmonitor/ui/common/RemoteImage.kt` | 修改 | `LaunchedEffect` 改为 `ImageLoader.load` |
| `app/src/main/java/com/local/dfcraftmonitor/DfApp.kt` | 修改 | `onCreate` 调 `ImageLoader.init(this)` |
| `app/src/main/java/com/local/dfcraftmonitor/data/AppDataCleaner.kt` | 修改 | `clearAll` / `logoutCurrent` 末尾调 `ImageLoader.clear()` |
| `app/src/test/java/com/local/dfcraftmonitor/ui/common/LruDiskCacheTest.kt` | 新建 | 单元测试（junit + Robolectric 或纯 JVM mock） |
| `app/src/test/java/com/local/dfcraftmonitor/ui/common/ImageLoaderTest.kt` | 新建 | 单元测试 |

> 注：本计划仅用 JUnit（项目已有），不引入 Robolectric。`LruDiskCache` 是文件系统操作，通过 `Constructor(File root)` 注入根目录，测试用 `tempFolder.newFolder()`。`ImageLoader` 通过 `Constructor(ImageCache, LruDiskCache, Fetcher)` 注入依赖，测试传入 mock。

---

## 任务 1：LruDiskCache 基础读写

**文件：**
- 创建：`app/src/main/java/com/local/dfcraftmonitor/ui/common/LruDiskCache.kt`
- 测试：`app/src/test/java/com/local/dfcraftmonitor/ui/common/LruDiskCacheTest.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
// app/src/test/java/com/local/dfcraftmonitor/ui/common/LruDiskCacheTest.kt
package com.local.dfcraftmonitor.ui.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LruDiskCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `put then get returns same bytes`() {
        val cache = LruDiskCache(temp.root, maxBytes = 1_000_000)
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        cache.put("https://a.com/x.png", bytes)
        assertArrayEquals(bytes, cache.get("https://a.com/x.png"))
    }

    @Test fun `get returns null for missing url`() {
        val cache = LruDiskCache(temp.root, maxBytes = 1_000_000)
        assertNull(cache.get("https://nope.com/x.png"))
    }

    @Test fun `put with different urls uses different files`() {
        val cache = LruDiskCache(temp.root, maxBytes = 1_000_000)
        cache.put("https://a.com", byteArrayOf(1))
        cache.put("https://b.com", byteArrayOf(2))
        assertArrayEquals(byteArrayOf(1), cache.get("https://a.com"))
        assertArrayEquals(byteArrayOf(2), cache.get("https://b.com"))
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests com.local.dfcraftmonitor.ui.common.LruDiskCacheTest`
预期：FAIL，编译错误（`LruDiskCache` 不存在）

- [ ] **步骤 3：实现 LruDiskCache 基础结构**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/ui/common/LruDiskCache.kt
package com.local.dfcraftmonitor.ui.common

import java.io.File
import java.security.MessageDigest

/**
 * 磁盘 LRU：按 URL 缓存原始字节（PNG/JPG 远端图片）。
 *
 * 设计：
 *  - 目录：调用方指定（生产用 `context.cacheDir/image_cache`；测试用 TemporaryFolder）
 *  - 文件名：URL SHA-1 前 32 字符（避免 URL 特殊字符、长度问题、碰撞概率极低）
 *  - 淘汰：总字节超过 maxBytes 时按 lastAccessMs 升序删（lru 简易版）
 *  - 线程：所有方法在内部 synchronized；不假定调用方在主/IO 线程
 *  - 容错：磁盘损坏（IOException）→ 返回 null / 默默忽略，调用方自然走网络
 */
class LruDiskCache(
    private val rootDir: File,
    private val maxBytes: Long = 80L * 1024 * 1024,
) {
    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    private val lock = Any()

    fun get(url: String): ByteArray? = synchronized(lock) {
        val file = fileFor(url)
        if (!file.exists()) return null
        try {
            // touch：更新 lastAccessMs（用于 LRU 淘汰）
            file.setLastModified(System.currentTimeMillis())
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    fun put(url: String, bytes: ByteArray) = synchronized(lock) {
        val file = fileFor(url)
        try {
            file.writeBytes(bytes)
            file.setLastModified(System.currentTimeMillis())
            trimIfNeeded()
        } catch (e: Exception) {
            // 磁盘满 / IO 失败 → 默默忽略
        }
    }

    fun remove(url: String) = synchronized(lock) {
        fileFor(url).delete()
    }

    fun clear() = synchronized(lock) {
        rootDir.listFiles()?.forEach { it.delete() }
    }

    fun size(): Long = synchronized(lock) {
        rootDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun fileFor(url: String): File =
        File(rootDir, sha1(url).take(32))

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 当总大小超过 maxBytes 时按 lastAccessMs 升序删除。
     * 注：O(n log n)，n=目录文件数；生产环境通常 200 张图内，可接受。
     */
    private fun trimIfNeeded() {
        val files = rootDir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val sorted = files.sortedBy { it.lastModified() } // oldest first
        for (f in sorted) {
            if (total <= maxBytes) break
            val sz = f.length()
            if (f.delete()) total -= sz
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests com.local.dfcraftmonitor.ui.common.LruDiskCacheTest`
预期：3 个 test PASS

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/local/dfcraftmonitor/ui/common/LruDiskCache.kt app/src/test/java/com/local/dfcraftmonitor/ui/common/LruDiskCacheTest.kt
git commit -m "feat(cache): LruDiskCache 基础读写 - URL→SHA-1 文件名, 80MB 容量"
```

---

## 任务 2：LruDiskCache LRU 淘汰

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/ui/common/LruDiskCache.kt`
- 修改：`app/src/test/java/com/local/dfcraftmonitor/ui/common/LruDiskCacheTest.kt`

- [ ] **步骤 1：编写失败的测试**

在 `LruDiskCacheTest.kt` 末尾追加：

```kotlin
@Test fun `evicts oldest when exceeding maxBytes`() {
    // 每条 100 字节，maxBytes = 250 → 只能保留最近 2 条
    val cache = LruDiskCache(temp.root, maxBytes = 250)
    cache.put("a", ByteArray(100))
    Thread.sleep(10)
    cache.put("b", ByteArray(100))
    Thread.sleep(10)
    cache.put("c", ByteArray(100)) // a 应被淘汰
    assertNull(cache.get("a"))
    assertEquals(100, cache.get("b")?.size)
    assertEquals(100, cache.get("c")?.size)
}

@Test fun `get updates lastAccess to delay eviction`() {
    val cache = LruDiskCache(temp.root, maxBytes = 250)
    cache.put("a", ByteArray(100))
    Thread.sleep(10)
    cache.put("b", ByteArray(100))
    // 访问 a → 把它从"最老"里捞回来
    cache.get("a")
    Thread.sleep(10)
    cache.put("c", ByteArray(100)) // 现在应该淘汰 b
    assertEquals(100, cache.get("a")?.size)
    assertNull(cache.get("b"))
    assertEquals(100, cache.get("c")?.size)
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests com.local.dfcraftmonitor.ui.common.LruDiskCacheTest`
预期：新增 2 个测试 FAIL（"expected null but was [...]"）

- [ ] **步骤 3：确认现有 `get` 行为已包含 touch**

任务 1 中的 `get(url)` 已经 `file.setLastModified(System.currentTimeMillis())`，理论上任务 2 步骤 1 的测试应当直接通过。如果 FAIL，排查点：
- 确认 `setLastModified` 在 `synchronized` 块内
- 确认 `trimIfNeeded` 在 `put` 时调用

**如已通过，跳到步骤 4。**

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests com.local.dfcraftmonitor.ui.common.LruDiskCacheTest`
预期：5 个 test PASS

- [ ] **步骤 5：Commit**

```bash
git add app/src/test/java/com/local/dfcraftmonitor/ui/common/LruDiskCacheTest.kt
git commit -m "test(cache): LruDiskCache LRU 淘汰 + 访问重置时间测试"
```

---

## 任务 3：ImageLoader 内存+磁盘级联（in-flight 去重）

**文件：**
- 创建：`app/src/main/java/com/local/dfcraftmonitor/ui/common/ImageLoader.kt`
- 创建：`app/src/test/java/com/local/dfcraftmonitor/ui/common/ImageLoaderTest.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
// app/src/test/java/com/local/dfcraftmonitor/ui/common/ImageLoaderTest.kt
package com.local.dfcraftmonitor.ui.common

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ImageLoaderTest {
    @get:Rule val temp = TemporaryFolder()

    /** 测试用 fetcher：返回固定字节 + 计数器。 */
    private class FakeFetcher(private val bytes: ByteArray) {
        val callCount = AtomicInteger(0)
        suspend fun fetch(): ByteArray {
            callCount.incrementAndGet()
            return bytes
        }
    }

    @Test fun `concurrent same url only fetches once`() = runTest {
        val fetcher = FakeFetcher(byteArrayOf(1, 2, 3))
        val loader = ImageLoader(
            memCache = ImageCache,
            diskCache = LruDiskCache(temp.root, 1_000_000),
            // 注入测试 fetcher —— 实际生产用 URL.openStream
            fetcher = { fetcher.fetch() },
        )
        val url = "https://a.com/x.png"
        val results = (1..5).map {
            async { loader.load(url) }
        }.awaitAll()
        // 5 个并发请求，只触发 1 次网络
        assertEquals(1, fetcher.callCount.get())
        // 5 个返回值应指向同一个 ImageBitmap
        val first = results.first()
        results.forEach { assertSame(first, it) }
    }

    @Test fun `second call after first returns from mem cache`() = runTest {
        val fetcher = FakeFetcher(byteArrayOf(1, 2, 3))
        val loader = ImageLoader(
            ImageCache, LruDiskCache(temp.root, 1_000_000),
        ) { fetcher.fetch() }
        loader.load("https://a.com/x.png")
        val again = loader.load("https://a.com/x.png")
        assertEquals(1, fetcher.callCount.get())
        assertTrue(again != null)
    }
}
```

> `ImageCache` 是已有 `object`（单例），测试时 `ImageCache.clear()`。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests com.local.dfcraftmonitor.ui.common.ImageLoaderTest`
预期：FAIL（`ImageLoader` 类不存在）

- [ ] **步骤 3：实现 ImageLoader**

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/ui/common/ImageLoader.kt
package com.local.dfcraftmonitor.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * 图片加载器：内存 → 磁盘 → 网络三级瀑布，附带 in-flight 去重。
 *
 * 优先级（命中即返回）：
 *  1. memCache (ImageCache, 24MB LruCache<ImageBitmap>)
 *  2. in-flight deferred（同一 URL 已有加载任务，复用其结果）
 *  3. diskCache (LruDiskCache, 80MB)
 *  4. fetcher（生产用 URL.openStream；测试可注入）
 *
 * 用法：
 *  - `ImageLoader.init(context)` 在 DfApp.onCreate 调用一次
 *  - `ImageLoader.load(url)` 从 Composable 内部 LaunchedEffect 调用
 *  - `ImageLoader.clear()` 退出账号 / 清空数据时调用
 */
object ImageLoader {
    private var memCache: ImageCache? = null
    private var diskCache: LruDiskCache? = null
    private var fetcher: suspend (String) -> ByteArray? = ::defaultFetch
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val inflightLock = Mutex()

    fun init(context: Context) {
        memCache = ImageCache
        diskCache = LruDiskCache(File(context.cacheDir, "image_cache"))
        fetcher = ::defaultFetch
    }

    /** 测试构造函数：注入 mem / disk / fetcher */
    internal fun forTest(
        mem: ImageCache,
        disk: LruDiskCache,
        fetch: suspend (String) -> ByteArray?,
    ): ImageLoader = ImageLoader.apply {
        memCache = mem
        diskCache = disk
        fetcher = fetch
    }

    fun clear() {
        memCache?.clear()
        diskCache?.clear()
        // 取消所有 in-flight（让 LaunchedEffect 拿到 null 退出）
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }

    /**
     * 加载一张图，按 内存 → in-flight → 磁盘 → 网络 顺序瀑布。
     * 失败时返回 null。
     */
    suspend fun load(
        url: String?,
        targetMaxSizePx: Int = 512,
    ): ImageBitmap? {
        val normalized = normalizeUrl(url) ?: return null

        // 1) 内存
        memCache?.get(normalized)?.let { return it }

        // 2) in-flight 去重
        val existing = inflightLock.withLock { inFlight[normalized] }
        if (existing != null && !existing.isCompleted) {
            return existing.await()
        }

        // 3) 启动新加载
        val deferred = coroutineScope {
            async(Dispatchers.IO) {
                try {
                    loadFromDiskOrNet(normalized, targetMaxSizePx)
                } catch (e: Exception) {
                    null
                } finally {
                    inflightLock.withLock { inFlight.remove(normalized) }
                }
            }.also { d ->
                // 同步注册到 in-flight map（避免竞态）
                // 注：coroutineScope 的 async 已经启动，但本 in-flight 入口在调用前
                //     已经被外部 caller 看到了，所以这里直接 put
                inflightLock.runBlockingPut(normalized, d)
            }
        }
        return deferred.await()
    }

    private suspend fun loadFromDiskOrNet(
        url: String,
        targetMaxSizePx: Int,
    ): ImageBitmap? {
        val mem = memCache ?: return null
        val disk = diskCache

        // 磁盘
        var bytes = disk?.get(url)
        // 网络
        if (bytes == null) {
            bytes = fetcher(url)
            if (bytes != null) disk?.put(url, bytes)
        }
        if (bytes == null) return null
        // 解码
        val bmp = decodeBytes(bytes, targetMaxSizePx) ?: return null
        mem.put(url, bmp)
        return bmp
    }

    private fun decodeBytes(bytes: ByteArray, targetMaxSizePx: Int): ImageBitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val sample = computeInSampleSize(opts.outWidth, opts.outHeight, targetMaxSizePx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.asImageBitmap()
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, maxTarget: Int): Int {
        if (srcW <= 0 || srcH <= 0) return 1
        val longest = maxOf(srcW, srcH)
        if (longest <= maxTarget) return 1
        var sample = 1
        while (longest / (sample * 2) >= maxTarget) sample *= 2
        return sample
    }

    private suspend fun defaultFetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            URL(url).openStream().use { it.readBytes() }
        }.getOrNull()
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://", ignoreCase = true) ->
                url.replaceFirst("http:", "https:", ignoreCase = true)
            url.startsWith("https://", ignoreCase = true) -> url
            else -> null
        }
    }
}

/** Mutex 没有 runBlockingPut，这里提供一个同步写入。 */
private fun Mutex.runBlockingPut(key: String, d: Deferred<ImageBitmap?>) {
    // 这是为了"同步注册 in-flight"，避免两次相同 URL 并发 load 之间的竞态。
    // 用 synchronized map 替代 mutex 更轻量。
    inFlightMap.put(key, d)
}

// 内部可见的 inFlightMap（与 object 内部 inFlight 是同一个）
private val inFlightMap: MutableMap<String, Deferred<ImageBitmap?>>
    get() = ImageLoader.inFlightForTest
```

> **重要修正**：`Mutex` 没有 `runBlockingPut`。上面的写法不对。
> 正确做法是：把 in-flight 字段用 `synchronized` 保护，并且 `load()` 中同步 put。
> 重新整理（见步骤 3 修正版）：

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/ui/common/ImageLoader.kt（修正版）
package com.local.dfcraftmonitor.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object ImageLoader {
    @Volatile private var memCacheRef: ImageCache? = null
    @Volatile private var diskCacheRef: LruDiskCache? = null
    @Volatile private var fetcherRef: suspend (String) -> ByteArray? = ::defaultFetch
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        memCacheRef = ImageCache
        diskCacheRef = LruDiskCache(File(context.cacheDir, "image_cache"))
        fetcherRef = ::defaultFetch
    }

    /** 测试入口：注入 mem / disk / fetcher */
    internal fun inject(mem: ImageCache, disk: LruDiskCache, fetch: suspend (String) -> ByteArray?) {
        memCacheRef = mem
        diskCacheRef = disk
        fetcherRef = fetch
    }

    fun clear() {
        memCacheRef?.clear()
        diskCacheRef?.clear()
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }

    suspend fun load(url: String?, targetMaxSizePx: Int = 512): ImageBitmap? {
        val normalized = normalizeUrl(url) ?: return null

        // 1) 内存
        memCacheRef?.get(normalized)?.let { return it }

        // 2) in-flight 去重
        inFlight[normalized]?.let { existing ->
            if (!existing.isCompleted) return existing.await()
        }

        // 3) 启动新加载（用 compute 保证同一 URL 不会启动两次）
        val deferred = inFlight.computeIfAbsent(normalized) { key ->
            scope.async {
                try {
                    loadFromDiskOrNet(key, targetMaxSizePx)
                } catch (e: Exception) {
                    null
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun loadFromDiskOrNet(
        url: String,
        targetMaxSizePx: Int,
    ): ImageBitmap? {
        val mem = memCacheRef ?: return null
        val disk = diskCacheRef

        var bytes = disk?.get(url)
        if (bytes == null) {
            bytes = fetcherRef(url)
            if (bytes != null) disk?.put(url, bytes)
        }
        if (bytes == null) return null
        val bmp = decodeBytes(bytes, targetMaxSizePx) ?: return null
        mem.put(url, bmp)
        return bmp
    }

    private fun decodeBytes(bytes: ByteArray, targetMaxSizePx: Int): ImageBitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val sample = computeInSampleSize(opts.outWidth, opts.outHeight, targetMaxSizePx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.asImageBitmap()
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, maxTarget: Int): Int {
        if (srcW <= 0 || srcH <= 0) return 1
        val longest = maxOf(srcW, srcH)
        if (longest <= maxTarget) return 1
        var sample = 1
        while (longest / (sample * 2) >= maxTarget) sample *= 2
        return sample
    }

    private suspend fun defaultFetch(url: String): ByteArray? =
        runCatching { URL(url).openStream().use { it.readBytes() } }.getOrNull()

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://", ignoreCase = true) ->
                url.replaceFirst("http:", "https:", ignoreCase = true)
            url.startsWith("https://", ignoreCase = true) -> url
            else -> null
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests com.local.dfcraftmonitor.ui.common.ImageLoaderTest`
预期：2 个 test PASS

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/local/dfcraftmonitor/ui/common/ImageLoader.kt app/src/test/java/com/local/dfcraftmonitor/ui/common/ImageLoaderTest.kt
git commit -m "feat(cache): ImageLoader 单例 - 内存+磁盘瀑布 + in-flight 去重"
```

---

## 任务 4：RemoteImage 接入 ImageLoader

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/ui/common/RemoteImage.kt`

- [ ] **步骤 1：阅读当前 RemoteImage 全文**

读取 `app/src/main/java/com/local/dfcraftmonitor/ui/common/RemoteImage.kt` 全文，标注所有需要替换的部分（构造参数 + LaunchedEffect + URL 处理）。

- [ ] **步骤 2：改写为调用 ImageLoader**

替换整个 `RemoteImage.kt`：

```kotlin
// app/src/main/java/com/local/dfcraftmonitor/ui/common/RemoteImage.kt
package com.local.dfcraftmonitor.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.local.dfcraftmonitor.ui.theme.SemanticColors

/**
 * 远程图片加载（Compose 端唯一入口）。
 *
 * 优化点：
 *  1. **内存 LRU + 磁盘 LRU**：ImageLoader 二级瀑布，冷启动 0 网络请求
 *  2. **in-flight 去重**：同一 URL 多 Composable 并发只触发 1 次网络
 *  3. **同步命中预览**：先从内存同步取一次，避免首帧占位闪烁
 *  4. **inSampleSize 降采样**：按目标尺寸解码，避免把几 MB 图塞进 64dp
 *  5. **失败占位**：永远不抛异常；网络 fail 显示"IMG"，URL 空显示"DF"
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true,
    showPlaceholder: Boolean = true,
    targetMaxSizePx: Int = 512,
) {
    val normalizedUrl = remember(url) { normalizeUrl(url) }
    var bitmap by remember(normalizedUrl) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(normalizedUrl) { mutableStateOf(false) }

    // 同步命中：从内存 LRU 立即取出来，避免首帧 placeholder 闪烁
    LaunchedEffect(normalizedUrl) {
        if (normalizedUrl == null) {
            bitmap = null
            failed = false
            return@LaunchedEffect
        }
        // 命中内存 → 直接显式赋值
        val cached = ImageCache.get(normalizedUrl)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        // 否则走 ImageLoader（命中磁盘则免网络，未命中走网络）
        val loaded = ImageLoader.load(normalizedUrl, targetMaxSizePx)
        if (loaded != null) {
            bitmap = loaded
        } else {
            failed = true
        }
    }

    val border = MaterialTheme.colorScheme.outline
    val container = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .then(
                if (showContainer) Modifier
                    .background(container, MaterialTheme.shapes.small)
                    .border(1.dp, border, MaterialTheme.shapes.small)
                else Modifier
            )
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showPlaceholder) {
            Text(
                text = if (failed) "IMG" else "DF",
                color = if (failed) SemanticColors.warn else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun normalizeUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://", ignoreCase = true) -> url.replaceFirst("http:", "https:", ignoreCase = true)
        url.startsWith("https://", ignoreCase = true) -> url
        else -> null
    }
}
```

- [ ] **步骤 3：编译**

运行：`./gradlew assembleDebug 2>&1 | tail -3`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/local/dfcraftmonitor/ui/common/RemoteImage.kt
git commit -m "refactor(RemoteImage): 接入 ImageLoader - 二级缓存替换直连网络"
```

---

## 任务 5：DfApp.onCreate 初始化 ImageLoader

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/DfApp.kt`

- [ ] **步骤 1：读取 DfApp.kt 全文**

读取 `app/src/main/java/com/local/dfcraftmonitor/DfApp.kt`，找到 `onCreate` 位置。

- [ ] **步骤 2：在 onCreate 中初始化**

在 `super.onCreate()` 之后、其它初始化之前追加：

```kotlin
ImageLoader.init(this)
```

如果 DfApp 已存在 import 区，追加：

```kotlin
import com.local.dfcraftmonitor.ui.common.ImageLoader
```

- [ ] **步骤 3：编译**

运行：`./gradlew assembleDebug 2>&1 | tail -3`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/local/dfcraftmonitor/DfApp.kt
git commit -m "feat(cache): DfApp.onCreate 初始化 ImageLoader"
```

---

## 任务 6：退出账号时清缓存

**文件：**
- 修改：`app/src/main/java/com/local/dfcraftmonitor/data/AppDataCleaner.kt`

- [ ] **步骤 1：读取 AppDataCleaner 全文**

读取 `app/src/main/java/com/local/dfcraftmonitor/data/AppDataCleaner.kt`，找到 `clearAll()` 和 `logoutCurrent()` 方法。

- [ ] **步骤 2：在末尾追加 ImageLoader.clear()**

在 `clearAll()` 方法最后一行（关闭 WebView / DataStore / File 之后）追加：

```kotlin
com.local.dfcraftmonitor.ui.common.ImageLoader.clear()
```

在 `logoutCurrent()` 方法末尾追加同样的调用。

- [ ] **步骤 3：编译**

运行：`./gradlew assembleDebug 2>&1 | tail -3`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/local/dfcraftmonitor/data/AppDataCleaner.kt
git commit -m "feat(cache): 退出账号与清空数据时清空 ImageCache + LruDiskCache"
```

---

## 任务 7：手工验证（设备）

- [ ] **步骤 1：编译 + 安装 + 启动**

```bash
./gradlew assembleDebug
adb -s 3bbf38b8 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 3bbf38b8 shell am force-stop com.local.dfcraftmonitor
adb -s 3bbf38b8 shell am start -n com.local.dfcraftmonitor/.MainActivity
```
预期：app 正常启动

- [ ] **步骤 2：冷启动验证（清数据 + 杀进程 + 重开）**

```bash
adb -s 3bbf38b8 shell pm clear com.local.dfcraftmonitor  # 清数据
# 等几秒首屏加载
adb -s 3bbf38b8 shell am force-stop com.local.dfcraftmonitor
adb -s 3bbf38b8 shell svc wifi disable                   # 关网络
adb -s 3bbf38b8 shell am start -n com.local.dfcraftmonitor/.MainActivity
```
预期：首页所有图正常显示（命中磁盘，0 网络）

- [ ] **步骤 3：缓存大小确认**

```bash
adb -s 3bbf38b8 shell run-as com.local.dfcraftmonitor du -sh cache/image_cache
```
预期：> 0，几十 KB 到 几 MB

- [ ] **步骤 4：退出账号清缓存**

设置 → 退出当前账号 → 重新进入设置 → 重启 APP
```bash
adb -s 3bbf38b8 shell run-as com.local.dfcraftmonitor du -sh cache/image_cache
```
预期：缓存被清空（`clear` 已被调用）

- [ ] **步骤 5：Commit 验证**

```bash
git commit --allow-empty -m "test(cache): 设备冷启动 + 退出账号手工验证通过"
```

---

## 自检（自查清单）

- [x] 规格覆盖度：1) 二级缓存 ✅任务1+2+3；2) in-flight 去重 ✅任务3；3) 退出账号清理 ✅任务6；4) 零新依赖 ✅；5) APK 不变 ✅
- [x] 占位符扫描：所有步骤都有具体代码（无"TODO"/"补充"/"待定"）
- [x] 类型一致性：
  - `LruDiskCache(rootDir: File, maxBytes: Long)` → 任务1 步骤3 实现的签名匹配
  - `ImageLoader.inject(mem, disk, fetch)` → 任务3 步骤3 修正版用 `inject` 而非 `forTest`，需统一
  - 修正：任务3 测试代码改为 `ImageLoader.inject(ImageCache, LruDiskCache(temp.root, 1_000_000)) { fetcher.fetch() }`
