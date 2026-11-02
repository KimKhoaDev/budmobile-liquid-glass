package com.phimbud.mobile.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlin.math.roundToLong

expect class PlatformContext

expect class KeyValueStore(context: PlatformContext, storeName: String) {
    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun getAllStrings(): Map<String, String>
    fun clear()
}

@Immutable
data class CachedSubtitle(
    val path: String,
    val content: String
)

expect class SubtitleCache(context: PlatformContext) {
    fun readIfCached(cacheFileName: String): CachedSubtitle?
    fun write(cacheFileName: String, content: String): CachedSubtitle
}

expect fun currentTimeMillis(): Long

expect fun logError(tag: String, message: String, throwable: Throwable? = null)

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun PlatformSystemBarsEffect()

/**
 * Formats an epoch timestamp (ms) into PhimBud's UTC search index filename key: "yyyy-MM-dd-H.js"
 * using Howard Hinnant's civil-from-days algorithm (exact across all leap years, zero locale bugs).
 */
fun utcDateHourKeyFromEpochMillis(epochMillis: Long = currentTimeMillis()): String {
    val totalSeconds = epochMillis / 1000L
    val secondsInDay = 86_400L
    var days = totalSeconds / secondsInDay
    var remSeconds = totalSeconds % secondsInDay
    if (remSeconds < 0) {
        remSeconds += secondsInDay
        days -= 1
    }
    val hour = (remSeconds / 3600L).toInt()

    val z = days + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    var y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
    val m = (mp + (if (mp < 10L) 3L else -9L)).toInt()
    if (m <= 2) {
        y += 1L
    }

    val mm = m.toString().padStart(2, '0')
    val dd = d.toString().padStart(2, '0')
    return "$y-$mm-$dd-$hour.js"
}

fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val absTenths = kotlin.math.abs(tenths)
    return "$sign${absTenths / 10}.${absTenths % 10}"
}

fun formatPlaybackTime(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val totalSeconds = safeMillis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:$mm:$ss"
    } else {
        "$mm:$ss"
    }
}
