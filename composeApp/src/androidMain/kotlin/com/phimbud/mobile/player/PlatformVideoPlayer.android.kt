package com.phimbud.mobile.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.OnPictureInPictureModeChangedProvider
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.repository.PhimBudRepository
import java.io.File

@OptIn(UnstableApi::class)
internal class AndroidExoPlayerController(
    private val playerManager: PhimBudPlayerManager,
    val exoPlayer: ExoPlayer,
    initialInPipMode: Boolean
) : PlatformPlayerController {

    override var isPlaying by mutableStateOf(false)
    override var isBuffering by mutableStateOf(false)
    override var isPlaybackEnded by mutableStateOf(false)
    override var playbackError by mutableStateOf<String?>(null)
    override var durationMs by mutableLongStateOf(0L)
    override var isInPipMode by mutableStateOf(initialInPipMode)

    private var currentLoadedFid: String? = null
    private var currentLoadedUrl: String? = null
    private var currentLoadedSubPath: String? = null

    override fun currentPositionMs(): Long = exoPlayer.currentPosition.coerceAtLeast(0L)

    override fun play() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            isPlaybackEnded = false
            exoPlayer.seekTo(0L)
        }
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        if (durationMs <= 0L || target < durationMs - 2_000L) {
            isPlaybackEnded = false
        }
        exoPlayer.seekTo(target)
    }

    override fun clearErrorAndRetry() {
        playbackError = null
        isPlaybackEnded = false
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun loadOrUpdateMedia(
        fid: String,
        m3u8Url: String,
        knownDurationMs: Long,
        resumePositionMs: Long,
        subtitle: CachedSubtitle?,
        subtitleLanguage: String
    ) {
        playbackError = null
        val isNewEpisode = (currentLoadedFid != fid)
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val shouldPlay = exoPlayer.playWhenReady
        val subFile = subtitle?.path?.let { File(it) }

        if (isNewEpisode) {
            isPlaybackEnded = false
            val mediaSource = playerManager.buildMediaSource(
                m3u8Url = m3u8Url,
                subtitleFile = subFile,
                language = subtitleLanguage
            )
            durationMs = knownDurationMs
            exoPlayer.setMediaSource(mediaSource, resumePositionMs.coerceAtLeast(0L))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            currentLoadedSubPath = subtitle?.path
        } else {
            val urlChanged = (currentLoadedUrl != m3u8Url)
            // Avoid rebuilding MediaSource (preserving 50s video buffer) when user simply turns subtitles OFF!
            val subChangedToNewFile = (subtitle != null && currentLoadedSubPath != subtitle.path)

            if (urlChanged || subChangedToNewFile) {
                val mediaSource = playerManager.buildMediaSource(
                    m3u8Url = m3u8Url,
                    subtitleFile = subFile,
                    language = subtitleLanguage
                )
                exoPlayer.setMediaSource(mediaSource, currentPos)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = shouldPlay
                currentLoadedSubPath = subtitle?.path
            }
        }

        if (subtitle != null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(subtitleLanguage)
                .build()
        } else {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

        currentLoadedFid = fid
        currentLoadedUrl = m3u8Url
    }
}

@OptIn(UnstableApi::class)
@Composable
actual fun rememberPlatformPlayerController(
    repository: PhimBudRepository
): PlatformPlayerController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val playerManager = remember(repository) {
        PhimBudPlayerManager(
            context = context.applicationContext,
            baseOkHttpClient = repository.apiClient.httpEngine.okHttpClient
        )
    }
    val controller = remember(playerManager) {
        val act = context as? Activity
        val initialPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && act?.isInPictureInPictureMode == true
        AndroidExoPlayerController(
            playerManager = playerManager,
            exoPlayer = playerManager.createPlayer(),
            initialInPipMode = initialPip
        )
    }

    // Pause video & audio on ON_STOP (screen locked, PiP dismissed, or backgrounded without PiP).
    // Entering PiP only transitions to ON_PAUSE (still STARTED), so PiP playback continues smoothly.
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                controller.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context, controller) {
        val pipProvider = context as? OnPictureInPictureModeChangedProvider
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            controller.isInPipMode = info.isInPictureInPictureMode
        }
        pipProvider?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            pipProvider?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    DisposableEffect(controller) {
        val exo = controller.exoPlayer
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                controller.isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                controller.isBuffering = (state == Player.STATE_BUFFERING)
                controller.isPlaybackEnded = (state == Player.STATE_ENDED)
                if (state == Player.STATE_READY) {
                    controller.playbackError = null
                    val dur = exo.duration
                    if (dur > 0L) {
                        controller.durationMs = dur
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                controller.isBuffering = false
                controller.playbackError =
                    "Lỗi luồng phát video (${error.errorCodeName}). Bạn có thể thử lại hoặc đổi Server khác."
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
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
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val origCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode
        } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

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

    LaunchedEffect(showSubtitleDialog, showServerDialog, controlsVisible) {
        if (!showSubtitleDialog && !showServerDialog) {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
actual fun PlatformPlayerShortcutEffect(
    enabled: Boolean,
    onAction: (PlayerShortcutAction) -> Unit
) = Unit

@OptIn(UnstableApi::class)
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
    val androidController = controller as AndroidExoPlayerController
    val exoResizeMode = when (resizeMode) {
        VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }
    val fgColor = when (subtitlePrefs.colorPreset) {
        SubtitleColorPreset.WHITE -> android.graphics.Color.WHITE
        SubtitleColorPreset.WARM_YELLOW -> android.graphics.Color.rgb(255, 243, 176)
    }
    val windowColor = when (subtitlePrefs.bgStyle) {
        SubtitleBgStyle.NETFLIX_OUTLINE -> android.graphics.Color.TRANSPARENT
        SubtitleBgStyle.SMART_ADAPTIVE -> android.graphics.Color.argb(90, 0, 0, 0)
        SubtitleBgStyle.DARK_BOX -> android.graphics.Color.argb(195, 0, 0, 0)
    }
    val bottomFraction = (subtitlePrefs.bottomMarginPercent / 100f).coerceIn(0.04f, 0.38f)
    val scaledSp = (18f * subtitlePrefs.fontScale).coerceIn(13f, 30f)

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = androidController.exoPlayer
                useController = false
                this.resizeMode = exoResizeMode
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                subtitleView?.apply {
                    setStyle(
                        CaptionStyleCompat(
                            fgColor,
                            windowColor,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSp)
                    setBottomPaddingFraction(bottomFraction)
                }
            }
        },
        update = { playerView ->
            playerView.resizeMode = exoResizeMode
            playerView.subtitleView?.apply {
                setStyle(
                    CaptionStyleCompat(
                        fgColor,
                        windowColor,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null
                    )
                )
                setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSp)
                setBottomPaddingFraction(bottomFraction)
            }
        },
        modifier = modifier
    )
}
