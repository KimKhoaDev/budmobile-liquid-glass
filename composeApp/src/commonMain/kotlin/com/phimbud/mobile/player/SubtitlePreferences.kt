package com.phimbud.mobile.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.phimbud.mobile.core.platform.KeyValueStore
import com.phimbud.mobile.core.platform.PlatformContext
import kotlin.math.roundToInt

enum class SubtitleBgStyle(val label: String) {
    SMART_ADAPTIVE("Thông minh"),
    NETFLIX_OUTLINE("Viền nét"),
    DARK_BOX("Hộp đậm")
}

enum class SubtitleColorPreset(val label: String, val color: Color) {
    WHITE("Trắng", Color.White),
    WARM_YELLOW("Vàng dịu", Color(0xFFFFF3B0))
}

@Immutable
data class SubtitlePreferences(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val bottomMarginPercent: Float = DEFAULT_BOTTOM_MARGIN_PERCENT,
    val bgStyle: SubtitleBgStyle = SubtitleBgStyle.SMART_ADAPTIVE,
    val colorPreset: SubtitleColorPreset = SubtitleColorPreset.WHITE
) {
    val fontScalePercent: Int
        get() = (fontScale * 100f).roundToInt()

    fun normalized(): SubtitlePreferences = copy(
        fontScale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
        bottomMarginPercent = bottomMarginPercent.coerceIn(MIN_BOTTOM_MARGIN_PERCENT, MAX_BOTTOM_MARGIN_PERCENT)
    )

    companion object {
        const val DEFAULT_FONT_SCALE = 1.0f
        const val MIN_FONT_SCALE = 0.70f
        const val MAX_FONT_SCALE = 1.65f
        const val FONT_SCALE_STEP = 0.10f

        const val DEFAULT_BOTTOM_MARGIN_PERCENT = 11.0f
        const val HARDSUB_AVOID_MARGIN_PERCENT = 18.0f
        const val MIN_BOTTOM_MARGIN_PERCENT = 3.0f
        const val MAX_BOTTOM_MARGIN_PERCENT = 38.0f
    }
}

@Immutable
data class SmartSubtitleLayoutMetrics(
    val cinemaRefHeightDp: Float,
    val activeVideoWidthDp: Float,
    val fontSizeSp: Float,
    val lineHeightSp: Float,
    val bottomPaddingDp: Float,
    val topPaddingDp: Float,
    val maxBoxWidthDp: Float
)

object SmartSubtitleCalculator {

    /**
     * Computes viewport-aware, Windows-DPI-invariant subtitle typography & safe-zone margins
     * anchored to the active video frame so subtitles stay comfortable across 16:9, 21:9, 16:10,
     * Cinemascope (2.39:1), and floating portrait windows.
     */
    fun computeMetrics(
        containerWidthDp: Float,
        containerHeightDp: Float,
        videoWidthPx: Int,
        videoHeightPx: Int,
        resizeMode: VideoResizeMode,
        prefs: SubtitlePreferences,
        controlsVisible: Boolean,
        controlsBottomSafeDp: Float = 112f
    ): SmartSubtitleLayoutMetrics {
        val safeW = containerWidthDp.coerceAtLeast(1f)
        val safeH = containerHeightDp.coerceAtLeast(1f)

        val (renderedW, renderedH) = if (videoWidthPx > 0 && videoHeightPx > 0 && resizeMode == VideoResizeMode.FIT) {
            val videoAspect = videoWidthPx.toFloat() / videoHeightPx.toFloat()
            val containerAspect = safeW / safeH
            if (videoAspect >= containerAspect) {
                safeW to (safeW / videoAspect)
            } else {
                (safeH * videoAspect) to safeH
            }
        } else {
            safeW to safeH
        }

        // Normalize reference height against 16:9 width so cropped 2.39:1 movies don't shrink font size
        // compared to 16:9 streams on the same monitor, while portrait windows hug the actual video height.
        val widescreenEquivalentH = renderedW * (9f / 16f)
        val cinemaRefH = minOf(safeH, maxOf(renderedH, widescreenEquivalentH)).coerceAtLeast(120f)

        val clampedPrefs = prefs.normalized()
        val rawFontSp = cinemaRefH * 0.037f * clampedPrefs.fontScale
        val fontSizeSp = rawFontSp.coerceIn(15f, 68f)
        val lineHeightSp = fontSizeSp * 1.34f

        val letterboxEachSideDp = ((safeH - cinemaRefH) * 0.5f).coerceAtLeast(0f)
        val desiredBottomDp = letterboxEachSideDp + cinemaRefH * (clampedPrefs.bottomMarginPercent / 100f)
        val minControlsLiftDp = if (controlsVisible) controlsBottomSafeDp else 0f
        val maxAllowedBottomDp = (safeH - fontSizeSp * 2.6f).coerceAtLeast(24f)
        val bottomPaddingDp = maxOf(desiredBottomDp, minControlsLiftDp).coerceIn(16f, maxAllowedBottomDp)

        val desiredTopDp = letterboxEachSideDp + cinemaRefH * 0.08f
        val minTopControlsLiftDp = if (controlsVisible) 76f else 0f
        val topPaddingDp = maxOf(desiredTopDp, minTopControlsLiftDp).coerceIn(16f, maxAllowedBottomDp)

        val maxBoxWidthDp = (renderedW * 0.76f).coerceIn(220f, safeW - 32f)

        return SmartSubtitleLayoutMetrics(
            cinemaRefHeightDp = cinemaRefH,
            activeVideoWidthDp = renderedW,
            fontSizeSp = fontSizeSp,
            lineHeightSp = lineHeightSp,
            bottomPaddingDp = bottomPaddingDp,
            topPaddingDp = topPaddingDp,
            maxBoxWidthDp = maxBoxWidthDp
        )
    }

    /**
     * Computes backdrop opacity based on style preference and live bottom-band video luminance (0f..1f).
     */
    fun computeBackdropAlpha(style: SubtitleBgStyle, bottomLuminance: Float): Float {
        val lum = bottomLuminance.coerceIn(0f, 1f)
        return when (style) {
            SubtitleBgStyle.NETFLIX_OUTLINE -> 0f
            SubtitleBgStyle.DARK_BOX -> 0.78f
            SubtitleBgStyle.SMART_ADAPTIVE -> {
                // Subtle on dark cinema scenes (~0.24), firmer on bright/glaring backgrounds (up to ~0.68)
                (0.22f + lum * 0.48f).coerceIn(0.22f, 0.68f)
            }
        }
    }
}

class SubtitlePreferencesStore(platformContext: PlatformContext) {
    private val store = KeyValueStore(platformContext, STORE_NAME)

    fun load(): SubtitlePreferences {
        val scale = store.getString(KEY_FONT_SCALE)?.toFloatOrNull()
            ?: SubtitlePreferences.DEFAULT_FONT_SCALE
        val margin = store.getString(KEY_BOTTOM_MARGIN)?.toFloatOrNull()
            ?: SubtitlePreferences.DEFAULT_BOTTOM_MARGIN_PERCENT
        val styleName = store.getString(KEY_BG_STYLE)
        val style = SubtitleBgStyle.entries.firstOrNull { it.name == styleName }
            ?: SubtitleBgStyle.SMART_ADAPTIVE
        val colorName = store.getString(KEY_COLOR_PRESET)
        val color = SubtitleColorPreset.entries.firstOrNull { it.name == colorName }
            ?: SubtitleColorPreset.WHITE

        return SubtitlePreferences(
            fontScale = scale,
            bottomMarginPercent = margin,
            bgStyle = style,
            colorPreset = color
        ).normalized()
    }

    fun save(prefs: SubtitlePreferences) {
        val norm = prefs.normalized()
        store.putString(KEY_FONT_SCALE, norm.fontScale.toString())
        store.putString(KEY_BOTTOM_MARGIN, norm.bottomMarginPercent.toString())
        store.putString(KEY_BG_STYLE, norm.bgStyle.name)
        store.putString(KEY_COLOR_PRESET, norm.colorPreset.name)
    }

    companion object {
        private const val STORE_NAME = "phimbud_subtitle_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_BOTTOM_MARGIN = "bottom_margin_pct"
        private const val KEY_BG_STYLE = "bg_style"
        private const val KEY_COLOR_PRESET = "color_preset"
    }
}
