package com.phimbud.mobile.core.platform

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil3.ImageLoader
import coil3.request.allowRgb565
import com.phimbud.mobile.ui.theme.NetflixBlack
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

actual class PlatformContext(val androidContext: Context)

actual class KeyValueStore actual constructor(
    context: PlatformContext,
    storeName: String
) {
    private val prefs: SharedPreferences =
        context.androidContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)

    actual fun getString(key: String, defaultValue: String?): String? {
        return prefs.getString(key, defaultValue)
    }

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    actual fun getAllStrings(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in prefs.all) {
            if (v is String) {
                out[k] = v
            }
        }
        return out
    }

    actual fun clear() {
        prefs.edit().clear().apply()
    }
}

actual class SubtitleCache actual constructor(context: PlatformContext) {
    private val dir = File(context.androidContext.cacheDir, "subtitles")

    actual fun readIfCached(cacheFileName: String): CachedSubtitle? {
        val file = File(dir, cacheFileName)
        if (file.exists() && file.length() > 0L) {
            val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            if (!content.isNullOrBlank()) {
                return CachedSubtitle(path = file.absolutePath, content = content)
            }
        }
        return null
    }

    actual fun write(cacheFileName: String, content: String): CachedSubtitle {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, cacheFileName)
        val tmpFile = File(dir, "$cacheFileName.tmp")
        tmpFile.writeText(content, Charsets.UTF_8)
        if (!tmpFile.renameTo(file)) {
            file.writeText(content, Charsets.UTF_8)
            tmpFile.delete()
        }
        return CachedSubtitle(path = file.absolutePath, content = content)
    }
}

actual class CatalogIndexCache actual constructor(context: PlatformContext) {
    private val dir = File(context.androidContext.cacheDir, "catalog_index")

    private fun sanitizeFileName(timeKey: String): String {
        return timeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    actual fun readExact(timeKey: String): String? {
        val file = File(dir, sanitizeFileName(timeKey))
        if (!file.exists() || file.length() <= 2L) return null
        return runCatching { file.readText(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    actual fun readLatestFallback(): String? {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") && it.length() > 2L }
            ?: return null
        val newest = files.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { newest.readText(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    actual fun write(timeKey: String, content: String) {
        runCatching {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val safeName = sanitizeFileName(timeKey)
            val target = File(dir, safeName)
            val tmp = File(dir, "$safeName.tmp")
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(content, Charsets.UTF_8)
                tmp.delete()
            }
            dir.listFiles()?.forEach { existing ->
                if (existing.name != safeName) {
                    existing.delete()
                }
            }
        }
    }

    actual fun clear() {
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}

actual fun platformImageCacheDirectory(context: PlatformContext): Path {
    return File(context.androidContext.cacheDir, "phimbud_image_cache").absolutePath.toPath()
}

actual fun configurePlatformImageLoader(
    builder: ImageLoader.Builder,
    context: PlatformContext
): ImageLoader.Builder {
    return builder.allowRgb565(true)
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    Log.e(tag, message, throwable)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun PlatformSystemBarsEffect() {
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(view) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            val blackArgb = NetflixBlack.toArgb()
            window.statusBarColor = blackArgb
            window.navigationBarColor = blackArgb
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }
}
