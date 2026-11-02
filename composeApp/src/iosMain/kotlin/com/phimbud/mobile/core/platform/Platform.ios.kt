@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.phimbud.mobile.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import okio.FileSystem
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
    private val dir = run {
        val cachesPath = (NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String)?.toPath() ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        (cachesPath / "subtitles").also { path ->
            runCatching { fs.createDirectories(path) }
        }
    }

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
    SideEffect {
        UIApplication.sharedApplication.setStatusBarStyle(UIStatusBarStyleLightContent, animated = true)
    }
}
