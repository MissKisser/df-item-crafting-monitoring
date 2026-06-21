package com.local.dfcraftmonitor.ui.common

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * 内存图片缓存。
 *
 * 背景：首页/工具页/我的页/弹窗里有几十张远程图（干员头像/物品图/段位图标）。
 * 之前每个 [androidx.compose.foundation.Image] 都在自己 LaunchedEffect 里开网络流重读 URL，
 * 在主页面 + 弹窗同时打开时会出现 5~20 张并发 IO，触发明显卡顿。
 *
 * 这里提供一个进程级 LRU，按 URL 缓存解码后的 [ImageBitmap]。
 * - 命中：直接返回 Bitmap，避免再次网络请求与 BitmapFactory.decode 分配。
 * - 未命中：调用方负责下载和解码（仍在 IO 线程），之后 put。
 *
 * 容量按 Bitmap 字节数估算（[LruCache.sizeOf]），默认上限 24MB，
 * 对 1080p 屏幕够放约 50~100 张 64x64~128x128 图。
 */
object ImageCache {
    private const val DEFAULT_MAX_BYTES: Int = 24 * 1024 * 1024

    private val cache: LruCache<String, ImageBitmap> = object : LruCache<String, ImageBitmap>(DEFAULT_MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val alloc = value.asAndroidBitmap().allocationByteCount
            // 至少按 1 字节估算，防止 allocationByteCount == 0 时整张图被当 0 大小反复插入
            return alloc.coerceAtLeast(1)
        }
    }

    fun get(url: String): ImageBitmap? = cache.get(url)

    fun put(url: String, bitmap: ImageBitmap) {
        cache.put(url, bitmap)
    }

    fun clear() = cache.evictAll()

    fun size(): Int = cache.size()

    /** 调试用：缓存占用字节数。 */
    fun usedBytes(): Int {
        var total = 0
        cache.snapshot().values.forEach { total += it.asAndroidBitmap().allocationByteCount }
        return total
    }
}
