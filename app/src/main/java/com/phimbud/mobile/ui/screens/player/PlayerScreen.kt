package com.phimbud.mobile.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.OnPictureInPictureModeChangedProvider
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.phimbud.mobile.PhimBudApp
import com.phimbud.mobile.player.PhimBudPlayerManager
import com.phimbud.mobile.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Force landscape, immersive fullscreen, notch cutout extension, and keep screen on
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Keep screen on while playing video
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Extend into display cutout (notch / punch hole camera) for Samsung A56 and modern tall displays
        val origCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode
        } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Hide system bars (Status bar & Navigation bar) for immersive edge-to-edge fullscreen
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null && origCutoutMode != null) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = origCutoutMode
                }
            }

            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val playerManager = remember {
        (context.applicationContext as? PhimBudApp)?.repository?.playerManager
            ?: PhimBudPlayerManager(context)
    }
    val exoPlayer = remember { playerManager.createPlayer() }

    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    var isInPipMode by remember {
        val act = context as? Activity
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && act?.isInPictureInPictureMode == true)
    }

    // Pause video & audio on ON_STOP (screen locked with Power button, PiP window closed, or app backgrounded without PiP).
    // Note: Entering PiP only transitions to ON_PAUSE (still STARTED), so PiP playback continues uninterrupted!
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Track Picture-in-Picture mode changes so HUD & dialogs hide automatically inside tiny PiP window
    DisposableEffect(context) {
        val pipProvider = context as? OnPictureInPictureModeChangedProvider
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                controlsVisible = false
            }
        }
        pipProvider?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            pipProvider?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    // Video display aspect ratio mode: FIT (16:9), ZOOM (100% full screen on Samsung 19.5:9), FILL (stretch)
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

    // Re-enforce immersive system-bar hiding after AlertDialogs close (Dialog windows cause Android to re-show status/nav bars)
    LaunchedEffect(showSubtitleDialog, showServerDialog, controlsVisible) {
        if (!showSubtitleDialog && !showServerDialog) {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var currentLoadedFid by remember { mutableStateOf<String?>(null) }
    var currentLoadedUrl by remember { mutableStateOf<String?>(null) }
    var currentLoadedSubFile by remember { mutableStateOf<File?>(null) }

    // Start progress sync
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    playbackError = null
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = "Lỗi luồng phát video (${error.errorCodeName}). Bạn có thể thử lại hoặc đổi Server khác."
            }
        }
        exoPlayer.addListener(listener)
        viewModel.startProgressSync { exoPlayer.currentPosition / 1000 }

        onDispose {
            viewModel.stopProgressSync(exoPlayer.currentPosition / 1000)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Controls auto-hide timer (works for both unlocked HUD and locked "Mở khóa" pill; pauses while dialogs are open)
    LaunchedEffect(controlsVisible, isPlaying, isLocked, showSubtitleDialog, showServerDialog, controlsInteractionTick) {
        if (controlsVisible && !showSubtitleDialog && !showServerDialog && (isPlaying || isLocked)) {
            delay(if (isLocked) 2800L else 4000L)
            controlsVisible = false
        }
    }

    // Prepare player when state is Ready
    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loading) {
            playbackError = null
            return@LaunchedEffect
        }
        val ready = uiState as? PlayerUiState.Ready ?: return@LaunchedEffect
        playbackError = null

        val isNewEpisode = (currentLoadedFid != ready.watchInfo.fid)
        val currentPos = exoPlayer.currentPosition
        val shouldPlay = exoPlayer.playWhenReady

        val activeLang = ready.activeSubtitle?.language ?: "vi"

        if (isNewEpisode) {
            val mediaSource = playerManager.buildMediaSource(
                ready.m3u8Url,
                ready.activeSubtitleFile,
                activeLang
            )
            duration = (ready.watchInfo.durationSeconds * 1000).toLong().coerceAtLeast(0L)
            val resumePos = ready.watchInfo.lastCurrentTime?.times(1000L) ?: 0L
            exoPlayer.setMediaSource(mediaSource)
            if (resumePos > 0) {
                exoPlayer.seekTo(resumePos)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            currentLoadedSubFile = ready.activeSubtitleFile
        } else {
            val urlChanged = (currentLoadedUrl != ready.m3u8Url)
            // Avoid rebuilding MediaSource (and losing the 50s video buffer) when user simply turns subtitles OFF!
            val subChangedToNewFile = (ready.activeSubtitleFile != null && currentLoadedSubFile != ready.activeSubtitleFile)

            if (urlChanged || subChangedToNewFile) {
                val mediaSource = playerManager.buildMediaSource(
                    ready.m3u8Url,
                    ready.activeSubtitleFile,
                    activeLang
                )
                exoPlayer.setMediaSource(mediaSource, currentPos)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = shouldPlay
                currentLoadedSubFile = ready.activeSubtitleFile
            }
        }

        if (ready.activeSubtitleFile != null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(activeLang)
                .build()
        } else {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

        currentLoadedFid = ready.watchInfo.fid
        currentLoadedUrl = ready.m3u8Url
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        // ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // High-contrast Netflix-style subtitles
                    subtitleView?.apply {
                        val captionStyle = CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                        setStyle(captionStyle)
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    }
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading / Buffering indicator
        if (uiState is PlayerUiState.Loading || (uiState is PlayerUiState.Ready && isBuffering)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(50.dp))
            }
        }

        // Error state overlay (handles both API errors and ExoPlayer HLS playback errors)
        val activeErrorMessage = (uiState as? PlayerUiState.Error)?.message ?: playbackError
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
                                if (uiState is PlayerUiState.Ready && playbackError != null) {
                                    playbackError = null
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                } else {
                                    playbackError = null
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
            visible = controlsVisible && !isInPipMode && activeErrorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isLocked) Color.Transparent else Color.Black.copy(alpha = 0.55f))
            ) {
                if (isLocked) {
                    // Unobtrusive Netflix-style unlock pill (does not dim the video or cover center faces)
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
                    // Full Netflix Player Controls
                    val readyState = uiState as? PlayerUiState.Ready

                    // Top Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Server Button
                            TextButton(onClick = { showServerDialog = true }) {
                                Text(
                                    text = "Server ${readyState?.currentServer ?: viewModel.currentServer}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Subtitles Button with active state indicator
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

                            // Aspect Ratio / Fullscreen Scale Button (Fit 16:9 vs Zoom 19.5:9 vs Fill)
                            Surface(
                                onClick = {
                                    controlsInteractionTick++
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                color = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT) NetflixRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Default.ZoomOutMap
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.AspectRatio
                                            else -> Icons.Default.FitScreen
                                        },
                                        contentDescription = "Tỉ lệ màn hình",
                                        tint = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT) NetflixRed else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Tràn viền"
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Kéo giãn"
                                            else -> "Chuẩn"
                                        },
                                        color = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Lock Button
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
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                controlsInteractionTick++
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
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

                        // Play/Pause
                        IconButton(
                            onClick = {
                                controlsInteractionTick++
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Tạm dừng" else "Tiếp tục",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(36.dp))

                        // Forward 10s
                        IconButton(
                            onClick = {
                                controlsInteractionTick++
                                val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(maxDur))
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

                    // Bottom Bar (Isolated composable so 500ms position ticks never recompose PlayerScreen or AndroidView)
                    PlayerBottomBar(
                        exoPlayer = exoPlayer,
                        isPlaying = isPlaying,
                        controlsVisible = controlsVisible,
                        knownDuration = duration,
                        nextTitleFid = readyState?.watchInfo?.nextTitleFid,
                        onScrubActivity = { controlsInteractionTick++ },
                        onNextEpisodeClick = { nextFid ->
                            val prevPosSec = exoPlayer.currentPosition / 1000
                            exoPlayer.pause()
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

        // Subtitle Picker Dialog
        if (showSubtitleDialog && !isInPipMode) {
            val ready = uiState as? PlayerUiState.Ready
            val subtitles = ready?.subtitles ?: emptyList()

            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = {
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
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Chọn Phụ đề",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
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
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Option: Tắt phụ đề
                        val isNoneActive = ready?.activeSubtitle == null
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
                                val isSelected = ready?.activeSubtitle?.id == sub.id
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
            val ready = uiState as? PlayerUiState.Ready
            val activeServer = ready?.currentServer ?: viewModel.currentServer
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
                                    val hadPlaybackError = (playbackError != null)
                                    playbackError = null
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
private fun PlayerBottomBar(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    controlsVisible: Boolean,
    knownDuration: Long,
    nextTitleFid: String?,
    onScrubActivity: () -> Unit,
    onNextEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPosition by remember { mutableLongStateOf(exoPlayer.currentPosition.coerceAtLeast(0L)) }
    var duration by remember(knownDuration) {
        mutableLongStateOf(knownDuration.coerceAtLeast(exoPlayer.duration.coerceAtLeast(0L)))
    }
    var isScrubbing by remember { mutableStateOf(false) }

    // Only poll playback position when controls are actually visible!
    LaunchedEffect(isPlaying, controlsVisible) {
        if (controlsVisible) {
            if (!isScrubbing) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            duration = exoPlayer.duration.coerceAtLeast(knownDuration).coerceAtLeast(0L)
            while (isPlaying && controlsVisible) {
                delay(500)
                if (!isScrubbing) {
                    currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
                val liveDur = exoPlayer.duration
                if (liveDur > 0) {
                    duration = liveDur
                }
            }
        }
    }

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                color = Color.White,
                fontSize = 13.sp
            )

            if (!nextTitleFid.isNullOrBlank()) {
                TextButton(
                    onClick = { onNextEpisodeClick(nextTitleFid) }
                ) {
                    Text(
                        text = "Tập tiếp theo ▶",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Slider(
            value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac ->
                isScrubbing = true
                onScrubActivity()
                currentPosition = (frac * duration).toLong()
            },
            onValueChangeFinished = {
                exoPlayer.seekTo(currentPosition)
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

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
