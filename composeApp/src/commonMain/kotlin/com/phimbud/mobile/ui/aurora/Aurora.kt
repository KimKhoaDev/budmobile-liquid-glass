package com.phimbud.mobile.ui.aurora

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.phimbud.mobile.core.platform.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Three glow colours that tint the app's ambient background. */
@Immutable
data class AuroraPalette(val primary: Color, val secondary: Color, val tertiary: Color)

/** The classic palette derived from a single accent colour. */
fun accentPalette(accent: Color) = AuroraPalette(
    primary = accent,
    secondary = lerp(accent, Color(0xFF3A2BD9), 0.55f),
    tertiary = lerp(accent, Color(0xFFFF8A00), 0.45f)
)

/** Deterministic, pleasant palette for posters we cannot sample (non-Android or failed loads). */
fun paletteFromSeed(seed: String): AuroraPalette {
    var h = 7
    for (c in seed) h = 31 * h + c.code
    val hue = (((h % 360) + 360) % 360).toFloat()
    return AuroraPalette(
        primary = Color.hsv(hue, 0.78f, 0.95f),
        secondary = Color.hsv((hue + 42f) % 360f, 0.72f, 0.92f),
        tertiary = Color.hsv((hue + 318f) % 360f, 0.72f, 0.92f)
    )
}

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

/**
 * Picks the three most vivid, distinct hues from ARGB [pixels] (a tiny downscaled poster).
 * Greys and near-blacks are ignored; returns null when the image has no real colour.
 */
fun paletteFromPixels(pixels: IntArray): AuroraPalette? {
    val bins = 12
    val weight = FloatArray(bins)
    val sat = FloatArray(bins)
    val value = FloatArray(bins)
    val sinH = FloatArray(bins)
    val cosH = FloatArray(bins)

    for (p in pixels) {
        if (((p ushr 24) and 0xFF) < 128) continue
        val r = ((p shr 16) and 0xFF) / 255f
        val g = ((p shr 8) and 0xFF) / 255f
        val b = (p and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val s = if (max == 0f) 0f else d / max
        if (max < 0.22f || s < 0.28f) continue
        var hue = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (hue < 0f) hue += 360f
        val bin = (hue / 30f).toInt().coerceIn(0, bins - 1)
        val w = s * max
        weight[bin] += w
        sat[bin] += s * w
        value[bin] += max * w
        val rad = hue * PI.toFloat() / 180f
        sinH[bin] += sin(rad) * w
        cosH[bin] += cos(rad) * w
    }

    val total = weight.sum()
    if (total < pixels.size * 0.02f) return null

    fun hueOf(bin: Int): Float {
        var deg = atan2(sinH[bin], cosH[bin]) * 180f / PI.toFloat()
        if (deg < 0f) deg += 360f
        return deg
    }

    fun colorOf(bin: Int): Color {
        val w = weight[bin].coerceAtLeast(0.0001f)
        return Color.hsv(
            hue = hueOf(bin),
            saturation = (sat[bin] / w * 1.1f).coerceIn(0.55f, 1f),
            value = (value[bin] / w * 1.15f).coerceIn(0.75f, 1f)
        )
    }

    val order = (0 until bins).sortedByDescending { weight[it] }
    val first = order.first()
    val firstHue = hueOf(first)
    val second = order.firstOrNull {
        it != first && weight[it] >= weight[first] * 0.18f && hueDistance(hueOf(it), firstHue) >= 40f
    }
    val third = order.firstOrNull {
        it != first && it != second && weight[it] >= weight[first] * 0.10f &&
            hueDistance(hueOf(it), firstHue) >= 40f &&
            (second == null || hueDistance(hueOf(it), hueOf(second)) >= 40f)
    }

    val primary = colorOf(first)
    return AuroraPalette(
        primary = primary,
        secondary = second?.let { colorOf(it) } ?: Color.hsv((firstHue + 45f) % 360f, 0.7f, 0.95f),
        tertiary = third?.let { colorOf(it) } ?: Color.hsv((firstHue + 315f) % 360f, 0.7f, 0.95f)
    )
}

/** Loads [url] (tiny) and returns its dominant colours, or null if unavailable. */
expect suspend fun extractPosterPalette(platformContext: PlatformContext, url: String): AuroraPalette?

/**
 * Holds the palette currently driving the ambient background. Screens call [setPoster]; results are
 * cached so revisiting a title is instant, and the background cross-fades between palettes.
 */
class AuroraController(
    private val platformContext: PlatformContext,
    private val scope: CoroutineScope
) {
    var palette: AuroraPalette? by mutableStateOf(null)
        private set

    private val cache = HashMap<String, AuroraPalette>()
    private var currentUrl: String? = null
    private var job: Job? = null

    fun setPoster(url: String?) {
        if (url == currentUrl) return
        currentUrl = url
        job?.cancel()
        if (url.isNullOrBlank()) {
            palette = null
            return
        }
        cache[url]?.let {
            palette = it
            return
        }
        job = scope.launch {
            val result = extractPosterPalette(platformContext, url) ?: paletteFromSeed(url)
            if (cache.size > 64) cache.clear()
            cache[url] = result
            if (currentUrl == url) palette = result
        }
    }

    fun clear() = setPoster(null)
}

val LocalAuroraController = staticCompositionLocalOf<AuroraController?> { null }

/** Drives the app-wide Aurora from [url] for as long as this composable is in the composition. */
@Composable
fun AuroraFromPoster(url: String?) {
    val controller = LocalAuroraController.current
    DisposableEffect(controller, url) {
        controller?.setPoster(url)
        onDispose { controller?.clear() }
    }
}
