package com.local.dfcraftmonitor.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.local.dfcraftmonitor.ui.theme.SemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * 远程图片加载。
 *
 * 优化点（解决卡顿）：
 *  1. **LRU 缓存**（[ImageCache]）：URL 命中直接复用 [ImageBitmap]，避免重复网络与解码。
 *  2. **单次并发**：每个 RemoteImage 在自己的 LaunchedEffect 里下载，但配合缓存，热路径上几乎无 IO。
 *  3. **inSampleSize 降采样**：按目标尺寸解码，避免把几 MB 的原图塞进 64dp 的圆框里。
 *  4. **失败占位**：永远不抛异常，不显示破裂图标。
 *  5. **稳定参数**：[ImageBitmap] 本身是 [androidx.compose.runtime.Stable]，
 *     只要 [url] / [contentDescription] / 关键修饰符稳定，组件可被 Compose 跳过。
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

    // 命中缓存时立即取出来，避免占位闪烁
    LaunchedEffect(normalizedUrl) {
        if (normalizedUrl == null) {
            bitmap = null
            failed = false
            return@LaunchedEffect
        }
        val cached = ImageCache.get(normalizedUrl)
        if (cached != null) {
            bitmap = cached
            failed = false
            return@LaunchedEffect
        }
        bitmap = null
        failed = false
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(normalizedUrl).openStream().use { it.readBytes() }
                // 第一次 decodeBounds 拿尺寸，按目标大小计算 inSampleSize
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                val sample = computeInSampleSize(opts.outWidth, opts.outHeight, targetMaxSizePx)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    ?.asImageBitmap()
            }.getOrNull()
        }
        if (loaded != null) {
            ImageCache.put(normalizedUrl, loaded)
        }
        bitmap = loaded
        failed = loaded == null
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
        } else {
            // showPlaceholder=false：什么都不画（给头像框等场景用）
            @Suppress("UNUSED_EXPRESSION")
            Color.Transparent
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

/** 计算 inSampleSize：把原图缩到 ≤ maxTarget 像素的正方形。 */
private fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxTarget: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    val longest = maxOf(srcWidth, srcHeight)
    if (longest <= maxTarget) return 1
    var sample = 1
    while (longest / (sample * 2) >= maxTarget) {
        sample *= 2
    }
    return sample
}
