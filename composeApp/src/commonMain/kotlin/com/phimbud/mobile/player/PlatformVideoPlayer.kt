package com.phimbud.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.repository.PhimBudRepository

enum class VideoResizeMode {
    FIT,
    ZOOM,
    FILL
}

@Stable
interface PlatformPlayerController {
    val isPlaying: Boolean
    val isBuffering: Boolean
    val playbackError: String?
    val durationMs: Long
    val isInPipMode: Boolean

    fun currentPositionMs(): Long
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun clearErrorAndRetry()

    fun loadOrUpdateMedia(
        fid: String,
        m3u8Url: String,
        knownDurationMs: Long,
        resumePositionMs: Long,
        subtitle: CachedSubtitle?,
        subtitleLanguage: String
    )
}

@Composable
expect fun rememberPlatformPlayerController(
    repository: PhimBudRepository
): PlatformPlayerController

@Composable
expect fun PlatformPlayerWindowEffect(
    showSubtitleDialog: Boolean,
    showServerDialog: Boolean,
    controlsVisible: Boolean
)

@Composable
expect fun PlatformVideoSurface(
    controller: PlatformPlayerController,
    resizeMode: VideoResizeMode,
    activeSubtitle: CachedSubtitle?,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier
)
