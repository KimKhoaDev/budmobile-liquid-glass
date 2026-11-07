package com.phimbud.mobile.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.LocalDesktopWindowController
import com.phimbud.mobile.core.crypto.SrtParser
import com.phimbud.mobile.core.crypto.SubtitleCue
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.platform.DesktopKeyDispatcher
import com.phimbud.mobile.core.platform.DesktopPlayerKey
import com.phimbud.mobile.core.platform.LocalDesktopKeyDispatcher
import com.phimbud.mobile.core.platform.logError
import com.phimbud.mobile.core.repository.PhimBudRepository
import com.phimbud.mobile.ui.theme.NetflixRed
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
import kotlin.math.abs
import kotlin.math.roundToInt

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

    var bottomRegionLuminance by mutableFloatStateOf(0.25f)
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
        },
        onBottomLuminanceChanged = { lum ->
            bottomRegionLuminance = lum
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
            bottomRegionLuminance = 0.25f
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
    controlsVisible: Boolean,
    onUserMouseActivity: () -> Unit
) {
    val winController = LocalDesktopWindowController.current ?: return
    val currentOnMouseActivity by rememberUpdatedState(onUserMouseActivity)
    var mouseMoveTick by remember { mutableIntStateOf(0) }

    // Ensure Fullscreen on enter & clean restore on exit, plus wake mouse cursor and HUD on mouse motion
    DisposableEffect(winController) {
        winController.setPlayerFullscreen(true)
        var lastWakeMs = 0L
        var lastX = -1
        var lastY = -1

        val awtListener = AWTEventListener { event ->
            if (event is MouseEvent && (event.id == MouseEvent.MOUSE_MOVED || event.id == MouseEvent.MOUSE_DRAGGED)) {
                winController.setCursorHidden(false)
                val movedDist = if (lastX < 0) 99 else abs(event.xOnScreen - lastX) + abs(event.yOnScreen - lastY)
                lastX = event.xOnScreen
                lastY = event.yOnScreen
                if (movedDist >= 5) {
                    val now = System.currentTimeMillis()
                    if (now - lastWakeMs >= 220L) {
                        lastWakeMs = now
                        mouseMoveTick++
                        currentOnMouseActivity()
                    }
                }
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
actual fun PlatformPlayerShortcutEffect(
    enabled: Boolean,
    onAction: (PlayerShortcutAction) -> Unit
) {
    val dispatcher = LocalDesktopKeyDispatcher.current ?: return
    val winController = LocalDesktopWindowController.current
    val currentOnAction by rememberUpdatedState(onAction)
    val currentWinController by rememberUpdatedState(winController)

    val entry = remember {
        DesktopKeyDispatcher.Entry(
            enabled = enabled,
            onKey = { key ->
                when (key) {
                    DesktopPlayerKey.SPACE -> currentOnAction(PlayerShortcutAction.TOGGLE_PLAY_PAUSE)
                    DesktopPlayerKey.ARROW_LEFT -> currentOnAction(PlayerShortcutAction.SEEK_BACKWARD_10S)
                    DesktopPlayerKey.ARROW_RIGHT -> currentOnAction(PlayerShortcutAction.SEEK_FORWARD_10S)
                    DesktopPlayerKey.F11 -> currentWinController?.toggleFullscreen()
                }
            }
        )
    }

    SideEffect {
        entry.enabled = enabled
    }

    DisposableEffect(dispatcher, entry) {
        dispatcher.register(entry)
        onDispose {
            dispatcher.unregister(entry)
        }
    }
}

@Composable
actual fun PlatformVideoSurface(
    controller: PlatformPlayerController,
    resizeMode: VideoResizeMode,
    activeSubtitle: CachedSubtitle?,
    subtitlePrefs: SubtitlePreferences,
    subtitleDelayMs: Long,
    onSubtitleBottomMarginChanged: (Float) -> Unit,
    controlsVisible: Boolean,
    modifier: Modifier
) {
    val desktopController = controller as DesktopPlayerController

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

    Box(modifier = modifier.background(Color.Black)) {
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
            DesktopSmartSubtitleOverlay(
                desktopController = desktopController,
                parsedCues = parsedCues,
                resizeMode = resizeMode,
                subtitlePrefs = subtitlePrefs,
                subtitleDelayMs = subtitleDelayMs,
                onSubtitleBottomMarginChanged = onSubtitleBottomMarginChanged,
                controlsVisible = controlsVisible,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DesktopSmartSubtitleOverlay(
    desktopController: DesktopPlayerController,
    parsedCues: List<SubtitleCue>,
    resizeMode: VideoResizeMode,
    subtitlePrefs: SubtitlePreferences,
    subtitleDelayMs: Long,
    onSubtitleBottomMarginChanged: (Float) -> Unit,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val activeState by remember(desktopController, parsedCues, subtitleDelayMs) {
        derivedStateOf {
            val adjustedPosMs = (desktopController.livePositionMs - subtitleDelayMs).coerceAtLeast(0L)
            SrtParser.findActiveState(parsedCues, adjustedPosMs)
        }
    }

    if (!activeState.hasAnyText) return

    val frame = desktopController.currentFrame
    val density = LocalDensity.current
    val currentPrefs by rememberUpdatedState(subtitlePrefs)
    val currentOnMarginChanged by rememberUpdatedState(onSubtitleBottomMarginChanged)
    var isDraggingBottomSub by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(
            maxWidth,
            maxHeight,
            frame?.width,
            frame?.height,
            resizeMode,
            subtitlePrefs,
            controlsVisible
        ) {
            SmartSubtitleCalculator.computeMetrics(
                containerWidthDp = maxWidth.value,
                containerHeightDp = maxHeight.value,
                videoWidthPx = frame?.width ?: 0,
                videoHeightPx = frame?.height ?: 0,
                resizeMode = resizeMode,
                prefs = subtitlePrefs,
                controlsVisible = controlsVisible
            )
        }

        val animatedBottomPad by animateDpAsState(
            targetValue = metrics.bottomPaddingDp.dp,
            animationSpec = tween(durationMillis = if (isDraggingBottomSub) 0 else 200),
            label = "subtitle_bottom_pad"
        )
        val animatedTopPad by animateDpAsState(
            targetValue = metrics.topPaddingDp.dp,
            animationSpec = tween(durationMillis = 200),
            label = "subtitle_top_pad"
        )

        // Top-aligned subtitle cue (e.g., {\an8} translator notes / location titles)
        val topText = activeState.topText
        if (!topText.isNullOrBlank()) {
            CinemaSubtitleBox(
                rawText = topText,
                metrics = metrics,
                subtitlePrefs = subtitlePrefs,
                bottomLuminance = desktopController.bottomRegionLuminance,
                isDragging = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 32.dp, end = 32.dp, top = animatedTopPad)
            )
        }

        // Standard bottom dialogue cue (supports direct vertical mouse dragging to dodge hardsubs)
        val bottomText = activeState.bottomText
        if (!bottomText.isNullOrBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 32.dp, end = 32.dp, bottom = animatedBottomPad)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(metrics.cinemaRefHeightDp) {
                        var workingMargin = currentPrefs.bottomMarginPercent
                        detectVerticalDragGestures(
                            onDragStart = {
                                workingMargin = currentPrefs.bottomMarginPercent
                                isDraggingBottomSub = true
                            },
                            onDragEnd = {
                                isDraggingBottomSub = false
                            },
                            onDragCancel = {
                                isDraggingBottomSub = false
                            },
                            onVerticalDrag = { change, dragAmountPx ->
                                change.consume()
                                val deltaDp = with(density) { dragAmountPx.toDp().value }
                                val deltaPct = (-deltaDp / metrics.cinemaRefHeightDp.coerceAtLeast(120f)) * 100f
                                workingMargin = (workingMargin + deltaPct).coerceIn(
                                    SubtitlePreferences.MIN_BOTTOM_MARGIN_PERCENT,
                                    SubtitlePreferences.MAX_BOTTOM_MARGIN_PERCENT
                                )
                                currentOnMarginChanged(workingMargin)
                            }
                        )
                    }
            ) {
                if (isDraggingBottomSub) {
                    Box(
                        modifier = Modifier
                            .background(NetflixRed.copy(alpha = 0.90f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Độ cao phụ đề: ${subtitlePrefs.bottomMarginPercent.roundToInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                CinemaSubtitleBox(
                    rawText = bottomText,
                    metrics = metrics,
                    subtitlePrefs = subtitlePrefs,
                    bottomLuminance = desktopController.bottomRegionLuminance,
                    isDragging = isDraggingBottomSub
                )
            }
        }
    }
}

@Composable
private fun CinemaSubtitleBox(
    rawText: String,
    metrics: SmartSubtitleLayoutMetrics,
    subtitlePrefs: SubtitlePreferences,
    bottomLuminance: Float,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val balancedText = remember(rawText) {
        SrtParser.balanceSubtitleLines(rawText, maxCharsPerLine = 46)
    }
    val backdropAlpha = SmartSubtitleCalculator.computeBackdropAlpha(
        style = subtitlePrefs.bgStyle,
        bottomLuminance = bottomLuminance
    )
    val strokeWidthPx = remember(metrics.fontSizeSp, density) {
        with(density) { (metrics.fontSizeSp * 0.12f).sp.toPx() }.coerceIn(2.4f, 7.5f)
    }
    val horizPadDp = (metrics.fontSizeSp * 0.42f).coerceIn(10f, 24f).dp
    val vertPadDp = (metrics.fontSizeSp * 0.16f).coerceIn(4f, 10f).dp
    val cornerDp = (metrics.fontSizeSp * 0.22f).coerceIn(6f, 12f).dp

    val strokeStyle = remember(strokeWidthPx) {
        TextStyle(
            drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round),
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.88f),
                offset = Offset(1.5f, 2.0f),
                blurRadius = 6f
            )
        )
    }
    val fillStyle = remember {
        TextStyle(drawStyle = Fill)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(max = metrics.maxBoxWidthDp.dp)
            .then(
                if (backdropAlpha > 0.01f) {
                    Modifier.background(Color.Black.copy(alpha = backdropAlpha), RoundedCornerShape(cornerDp))
                } else {
                    Modifier
                }
            )
            .then(
                if (isDragging) {
                    Modifier.border(1.2.dp, NetflixRed.copy(alpha = 0.85f), RoundedCornerShape(cornerDp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizPadDp, vertical = vertPadDp)
    ) {
        // Crisp rounded black vector outline + soft cinema drop shadow
        Text(
            text = balancedText,
            color = Color.Black,
            fontSize = metrics.fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = metrics.lineHeightSp.sp,
            style = strokeStyle
        )
        // High-contrast foreground glyph fill
        Text(
            text = balancedText,
            color = subtitlePrefs.colorPreset.color,
            fontSize = metrics.fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = metrics.lineHeightSp.sp,
            style = fillStyle
        )
    }
}
