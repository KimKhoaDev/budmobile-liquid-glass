package com.phimbud.mobile.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.LocalDesktopWindowController
import com.phimbud.mobile.core.crypto.SrtParser
import com.phimbud.mobile.core.crypto.SubtitleCue
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.platform.logError
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent

internal class DesktopPlayerController(
    private val proxyServer: DesktopHlsProxyServer,
    private val scope: CoroutineScope
) : PlatformPlayerController {

    override var isPlaying by mutableStateOf(false)
    override var isBuffering by mutableStateOf(false)
    override var isPlaybackEnded by mutableStateOf(false)
    override var playbackError by mutableStateOf<String?>(null)
    override var durationMs by mutableLongStateOf(0L)
    override val isInPipMode: Boolean = false

    var currentFrame by mutableStateOf<ImageBitmap?>(null)
        private set

    var livePositionMs by mutableLongStateOf(0L)
        private set

    private var currentLoadedFid: String? = null
    private var currentLoadedUrl: String? = null
    private var wantToPlay: Boolean = false
    private var loadJob: Job? = null

    private val ffmpegPlayer = DesktopFfmpegPlayer(
        onFrameRendered = { bitmap ->
            currentFrame = bitmap
        },
        onStateChanged = { playing, buffering ->
            isPlaying = playing
            isBuffering = buffering
        },
        onDurationKnown = { durMs ->
            if (durMs > 0L) {
                durationMs = durMs
            }
        },
        onPositionUpdated = { posMs ->
            livePositionMs = posMs.coerceAtLeast(0L)
        },
        onPlaybackEnded = {
            isPlaying = false
            isBuffering = false
            wantToPlay = false
            isPlaybackEnded = true
        },
        onPlaybackError = { errMsg ->
            isPlaying = false
            isBuffering = false
            playbackError = errMsg
        }
    )

    override fun currentPositionMs(): Long = livePositionMs

    override fun play() {
        if (isPlaybackEnded) {
            isPlaybackEnded = false
            seekTo(0L)
        }
        wantToPlay = true
        ffmpegPlayer.play()
    }

    override fun pause() {
        wantToPlay = false
        ffmpegPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceAtLeast(0L)
        if (durationMs <= 0L || targetMs < durationMs - 2_000L) {
            isPlaybackEnded = false
        }
        livePositionMs = targetMs
        ffmpegPlayer.seekTo(targetMs)
    }

    override fun clearErrorAndRetry() {
        val fid = currentLoadedFid ?: return
        val url = currentLoadedUrl ?: return
        val pos = currentPositionMs()
        isPlaybackEnded = false
        currentLoadedUrl = null
        loadOrUpdateMedia(
            fid = fid,
            m3u8Url = url,
            knownDurationMs = durationMs,
            resumePositionMs = pos,
            subtitle = null,
            subtitleLanguage = "vi"
        )
    }

    override fun loadOrUpdateMedia(
        fid: String,
        m3u8Url: String,
        knownDurationMs: Long,
        resumePositionMs: Long,
        subtitle: CachedSubtitle?,
        subtitleLanguage: String
    ) {
        // Subtitles are rendered directly by Compose overlay in 0ms without touching the video stream!
        val isNewEpisode = (currentLoadedFid != fid)
        val isNewUrl = (currentLoadedUrl != m3u8Url)
        if (!isNewEpisode && !isNewUrl) {
            return
        }

        val targetResumeMs = if (isNewEpisode) resumePositionMs else currentPositionMs()
        val shouldAutoPlay = if (isNewEpisode) true else wantToPlay

        currentLoadedFid = fid
        currentLoadedUrl = m3u8Url
        playbackError = null
        isBuffering = true
        if (isNewEpisode) {
            isPlaybackEnded = false
            currentFrame = null
            durationMs = knownDurationMs
            livePositionMs = targetResumeMs.coerceAtLeast(0L)
        }
        wantToPlay = shouldAutoPlay

        loadJob?.cancel()
        loadJob = scope.launch {
            runCatching {
                val proxyBase = proxyServer.ensureStarted()
                val localPlaylistUrl = HlsManifestRewriter.buildLocalPlaylistUrl(proxyBase, m3u8Url)
                ffmpegPlayer.openStream(
                    localPlaylistUrl = localPlaylistUrl,
                    initialResumeMs = targetResumeMs,
                    autoPlay = shouldAutoPlay
                )
            }.onFailure { err ->
                logError("DesktopPlayerController", "Failed to start HLS stream", err)
                isBuffering = false
                playbackError = "Không thể khởi tạo trình phát Windows (${err.message ?: "lỗi mạng"})."
            }
        }
    }

    fun release() {
        loadJob?.cancel()
        ffmpegPlayer.release()
        proxyServer.stop()
    }
}

@Composable
actual fun rememberPlatformPlayerController(
    repository: PhimBudRepository
): PlatformPlayerController {
    val scope = rememberCoroutineScope()
    val controller = remember(repository) {
        val proxy = DesktopHlsProxyServer(repository.apiClient.httpClient)
        DesktopPlayerController(
            proxyServer = proxy,
            scope = scope
        )
    }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    return controller
}

@Composable
actual fun PlatformPlayerWindowEffect(
    showSubtitleDialog: Boolean,
    showServerDialog: Boolean,
    controlsVisible: Boolean
) {
    val winController = LocalDesktopWindowController.current ?: return
    var mouseMoveTick by remember { mutableIntStateOf(0) }

    // Ensure Fullscreen on enter & clean restore on exit, plus wake mouse cursor on mouse motion
    DisposableEffect(winController) {
        winController.setPlayerFullscreen(true)
        val awtListener = AWTEventListener { event ->
            if (event is MouseEvent && (event.id == MouseEvent.MOUSE_MOVED || event.id == MouseEvent.MOUSE_DRAGGED)) {
                winController.setCursorHidden(false)
                mouseMoveTick++
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(awtListener, AWTEvent.MOUSE_MOTION_EVENT_MASK)

        onDispose {
            Toolkit.getDefaultToolkit().removeAWTEventListener(awtListener)
            winController.setCursorHidden(false)
            winController.setPlayerFullscreen(false)
        }
    }

    // Auto-hide cursor in Fullscreen when HUD controls are hidden and mouse is idle for 2.5s
    LaunchedEffect(controlsVisible, showSubtitleDialog, showServerDialog, mouseMoveTick) {
        if (controlsVisible || showSubtitleDialog || showServerDialog) {
            winController.setCursorHidden(false)
        } else {
            delay(2500L)
            winController.setCursorHidden(true)
        }
    }
}

@Composable
actual fun PlatformVideoSurface(
    controller: PlatformPlayerController,
    resizeMode: VideoResizeMode,
    activeSubtitle: CachedSubtitle?,
    controlsVisible: Boolean,
    modifier: Modifier
) {
    val desktopController = controller as DesktopPlayerController
    val winController = LocalDesktopWindowController.current
    val focusRequester = remember { FocusRequester() }

    val scale = when (resizeMode) {
        VideoResizeMode.FIT -> ContentScale.Fit
        VideoResizeMode.ZOOM -> ContentScale.Crop
        VideoResizeMode.FILL -> ContentScale.FillBounds
    }

    val parsedCues by produceState(
        initialValue = emptyList<SubtitleCue>(),
        key1 = activeSubtitle?.path,
        key2 = activeSubtitle?.content
    ) {
        val raw = activeSubtitle?.content
        value = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                SrtParser.parse(raw)
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.Spacebar -> {
                        if (desktopController.isPlaying) desktopController.pause() else desktopController.play()
                        true
                    }
                    Key.DirectionLeft -> {
                        desktopController.seekTo((desktopController.currentPositionMs() - 10_000L).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight -> {
                        val maxDur = desktopController.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                        desktopController.seekTo((desktopController.currentPositionMs() + 10_000L).coerceAtMost(maxDur))
                        true
                    }
                    Key.F11 -> {
                        winController?.toggleFullscreen()
                        true
                    }
                    else -> false
                }
            }
    ) {
        val frame = desktopController.currentFrame
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = "Video",
                contentScale = scale,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (parsedCues.isNotEmpty()) {
            DesktopSubtitleOverlay(
                desktopController = desktopController,
                parsedCues = parsedCues,
                controlsVisible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private val DesktopSubtitleTextStyle = TextStyle(
    shadow = Shadow(
        color = Color.Black,
        offset = Offset(1.5f, 1.5f),
        blurRadius = 4f
    )
)

@Composable
private fun DesktopSubtitleOverlay(
    desktopController: DesktopPlayerController,
    parsedCues: List<SubtitleCue>,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val activeCueText by remember(desktopController, parsedCues) {
        derivedStateOf {
            SrtParser.findActiveText(parsedCues, desktopController.livePositionMs)
        }
    }

    if (!activeCueText.isNullOrBlank()) {
        val bottomPad = if (controlsVisible) 88.dp else 30.dp
        Box(
            modifier = modifier
                .padding(start = 48.dp, end = 48.dp, bottom = bottomPad)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = activeCueText.orEmpty(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 27.sp,
                style = DesktopSubtitleTextStyle
            )
        }
    }
}
