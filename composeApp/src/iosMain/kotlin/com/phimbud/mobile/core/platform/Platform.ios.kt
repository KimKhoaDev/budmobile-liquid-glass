@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.phimbud.mobile.core.platform

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

actual class PlatformContext()

private fun resolveIosCachesRoot(): Path {
    return (NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String)?.toPath() ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY
}

actual class KeyValueStore actual constructor(
    context: PlatformContext,
    storeName: String
) {
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
    private val prefix = "${storeName}__"

    actual fun getString(key: String, defaultValue: String?): String? {
        return defaults.stringForKey(prefix + key) ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = prefix + key)
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(prefix + key)
    }

    actual fun getAllStrings(): Map<String, String> {
        val dict = defaults.dictionaryRepresentation()
        val out = LinkedHashMap<String, String>()
        for ((rawKey, rawVal) in dict) {
            val k = rawKey as? String ?: continue
            if (k.startsWith(prefix) && rawVal is String) {
                out[k.removePrefix(prefix)] = rawVal
            }
        }
        return out
    }

    actual fun clear() {
        val dict = defaults.dictionaryRepresentation()
        for (rawKey in dict.keys) {
            val k = rawKey as? String ?: continue
            if (k.startsWith(prefix)) {
                defaults.removeObjectForKey(k)
            }
        }
    }
}

actual class SubtitleCache actual constructor(context: PlatformContext) {
    private val fs = FileSystem.SYSTEM
    private val dir: Path by lazy { resolveIosCachesRoot() / "subtitles" }

    actual fun readIfCached(cacheFileName: String): CachedSubtitle? {
        val file = dir / cacheFileName
        if (!fs.exists(file)) return null
        val content = runCatching {
            fs.read(file) { readUtf8() }
        }.getOrNull()
        return if (!content.isNullOrBlank()) {
            CachedSubtitle(path = file.toString(), content = content)
        } else {
            null
        }
    }

    actual fun write(cacheFileName: String, content: String): CachedSubtitle {
        runCatching { fs.createDirectories(dir) }
        val file = dir / cacheFileName
        val tmpFile = dir / "$cacheFileName.tmp"
        fs.write(tmpFile) {
            writeUtf8(content)
        }
        runCatching {
            fs.atomicMove(tmpFile, file)
        }.onFailure {
            fs.write(file) { writeUtf8(content) }
            runCatching { fs.delete(tmpFile) }
        }
        return CachedSubtitle(path = file.toString(), content = content)
    }
}

actual class CatalogIndexCache actual constructor(context: PlatformContext) {
    private val fs = FileSystem.SYSTEM
    private val dir: Path by lazy { resolveIosCachesRoot() / "catalog_index" }

    private fun sanitizeFileName(timeKey: String): String {
        return timeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    actual fun readExact(timeKey: String): String? {
        val file = dir / sanitizeFileName(timeKey)
        if (!fs.exists(file)) return null
        return runCatching { fs.read(file) { readUtf8() } }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    actual fun readLatestFallback(): String? {
        if (!fs.exists(dir)) return null
        val candidates = runCatching { fs.list(dir) }.getOrNull()
            ?.filter { !it.name.endsWith(".tmp") }
            ?: return null
        val latest = candidates.maxByOrNull { path ->
            runCatching { fs.metadataOrNull(path)?.lastModifiedAtMillis ?: 0L }.getOrDefault(0L)
        } ?: return null
        return runCatching { fs.read(latest) { readUtf8() } }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    actual fun write(timeKey: String, content: String) {
        runCatching {
            fs.createDirectories(dir)
            val safeName = sanitizeFileName(timeKey)
            val target = dir / safeName
            val tmp = dir / "$safeName.tmp"
            fs.write(tmp) { writeUtf8(content) }
            runCatching {
                fs.atomicMove(tmp, target)
            }.onFailure {
                fs.write(target) { writeUtf8(content) }
                runCatching { fs.delete(tmp) }
            }
            fs.list(dir).forEach { existing ->
                if (existing.name != safeName) {
                    runCatching { fs.delete(existing) }
                }
            }
        }
    }

    actual fun clear() {
        runCatching {
            if (fs.exists(dir)) {
                fs.list(dir).forEach { runCatching { fs.delete(it) } }
            }
        }
    }
}

actual fun platformImageCacheDirectory(context: PlatformContext): Path {
    return resolveIosCachesRoot() / "phimbud_image_cache"
}

actual fun configurePlatformImageLoader(
    builder: ImageLoader.Builder,
    context: PlatformContext
): ImageLoader.Builder = builder

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    val errDetail = throwable?.let { " | ${it::class.simpleName}: ${it.message}" }.orEmpty()
    NSLog("[$tag] $message$errDetail")
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses edge-swipe / UI navigation buttons instead of hardware back button on root tabs
}

@Composable
actual fun PlatformSystemBarsEffect() {
    LaunchedEffect(Unit) {
        UIApplication.sharedApplication.setStatusBarStyle(UIStatusBarStyleLightContent, animated = true)
    }
}

@Composable
actual fun Modifier.platformHorizontalDragScroll(scrollableState: ScrollableState): Modifier = this
