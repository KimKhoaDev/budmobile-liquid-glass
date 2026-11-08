package com.phimbud.mobile.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.Season
import com.phimbud.mobile.core.model.WatchInfo
import com.phimbud.mobile.core.platform.PlatformBackHandler
import com.phimbud.mobile.core.platform.currentTimeMillis
import com.phimbud.mobile.core.platform.formatPlaybackTime
import com.phimbud.mobile.player.PlatformPlayerController
import com.phimbud.mobile.player.PlatformPlayerShortcutEffect
import com.phimbud.mobile.player.PlatformPlayerWindowEffect
import com.phimbud.mobile.player.PlatformVideoSurface
import com.phimbud.mobile.player.PlayerShortcutAction
import com.phimbud.mobile.player.SkipSegment
import com.phimbud.mobile.player.SkipSegmentType
import com.phimbud.mobile.player.SubtitleBgStyle
import com.phimbud.mobile.player.SubtitleColorPreset
import com.phimbud.mobile.player.SubtitlePreferences
import com.phimbud.mobile.player.SubtitlePreferencesStore
import com.phimbud.mobile.player.VideoResizeMode
import com.phimbud.mobile.player.rememberPlatformBrightnessController
import com.phimbud.mobile.player.rememberPlatformPlayerController
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val controller = rememberPlatformPlayerController(viewModel.repository)
    val subtitlePrefsStore = remember(viewModel.repository) {
        SubtitlePreferencesStore(viewModel.repository.platformContext)
    }
    var subtitlePrefs by remember(subtitlePrefsStore) {
        mutableStateOf(subtitlePrefsStore.load())
    }
    fun updateSubtitlePrefs(updated: SubtitlePreferences) {
        val norm = updated.normalized()
        subtitlePrefs = norm
        subtitlePrefsStore.save(norm)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var subtitleDialogTab by remember { mutableIntStateOf(0) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showEpisodesOverlay by remember { mutableStateOf(false) }

    var osdIcon by remember { mutableStateOf<ImageVector?>(null) }
    var osdLabel by remember { mutableStateOf("") }
    var osdTick by remember { mutableIntStateOf(0) }
    var osdVisible by remember { mutableStateOf(false) }

    val brightnessController = rememberPlatformBrightnessController()
    var isBrightnessOsdVisible by remember { mutableStateOf(false) }
    var isBrightnessDragging by remember { mutableStateOf(false) }
    var brightnessOsdTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(brightnessOsdTick, isBrightnessDragging) {
        if (brightnessOsdTick > 0 && !isBrightnessDragging) {
            delay(1200L)
            isBrightnessOsdVisible = false
        }
    }

    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun triggerShortcutOsd(icon: ImageVector, label: String) {
        osdIcon = icon
        osdLabel = label
        osdTick++
    }

    LaunchedEffect(osdTick) {
        if (osdTick > 0) {
            osdVisible = true
            delay(1000L)
            osdVisible = false
        }
    }

    PlatformBackHandler(enabled = !showEpisodesOverlay && !showSubtitleDialog && !showServerDialog) {
        onBackClick()
    }

    PlatformBackHandler(enabled = showEpisodesOverlay) {
        showEpisodesOverlay = false
    }

    PlatformBackHandler(enabled = showSubtitleDialog || showServerDialog) {
        showSubtitleDialog = false
        showServerDialog = false
    }

    PlatformPlayerWindowEffect(
        showSubtitleDialog = showSubtitleDialog,
        showServerDialog = showServerDialog || showEpisodesOverlay,
        controlsVisible = controlsVisible,
        onUserMouseActivity = {
            if (!showEpisodesOverlay && !showSubtitleDialog && !showServerDialog) {
                controlsVisible = true
                controlsInteractionTick++
            }
        }
    )

    val isInPipMode = controller.isInPipMode
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            controlsVisible = false
            showEpisodesOverlay = false
        }
    }

    // Start / stop 20s progress sync with server
    DisposableEffect(controller) {
        viewModel.startProgressSync { controller.currentPositionMs() / 1000L }
        onDispose {
            viewModel.stopProgressSync(controller.currentPositionMs() / 1000L)
        }
    }

    // Auto-hide HUD timer (pauses while dialogs or episode overlay are open)
    LaunchedEffect(
        controlsVisible,
        controller.isPlaying,
        isLocked,
        showSubtitleDialog,
        showServerDialog,
        showEpisodesOverlay,
        controlsInteractionTick
    ) {
        if (controlsVisible &&
            !showSubtitleDialog &&
            !showServerDialog &&
            !showEpisodesOverlay &&
            (controller.isPlaying || isLocked)
        ) {
            delay(if (isLocked) 2800L else 4000L)
            controlsVisible = false
        }
    }

    val readyState = uiState as? PlayerUiState.Ready
    var subtitleDelayMs by remember(readyState?.watchInfo?.fid, readyState?.activeSubtitle?.id) {
        mutableLongStateOf(0L)
    }

    // Load or update HLS stream & subtitle only when actual media/subtitle inputs change
    LaunchedEffect(
        readyState?.watchInfo?.fid,
        readyState?.m3u8Url,
        readyState?.activeSubtitleData?.path,
        readyState?.activeSubtitle?.language
    ) {
        val ready = readyState ?: return@LaunchedEffect
        val knownDurationMs = (ready.watchInfo.durationSeconds * 1000.0).toLong().coerceAtLeast(0L)
        val rawResumePosMs = ready.watchInfo.lastCurrentTime?.times(1000L) ?: 0L
        val resumePosMs = if (knownDurationMs > 30_000L && rawResumePosMs >= knownDurationMs - 15_000L) {
            0L
        } else {
            rawResumePosMs
        }
        val activeLang = ready.activeSubtitle?.language ?: "vi"
        controller.loadOrUpdateMedia(
            fid = ready.watchInfo.fid,
            m3u8Url = ready.m3u8Url,
            knownDurationMs = knownDurationMs,
            resumePositionMs = resumePosMs,
            subtitle = ready.activeSubtitleData,
            subtitleLanguage = activeLang
        )
    }
    val hasEpisodesList = readyState?.let {
        it.watchInfo.seasonFid.isNotBlank() ||
            it.watchInfo.showFid.isNotBlank() ||
            it.episodes.isNotEmpty()
    } == true

    val resolvedNextEpisodeFid = remember(readyState) {
        val directNext = readyState?.watchInfo?.nextTitleFid?.takeIf { it.isNotBlank() }
        if (directNext != null) {
            directNext
        } else {
            readyState?.let { state ->
                val currentEpNum = state.watchInfo.episodeNumber
                val isCurrentSeason = state.selectedSeason == null ||
                    state.selectedSeason.fid == state.watchInfo.seasonFid ||
                    state.selectedSeason.number == state.watchInfo.seasonNumber
                if (isCurrentSeason) {
                    state.episodes.firstOrNull { it.number == currentEpNum + 1 }?.fid
                } else {
                    null
                }
            }
        }
    }

    // Automatically advance to the next episode when the current episode finishes
    LaunchedEffect(controller.isPlaybackEnded, readyState?.watchInfo?.fid, resolvedNextEpisodeFid) {
        if (controller.isPlaybackEnded && readyState != null && !resolvedNextEpisodeFid.isNullOrBlank()) {
            showEpisodesOverlay = false
            val finishedDurSec = (controller.durationMs.takeIf { it > 0L }
                ?: (readyState.watchInfo.durationSeconds * 1000.0).toLong().coerceAtLeast(0L)) / 1000L
            viewModel.playNextEpisode(resolvedNextEpisodeFid, finishedDurSec)
        }
    }

    val playbackErr = controller.playbackError
    val activeErrorMessage = (uiState as? PlayerUiState.Error)?.message ?: playbackErr

    PlatformPlayerShortcutEffect(
        enabled = !isLocked &&
            !showEpisodesOverlay &&
            !showSubtitleDialog &&
            !showServerDialog &&
            !isInPipMode &&
            activeErrorMessage == null &&
            readyState != null,
        onAction = { action ->
            when (action) {
                PlayerShortcutAction.TOGGLE_PLAY_PAUSE -> {
                    if (controller.isPlaying) {
                        controller.pause()
                        triggerShortcutOsd(Icons.Default.Pause, "Tạm dừng")
                    } else {
                        controller.play()
                        triggerShortcutOsd(Icons.Default.PlayArrow, "Tiếp tục")
                    }
                    if (controlsVisible) controlsInteractionTick++
                }
                PlayerShortcutAction.SEEK_BACKWARD_10S -> {
                    val targetPos = (controller.currentPositionMs() - 10_000L).coerceAtLeast(0L)
                    controller.seekTo(targetPos)
                    triggerShortcutOsd(Icons.Default.Replay10, "-10s • ${formatPlaybackTime(targetPos)}")
                    if (controlsVisible) controlsInteractionTick++
                }
                PlayerShortcutAction.SEEK_FORWARD_10S -> {
                    val knownDurMs = (readyState?.watchInfo?.durationSeconds?.times(1000.0)?.toLong() ?: 0L)
                        .coerceAtLeast(controller.durationMs)
                    val maxDur = knownDurMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val targetPos = (controller.currentPositionMs() + 10_000L).coerceAtMost(maxDur)
                    controller.seekTo(targetPos)
                    triggerShortcutOsd(Icons.Default.Forward10, "+10s • ${formatPlaybackTime(targetPos)}")
                    if (controlsVisible) controlsInteractionTick++
                }
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .pointerInput(
                isLocked,
                showEpisodesOverlay,
                showSubtitleDialog,
                showServerDialog,
                isInPipMode,
                activeErrorMessage
            ) {
                if (showSubtitleDialog || showServerDialog || isInPipMode || activeErrorMessage != null) {
                    return@pointerInput
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val boxWidth = size.width.toFloat()
                    val boxHeight = size.height.toFloat()
                    val isLeftSide = startPos.x < boxWidth * 0.45f
                    val canDragBrightness = isLeftSide && !isLocked && !showEpisodesOverlay

                    var isDragging = false
                    var lastY = startPos.y

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (isDragging) {
                                isBrightnessDragging = false
                                brightnessOsdTick++
                            } else if (!change.isConsumed) {
                                if (showEpisodesOverlay) {
                                    showEpisodesOverlay = false
                                } else if (isLocked) {
                                    controlsVisible = !controlsVisible
                                } else if (controlsVisible) {
                                    controlsVisible = false
                                } else {
                                    val now = currentTimeMillis()
                                    val isDoubleTap = (now - lastTapTimeMs < 320L) &&
                                        abs(startPos.x - lastTapPosition.x) < 140f &&
                                        abs(startPos.y - lastTapPosition.y) < 140f

                                    if (isDoubleTap) {
                                        pendingTapJob?.cancel()
                                        pendingTapJob = null
                                        lastTapTimeMs = 0L

                                        if (startPos.x < boxWidth * 0.5f) {
                                            val targetPos = (controller.currentPositionMs() - 10_000L).coerceAtLeast(0L)
                                            controller.seekTo(targetPos)
                                            triggerShortcutOsd(Icons.Default.Replay10, "-10s • ${formatPlaybackTime(targetPos)}")
                                        } else {
                                            val knownDurMs = (readyState?.watchInfo?.durationSeconds?.times(1000.0)?.toLong() ?: 0L)
                                                .coerceAtLeast(controller.durationMs)
                                            val maxDur = knownDurMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                            val targetPos = (controller.currentPositionMs() + 10_000L).coerceAtMost(maxDur)
                                            controller.seekTo(targetPos)
                                            triggerShortcutOsd(Icons.Default.Forward10, "+10s • ${formatPlaybackTime(targetPos)}")
                                        }
                                    } else {
                                        lastTapTimeMs = now
                                        lastTapPosition = startPos
                                        pendingTapJob?.cancel()
                                        pendingTapJob = coroutineScope.launch {
                                            delay(300L)
                                            controlsVisible = true
                                            controlsInteractionTick++
                                        }
                                    }
                                }
                            }
                            break
                        }

                        val currentY = change.position.y
                        val dy = currentY - startPos.y

                        if (!isDragging && canDragBrightness && abs(dy) > viewConfiguration.touchSlop) {
                            isDragging = true
                            isBrightnessDragging = true
                            isBrightnessOsdVisible = true
                        }

                        if (isDragging) {
                            change.consume()
                            val stepDy = currentY - lastY
                            lastY = currentY
                            val delta = -stepDy / (boxHeight * 0.75f)
                            brightnessController.setBrightness(brightnessController.currentBrightness + delta)
                        }
                    }
                }
            }
    ) {
        // Native Video Surface (ExoPlayer on Android, AVPlayer + Subtitle Overlay on iOS, FFmpeg + Smart Sub on Desktop)
        PlatformVideoSurface(
            controller = controller,
            resizeMode = resizeMode,
            activeSubtitle = readyState?.activeSubtitleData,
            subtitlePrefs = subtitlePrefs,
            subtitleDelayMs = subtitleDelayMs,
            onSubtitleBottomMarginChanged = { newMargin ->
                updateSubtitlePrefs(subtitlePrefs.copy(bottomMarginPercent = newMargin))
            },
            controlsVisible = controlsVisible && !showEpisodesOverlay,
            modifier = Modifier.fillMaxSize()
        )

        // Software Dimming Overlay for Desktop when hardware backlight is unavailable
        if (!brightnessController.isHardwareControlled && brightnessController.currentBrightness < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1.0f - brightnessController.currentBrightness) * 0.75f))
            )
        }

        // Floating Netflix-style Brightness OSD (revealed during vertical drag on the left)
        PlayerBrightnessOsd(
            visible = isBrightnessOsdVisible && !controlsVisible && !isLocked && !isInPipMode,
            brightness = brightnessController.currentBrightness,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .displayCutoutPadding()
                .padding(start = 28.dp)
        )

        // Compact Netflix-style Keyboard Shortcut OSD Pill (does not darken movie frame)
        AnimatedVisibility(
            visible = osdVisible && !isInPipMode && activeErrorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .padding(top = if (controlsVisible && !isLocked) 74.dp else 26.dp)
        ) {
            val icon = osdIcon
            if (icon != null && osdLabel.isNotBlank()) {
                Surface(
                    color = NetflixDarkGray.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = NetflixRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = osdLabel,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Loading / Buffering indicator
        if (uiState is PlayerUiState.Loading || (uiState is PlayerUiState.Ready && controller.isBuffering)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(50.dp))
            }
        }

        // Error state overlay (handles both API errors and HLS playback errors)
        if (activeErrorMessage != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = activeErrorMessage,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (uiState is PlayerUiState.Ready && playbackErr != null) {
                                    controller.clearErrorAndRetry()
                                } else {
                                    viewModel.loadStream(viewModel.watchFid, viewModel.currentServer)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                        ) {
                            Text("Thử lại", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showServerDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Đổi Server (hiện tại: ${viewModel.currentServer})")
                        }
                        TextButton(onClick = onBackClick) {
                            Text("Quay lại", color = Color.LightGray)
                        }
                    }
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible && !showEpisodesOverlay && !isInPipMode && activeErrorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isLocked) Color.Transparent else Color.Black.copy(alpha = 0.55f))
                    .displayCutoutPadding()
            ) {
                if (isLocked) {
                    Surface(
                        onClick = {
                            isLocked = false
                            controlsInteractionTick++
                        },
                        color = NetflixDarkGray.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Mở khóa màn hình",
                                tint = NetflixRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mở khóa",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Top Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Quay lại",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = readyState?.watchInfo?.let {
                                    if (it.showNameVi.isNotBlank()) "${it.showNameVi} - Tập ${it.episodeNumber}" else it.showNameEn
                                } ?: "Đang tải...",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showServerDialog = true }) {
                                Text(
                                    text = "Server ${readyState?.currentServer ?: viewModel.currentServer}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Surface(
                                onClick = { showSubtitleDialog = true },
                                color = if (readyState?.activeSubtitle != null) NetflixRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = "Phụ đề",
                                        tint = if (readyState?.activeSubtitle != null) NetflixRed else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = readyState?.activeSubtitle?.let { "Sub: ${it.label}" } ?: "Sub: Tắt",
                                        color = if (readyState?.activeSubtitle != null) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    controlsInteractionTick++
                                    resizeMode = when (resizeMode) {
                                        VideoResizeMode.FIT -> VideoResizeMode.ZOOM
                                        VideoResizeMode.ZOOM -> VideoResizeMode.FILL
                                        VideoResizeMode.FILL -> VideoResizeMode.FIT
                                    }
                                },
                                color = if (resizeMode != VideoResizeMode.FIT) NetflixRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = when (resizeMode) {
                                            VideoResizeMode.ZOOM -> Icons.Default.ZoomOutMap
                                            VideoResizeMode.FILL -> Icons.Default.AspectRatio
                                            VideoResizeMode.FIT -> Icons.Default.FitScreen
                                        },
                                        contentDescription = "Tỉ lệ màn hình",
                                        tint = if (resizeMode != VideoResizeMode.FIT) NetflixRed else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (resizeMode) {
                                            VideoResizeMode.ZOOM -> "Tràn viền"
                                            VideoResizeMode.FILL -> "Kéo giãn"
                                            VideoResizeMode.FIT -> "Chuẩn"
                                        },
                                        color = if (resizeMode != VideoResizeMode.FIT) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IconButton(onClick = { isLocked = true }) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Khóa màn hình",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Center Playback Buttons (Rewind 10s, Play/Pause, Forward 10s)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                controlsInteractionTick++
                                controller.seekTo((controller.currentPositionMs() - 10_000L).coerceAtLeast(0L))
                            },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Lùi 10s",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(36.dp))

                        IconButton(
                            onClick = {
                                controlsInteractionTick++
                                if (controller.isPlaying) controller.pause() else controller.play()
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (controller.isPlaying) "Tạm dừng" else "Tiếp tục",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(36.dp))

                        IconButton(
                            onClick = {
                                controlsInteractionTick++
                                val maxDur = controller.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                controller.seekTo((controller.currentPositionMs() + 10_000L).coerceAtMost(maxDur))
                            },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Tới 10s",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // In-HUD Vertical Brightness Slider on the left edge
                    PlayerBrightnessBar(
                        brightness = brightnessController.currentBrightness,
                        onBrightnessChanged = { newBrightness ->
                            controlsInteractionTick++
                            brightnessController.setBrightness(newBrightness)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                    )

                    val knownDurMs = readyState?.watchInfo?.durationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L) ?: 0L
                    PlayerBottomBar(
                        controller = controller,
                        isPlaying = controller.isPlaying,
                        controlsVisible = controlsVisible,
                        knownDuration = knownDurMs.coerceAtLeast(controller.durationMs),
                        nextTitleFid = resolvedNextEpisodeFid,
                        hasEpisodesList = hasEpisodesList,
                        skipSegments = readyState?.skipSegments ?: emptyList(),
                        onScrubActivity = { controlsInteractionTick++ },
                        onOpenEpisodesClick = {
                            controlsInteractionTick++
                            showEpisodesOverlay = true
                        },
                        onNextEpisodeClick = { nextFid ->
                            val prevPosSec = controller.currentPositionMs() / 1000L
                            controller.pause()
                            viewModel.playNextEpisode(nextFid, prevPosSec)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }
            }
        }

        // Netflix-style Floating Skip Intro / Skip Recap Overlay (shared across Android and iOS)
        FloatingSkipOverlay(
            controller = controller,
            isPlaying = controller.isPlaying,
            skipSegments = readyState?.skipSegments ?: emptyList(),
            controlsVisible = controlsVisible,
            isLocked = isLocked || showEpisodesOverlay,
            isInPipMode = isInPipMode,
            hasError = activeErrorMessage != null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .displayCutoutPadding()
                .padding(
                    end = 26.dp,
                    bottom = if (controlsVisible && !isLocked) 96.dp else 28.dp
                )
        )

        // Netflix-style In-Player Episode List Overlay
        AnimatedVisibility(
            visible = showEpisodesOverlay && readyState != null && !isInPipMode,
            enter = fadeIn() + slideInHorizontally { it / 4 },
            exit = fadeOut() + slideOutHorizontally { it / 4 },
            modifier = Modifier.fillMaxSize()
        ) {
            if (readyState != null) {
                PlayerEpisodesOverlay(
                    watchInfo = readyState.watchInfo,
                    seasons = readyState.seasons,
                    selectedSeason = readyState.selectedSeason,
                    episodes = readyState.episodes,
                    isLoading = readyState.isEpisodesLoading,
                    onSeasonSelected = { season ->
                        viewModel.selectSeason(season)
                    },
                    onEpisodeSelected = { episode ->
                        showEpisodesOverlay = false
                        if (episode.fid != readyState.watchInfo.fid) {
                            val prevPosSec = controller.currentPositionMs() / 1000L
                            controller.pause()
                            viewModel.playNextEpisode(episode.fid, prevPosSec)
                        }
                    },
                    onDismiss = {
                        showEpisodesOverlay = false
                        controlsVisible = true
                        controlsInteractionTick++
                    }
                )
            }
        }

        // Subtitle Picker Dialog
        if (showSubtitleDialog && !isInPipMode) {
            val subtitles = readyState?.subtitles ?: emptyList()

            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = NetflixRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Phụ đề & Tùy chỉnh",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                            if (subtitles.isNotEmpty()) {
                                Text(
                                    text = "${subtitles.size} phụ đề",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Segmented Tab Switcher (Netflix Pill Style)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NetflixCard, RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { subtitleDialogTab = 0 },
                                color = if (subtitleDialogTab == 0) NetflixRed else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = if (subtitleDialogTab == 0) Color.White else NetflixTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Phụ đề" + if (subtitles.isNotEmpty()) " (${subtitles.size})" else "",
                                        color = if (subtitleDialogTab == 0) Color.White else NetflixTextSecondary,
                                        fontWeight = if (subtitleDialogTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Surface(
                                onClick = { subtitleDialogTab = 1 },
                                color = if (subtitleDialogTab == 1) NetflixRed else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = if (subtitleDialogTab == 1) Color.White else NetflixTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cài đặt",
                                        color = if (subtitleDialogTab == 1) Color.White else NetflixTextSecondary,
                                        fontWeight = if (subtitleDialogTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                },
                text = {
                    if (subtitleDialogTab == 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            val isNoneActive = readyState?.activeSubtitle == null
                            Surface(
                                onClick = {
                                    viewModel.selectSubtitle(null)
                                    showSubtitleDialog = false
                                },
                                color = if (isNoneActive) NetflixRed.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.SubtitlesOff,
                                            contentDescription = null,
                                            tint = if (isNoneActive) NetflixRed else Color.LightGray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Tắt phụ đề",
                                            color = if (isNoneActive) NetflixRed else Color.White,
                                            fontWeight = if (isNoneActive) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                    if (isNoneActive) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Đang chọn",
                                            tint = NetflixRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.6f), thickness = 0.8.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            if (subtitles.isEmpty()) {
                                Text(
                                    text = "Không tìm thấy phụ đề nào cho tập phim này",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                subtitles.forEach { sub ->
                                    val isSelected = readyState?.activeSubtitle?.id == sub.id
                                    val isVi = sub.language.startsWith("vi", ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            viewModel.selectSubtitle(sub)
                                            showSubtitleDialog = false
                                        },
                                        color = if (isSelected) NetflixRed.copy(alpha = 0.2f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = sub.label,
                                                        color = if (isSelected) NetflixRed else Color.White,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 14.sp
                                                    )
                                                    if (isVi) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Surface(
                                                            color = NetflixRed,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "Vietsub",
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (sub.releaseName.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = sub.releaseName,
                                                        color = Color.Gray,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Đang chọn",
                                                    tint = NetflixRed,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SubtitleStyleCustomizerSection(
                                prefs = subtitlePrefs,
                                subtitleDelayMs = subtitleDelayMs,
                                onSubtitleDelayChanged = { newDelay ->
                                    subtitleDelayMs = newDelay.coerceIn(-15_000L, 15_000L)
                                },
                                onPrefsChanged = ::updateSubtitlePrefs
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("Đóng", color = NetflixRed, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = NetflixDarkGray,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Server Picker Dialog
        if (showServerDialog && !isInPipMode) {
            val activeServer = readyState?.currentServer ?: viewModel.currentServer
            AlertDialog(
                onDismissRequest = { showServerDialog = false },
                title = { Text("Chọn Server phát", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        listOf("1", "2", "3").forEach { srv ->
                            val isSelected = activeServer == srv
                            Surface(
                                onClick = {
                                    val hadPlaybackError = (controller.playbackError != null)
                                    if (hadPlaybackError && srv == activeServer) {
                                        viewModel.loadStream(viewModel.watchFid, srv)
                                    } else {
                                        viewModel.switchServer(srv)
                                    }
                                    showServerDialog = false
                                },
                                color = if (isSelected) NetflixRed.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Server $srv" + if (srv != "1") " (VIP)" else " (Mặc định)",
                                        color = if (isSelected) NetflixRed else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Đang chọn",
                                            tint = NetflixRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showServerDialog = false }) {
                        Text("Đóng", color = NetflixRed, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = NetflixDarkGray,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun PlayerEpisodesOverlay(
    watchInfo: WatchInfo,
    seasons: List<Season>,
    selectedSeason: Season?,
    episodes: List<Episode>,
    isLoading: Boolean,
    onSeasonSelected: (Season) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onDismiss: () -> Unit
) {
    val activeEpisodeIndex = remember(episodes, watchInfo.fid, watchInfo.episodeNumber, selectedSeason?.fid) {
        val byFid = episodes.indexOfFirst { it.fid == watchInfo.fid }
        if (byFid >= 0) {
            byFid
        } else if (selectedSeason == null || selectedSeason.fid == watchInfo.seasonFid || selectedSeason.number == watchInfo.seasonNumber) {
            episodes.indexOfFirst { it.number == watchInfo.episodeNumber }.coerceAtLeast(0)
        } else {
            0
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (activeEpisodeIndex - 1).coerceAtLeast(0)
    )

    LaunchedEffect(selectedSeason?.fid, episodes.size) {
        if (episodes.isNotEmpty()) {
            val targetIdx = (activeEpisodeIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(targetIdx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Consume background clicks so they don't toggle underlying HUD
            }
            .displayCutoutPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Show Title + Season Chips + Close Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = watchInfo.displayShowTitle.ifBlank { "Danh sách tập phim" },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Đang phát: Mùa ${watchInfo.seasonNumber} • Tập ${watchInfo.episodeNumber}",
                        color = NetflixRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (seasons.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1.2f, fill = false)
                    ) {
                        items(
                            items = seasons,
                            key = { it.fid }
                        ) { season ->
                            val isSelected = selectedSeason?.fid == season.fid
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSeasonSelected(season) },
                                label = {
                                    Text(
                                        text = "Mùa ${season.number}",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NetflixRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = NetflixCard,
                                    labelColor = NetflixTextSecondary
                                )
                            )
                        }
                    }
                }

                Surface(
                    onClick = onDismiss,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng danh sách tập",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLoading && episodes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(40.dp))
                    }
                }
                episodes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Không tìm thấy danh sách tập phim",
                            color = NetflixTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(
                            items = episodes,
                            key = { it.fid }
                        ) { episode ->
                            val isCurrentSeason = selectedSeason == null ||
                                selectedSeason.fid == watchInfo.seasonFid ||
                                selectedSeason.number == watchInfo.seasonNumber
                            val isCurrentEpisode = episode.fid == watchInfo.fid ||
                                (isCurrentSeason && episode.number == watchInfo.episodeNumber)

                            PlayerEpisodeRow(
                                episode = episode,
                                isCurrentEpisode = isCurrentEpisode,
                                onClick = { onEpisodeSelected(episode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerEpisodeRow(
    episode: Episode,
    isCurrentEpisode: Boolean,
    onClick: () -> Unit
) {
    val displayTitle = remember(episode.number, episode.name) {
        if (episode.name.isBlank() || episode.name.equals("Tập ${episode.number}", ignoreCase = true)) {
            "Tập ${episode.number}"
        } else {
            "Tập ${episode.number}: ${episode.name}"
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrentEpisode) NetflixRed.copy(alpha = 0.16f) else NetflixDarkGray.copy(alpha = 0.85f)
            )
            .then(
                if (isCurrentEpisode) {
                    Modifier.border(1.2.dp, NetflixRed.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Episode Thumbnail
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NetflixCard)
        ) {
            if (!episode.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isCurrentEpisode) 0.45f else 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Phát",
                    tint = if (isCurrentEpisode) NetflixRed else Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayTitle,
                    color = if (isCurrentEpisode) Color.White else NetflixTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrentEpisode) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!episode.airDate.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = episode.airDate,
                    color = NetflixTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        if (isCurrentEpisode) {
            Surface(
                color = NetflixRed,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Đang phát",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingSkipOverlay(
    controller: PlatformPlayerController,
    isPlaying: Boolean,
    skipSegments: List<SkipSegment>,
    controlsVisible: Boolean,
    isLocked: Boolean,
    isInPipMode: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier
) {
    var activeSegment by remember(skipSegments) { mutableStateOf<SkipSegment?>(null) }
    var lastPromptedSegment by remember(skipSegments) { mutableStateOf<SkipSegment?>(null) }
    var isPromptVisible by remember { mutableStateOf(false) }
    var promptTick by remember { mutableIntStateOf(0) }
    var skippedSegment by remember(skipSegments) { mutableStateOf<SkipSegment?>(null) }

    val maxSegmentEndMs = remember(skipSegments) {
        skipSegments.maxOfOrNull { it.endMs } ?: 0L
    }

    LaunchedEffect(skipSegments, isPlaying, controlsVisible, isLocked, isInPipMode, hasError) {
        if (skipSegments.isEmpty() || isLocked || isInPipMode || hasError) {
            activeSegment = null
            isPromptVisible = false
            return@LaunchedEffect
        }

        fun checkSegment() {
            val pos = controller.currentPositionMs().coerceAtLeast(0L)
            val matching = skipSegments.firstOrNull { seg ->
                pos in seg.startMs..(seg.endMs - 1_500L)
            }
            if (matching != activeSegment) {
                activeSegment = matching
                if (matching != null && matching != skippedSegment && matching != lastPromptedSegment) {
                    lastPromptedSegment = matching
                    isPromptVisible = true
                    promptTick++
                }
            }
        }

        checkSegment()
        while (isPlaying && controller.currentPositionMs() <= maxSegmentEndMs + 5_000L) {
            delay(200L)
            checkSegment()
        }
        if (controller.currentPositionMs() > maxSegmentEndMs) {
            activeSegment = null
            isPromptVisible = false
        }
    }

    // 10-second auto-dismiss timer so the prompt is easy to catch without staying forever
    LaunchedEffect(promptTick, isPlaying) {
        if (isPromptVisible && isPlaying) {
            delay(10_000L)
            isPromptVisible = false
        }
    }

    val currentSeg = activeSegment
    val shouldShow = currentSeg != null &&
        currentSeg != skippedSegment &&
        (isPromptVisible || controlsVisible) &&
        !isLocked &&
        !isInPipMode &&
        !hasError

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (currentSeg != null) {
            Surface(
                onClick = {
                    skippedSegment = currentSeg
                    isPromptVisible = false
                    controller.seekTo(currentSeg.endMs)
                    activeSegment = null
                },
                color = Color.Black.copy(alpha = 0.82f),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = currentSeg.label,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentSeg.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBottomBar(
    controller: PlatformPlayerController,
    isPlaying: Boolean,
    controlsVisible: Boolean,
    knownDuration: Long,
    nextTitleFid: String?,
    hasEpisodesList: Boolean,
    skipSegments: List<SkipSegment>,
    onScrubActivity: () -> Unit,
    onOpenEpisodesClick: () -> Unit,
    onNextEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPosition by remember { mutableLongStateOf(controller.currentPositionMs().coerceAtLeast(0L)) }
    var duration by remember(knownDuration) {
        mutableLongStateOf(knownDuration.coerceAtLeast(controller.durationMs.coerceAtLeast(0L)))
    }
    var isScrubbing by remember { mutableStateOf(false) }
    var hasManuallySkipped by remember(controller) { mutableStateOf(false) }

    // Only poll playback position when controls are actually visible!
    LaunchedEffect(isPlaying, controlsVisible) {
        if (controlsVisible) {
            if (!isScrubbing) {
                currentPosition = controller.currentPositionMs().coerceAtLeast(0L)
            }
            duration = controller.durationMs.coerceAtLeast(knownDuration).coerceAtLeast(0L)
            while (isPlaying && controlsVisible) {
                delay(500)
                if (!isScrubbing) {
                    currentPosition = controller.currentPositionMs().coerceAtLeast(0L)
                }
                val liveDur = controller.durationMs
                if (liveDur > 0L) {
                    duration = liveDur
                }
            }
        }
    }

    // Only show manual backup skip if NO auto-detected Intro exists, hasn't been clicked yet, and within first 90s
    val hasAutoIntro = remember(skipSegments) {
        skipSegments.any { it.type == SkipSegmentType.INTRO }
    }
    val isWithinIntroWindow by remember {
        derivedStateOf { currentPosition in 0L..90_000L }
    }
    val showQuickSkip85s = !hasAutoIntro &&
        !hasManuallySkipped &&
        isWithinIntroWindow &&
        duration > 180_000L

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${formatPlaybackTime(currentPosition)} / ${formatPlaybackTime(duration)}",
                color = Color.White,
                fontSize = 13.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showQuickSkip85s) {
                    Surface(
                        onClick = {
                            hasManuallySkipped = true
                            onScrubActivity()
                            val targetPos = (currentPosition + 85_000L).coerceAtMost(duration)
                            controller.seekTo(targetPos)
                            currentPosition = targetPos
                        },
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Bỏ qua Mở đầu (+85s)",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Bỏ qua Mở đầu (+85s)",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (hasEpisodesList) {
                    Surface(
                        onClick = onOpenEpisodesClick,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Các tập phim",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Các tập",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (!nextTitleFid.isNullOrBlank()) {
                    Surface(
                        onClick = { onNextEpisodeClick(nextTitleFid) },
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Tập tiếp theo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tập tiếp theo",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Slider(
            value = if (duration > 0L) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac ->
                isScrubbing = true
                onScrubActivity()
                currentPosition = (frac * duration).toLong()
            },
            onValueChangeFinished = {
                controller.seekTo(currentPosition)
                isScrubbing = false
                onScrubActivity()
            },
            colors = SliderDefaults.colors(
                thumbColor = NetflixRed,
                activeTrackColor = NetflixRed,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatSubtitleDelayLabel(delayMs: Long): String {
    if (delayMs == 0L) return "0.00s"
    val centiSec = kotlin.math.abs(delayMs) / 10L
    val whole = centiSec / 100L
    val frac = (centiSec % 100L).toString().padStart(2, '0')
    return if (delayMs > 0L) "Trễ +$whole.${frac}s" else "Sớm -$whole.${frac}s"
}

@Composable
private fun SubtitleStyleCustomizerSection(
    prefs: SubtitlePreferences,
    subtitleDelayMs: Long,
    onSubtitleDelayChanged: (Long) -> Unit,
    onPrefsChanged: (SubtitlePreferences) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Tùy chỉnh hiển thị",
                color = NetflixTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            val isDefault = prefs == SubtitlePreferences() && subtitleDelayMs == 0L
            if (!isDefault) {
                Surface(
                    onClick = {
                        onPrefsChanged(SubtitlePreferences())
                        onSubtitleDelayChanged(0L)
                    },
                    color = NetflixCard,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Mặc định",
                        color = NetflixTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 0. Subtitle Timing Sync Fine-Tuning (per-episode / mistimed .srt release safeguard)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Đồng bộ tiếng",
                    color = NetflixTextSecondary,
                    fontSize = 13.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { onSubtitleDelayChanged(subtitleDelayMs - 250L) },
                        color = NetflixCard,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "-0.25s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = { onSubtitleDelayChanged(0L) },
                        color = if (subtitleDelayMs != 0L) NetflixRed.copy(alpha = 0.25f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = formatSubtitleDelayLabel(subtitleDelayMs),
                            color = if (subtitleDelayMs != 0L) NetflixRed else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = { onSubtitleDelayChanged(subtitleDelayMs + 250L) },
                        color = NetflixCard,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "+0.25s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (subtitleDelayMs != 0L) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (subtitleDelayMs > 0L) {
                        "Đang làm phụ đề hiện chậm lại (khớp khi sub chạy trước tiếng)"
                    } else {
                        "Đang làm phụ đề hiện sớm hơn (khớp khi sub chạy sau tiếng)"
                    },
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        // 1. Font Size Scale (A- / % / A+)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cỡ chữ",
                color = NetflixTextSecondary,
                fontSize = 13.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        onPrefsChanged(
                            prefs.copy(fontScale = prefs.fontScale - SubtitlePreferences.FONT_SCALE_STEP)
                        )
                    },
                    color = NetflixCard,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "A-",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = "${prefs.fontScalePercent}%",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(46.dp)
                )
                Surface(
                    onClick = {
                        onPrefsChanged(
                            prefs.copy(fontScale = prefs.fontScale + SubtitlePreferences.FONT_SCALE_STEP)
                        )
                    },
                    color = NetflixCard,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "A+",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 2. Vertical Position (Height from bottom + Quick Presets)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Độ cao: ${prefs.bottomMarginPercent.roundToInt()}%",
                    color = NetflixTextSecondary,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isCinema = prefs.bottomMarginPercent.roundToInt() ==
                        SubtitlePreferences.DEFAULT_BOTTOM_MARGIN_PERCENT.roundToInt()
                    val isHardsub = prefs.bottomMarginPercent.roundToInt() ==
                        SubtitlePreferences.HARDSUB_AVOID_MARGIN_PERCENT.roundToInt()

                    Surface(
                        onClick = {
                            onPrefsChanged(
                                prefs.copy(bottomMarginPercent = SubtitlePreferences.DEFAULT_BOTTOM_MARGIN_PERCENT)
                            )
                        },
                        color = if (isCinema) NetflixRed else NetflixCard,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Chuẩn",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            onPrefsChanged(
                                prefs.copy(bottomMarginPercent = SubtitlePreferences.HARDSUB_AVOID_MARGIN_PERCENT)
                            )
                        },
                        color = if (isHardsub) NetflixRed else NetflixCard,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Né Hardsub",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Slider(
                value = prefs.bottomMarginPercent,
                onValueChange = { newVal ->
                    onPrefsChanged(prefs.copy(bottomMarginPercent = newVal))
                },
                valueRange = SubtitlePreferences.MIN_BOTTOM_MARGIN_PERCENT..SubtitlePreferences.MAX_BOTTOM_MARGIN_PERCENT,
                colors = SliderDefaults.colors(
                    thumbColor = NetflixRed,
                    activeTrackColor = NetflixRed,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Mẹo: Có thể kéo chuột trực tiếp trên dòng phụ đề để nâng/hạ nhanh",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        // 3. Backdrop Style
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Kiểu nền",
                color = NetflixTextSecondary,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitleBgStyle.entries.forEach { style ->
                    val selected = prefs.bgStyle == style
                    Surface(
                        onClick = { onPrefsChanged(prefs.copy(bgStyle = style)) },
                        color = if (selected) NetflixRed else NetflixCard,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = style.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 4. Text Color
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Màu chữ",
                color = NetflixTextSecondary,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitleColorPreset.entries.forEach { preset ->
                    val selected = prefs.colorPreset == preset
                    Surface(
                        onClick = { onPrefsChanged(prefs.copy(colorPreset = preset)) },
                        color = if (selected) NetflixRed else NetflixCard,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = preset.label,
                            color = if (selected) Color.White else preset.color,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
