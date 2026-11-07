package com.phimbud.mobile.core.platform

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

private fun resolveDesktopAppDataRoot(): File {
    val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
    return if (localAppData != null) {
        File(localAppData, "PhimBud")
    } else {
        File(System.getProperty("user.home", "."), ".phimbud")
    }
}

private fun replaceFileAtomically(tmpFile: File, targetFile: File) {
    runCatching {
        Files.move(
            tmpFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }.recoverCatching {
        Files.move(
            tmpFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }.onFailure {
        targetFile.writeBytes(tmpFile.readBytes())
        tmpFile.delete()
    }
}

actual class PlatformContext(
    val appDataRoot: File = resolveDesktopAppDataRoot()
)

actual class KeyValueStore actual constructor(
    context: PlatformContext,
    storeName: String
) {
    private val lock = Any()
    private val prefsDir = File(context.appDataRoot, "prefs")
    private val storeFile: File
    private val map = LinkedHashMap<String, String>()

    init {
        val safeStore = storeName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        storeFile = File(prefsDir, "$safeStore.properties")
        synchronized(lock) {
            if (storeFile.exists() && storeFile.length() > 0L) {
                runCatching {
                    val props = Properties()
                    InputStreamReader(FileInputStream(storeFile), Charsets.UTF_8).use { reader ->
                        props.load(reader)
                    }
                    for (name in props.stringPropertyNames()) {
                        props.getProperty(name)?.let { map[name] = it }
                    }
                }
            }
        }
    }

    private fun persistLocked() {
        runCatching {
            if (!prefsDir.exists()) {
                prefsDir.mkdirs()
            }
            val props = Properties()
            for ((k, v) in map) {
                props.setProperty(k, v)
            }
            val tmpFile = File(prefsDir, "${storeFile.name}.tmp")
            OutputStreamWriter(FileOutputStream(tmpFile), Charsets.UTF_8).use { writer ->
                props.store(writer, null)
            }
            replaceFileAtomically(tmpFile, storeFile)
        }
    }

    actual fun getString(key: String, defaultValue: String?): String? = synchronized(lock) {
        map[key] ?: defaultValue
    }

    actual fun putString(key: String, value: String) = synchronized(lock) {
        map[key] = value
        persistLocked()
    }

    actual fun remove(key: String) = synchronized(lock) {
        if (map.remove(key) != null) {
            persistLocked()
        }
    }

    actual fun getAllStrings(): Map<String, String> = synchronized(lock) {
        LinkedHashMap(map)
    }

    actual fun clear() = synchronized(lock) {
        if (map.isNotEmpty()) {
            map.clear()
            persistLocked()
        }
    }
}

actual class SubtitleCache actual constructor(context: PlatformContext) {
    private val dir = File(context.appDataRoot, "cache/subtitles")

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
        replaceFileAtomically(tmpFile, file)
        return CachedSubtitle(path = file.absolutePath, content = content)
    }
}

actual class CatalogIndexCache actual constructor(context: PlatformContext) {
    private val dir = File(context.appDataRoot, "cache/catalog_index")

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
            replaceFileAtomically(tmp, target)
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
    return File(context.appDataRoot, "cache/phimbud_image_cache").absolutePath.toPath()
}

actual fun configurePlatformImageLoader(
    builder: ImageLoader.Builder,
    context: PlatformContext
): ImageLoader.Builder = builder

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    System.err.println("[$tag] $message")
    throwable?.printStackTrace(System.err)
}

class DesktopBackDispatcher {
    internal class Entry(
        var enabled: Boolean,
        var onBack: () -> Unit
    )

    private val entries = ArrayList<Entry>()

    internal fun register(entry: Entry) {
        entries.add(entry)
    }

    internal fun unregister(entry: Entry) {
        entries.remove(entry)
    }

    fun dispatchBack(): Boolean {
        val active = entries.lastOrNull { it.enabled } ?: return false
        active.onBack()
        return true
    }
}

val LocalDesktopBackDispatcher = compositionLocalOf<DesktopBackDispatcher?> { null }

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalDesktopBackDispatcher.current ?: return
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember {
        DesktopBackDispatcher.Entry(enabled = enabled, onBack = { currentOnBack() })
    }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = { currentOnBack() }
    }
    DisposableEffect(dispatcher, entry) {
        dispatcher.register(entry)
        onDispose {
            dispatcher.unregister(entry)
        }
    }
}

@Composable
actual fun PlatformSystemBarsEffect() {
    // Desktop window frame is styled directly in Main.kt
}

@Composable
actual fun Modifier.platformHorizontalDragScroll(scrollableState: ScrollableState): Modifier {
    val scope = rememberCoroutineScope()
    val dragState = rememberDraggableState { delta ->
        scope.launch {
            scrollableState.scrollBy(-delta)
        }
    }
    return this.draggable(
        orientation = Orientation.Horizontal,
        state = dragState
    )
}
