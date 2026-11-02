package com.phimbud.mobile.core.platform

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.phimbud.mobile.ui.theme.NetflixBlack
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
    private val dir = File(context.androidContext.cacheDir, "subtitles").apply { mkdirs() }

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
        dir.mkdirs()
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
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = NetflixBlack.toArgb()
            window.navigationBarColor = NetflixBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
}
