@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.phimbud.mobile.player

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitView
import com.phimbud.mobile.core.crypto.SrtParser
import com.phimbud.mobile.core.crypto.SubtitleCue
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.platform.logError
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerItemStatusUnknown
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.setValue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationMaskAllButUpsideDown
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.attemptRotationToDeviceOrientation

internal class IosAvPlayerController(
    val avPlayer: AVPlayer,
    private val proxyServer: IosHlsProxyServer,
    private val scope: CoroutineScope
) : PlatformPlayerController {

    override var isPlaying by mutableStateOf(false)
    override var isBuffering by mutableStateOf(false)
    override var isPlaybackEnded by mutableStateOf(false)
    override var playbackError by mutableStateOf<String?>(null)
    override var durationMs by mutableLongStateOf(0L)
    override val isInPipMode: Boolean = false

    var livePositionMs by mutableLongStateOf(0L)
        private set

    private var currentLoadedFid: String? = null
    private var currentLoadedUrl: String? = null
    private var pendingResumeMs: Long = 0L
    private var wantToPlay: Boolean = false
    private var loadJob: Job? = null

    override fun currentPositionMs(): Long {
        val item = avPlayer.currentItem ?: return livePositionMs
        val sec = CMTimeGetSeconds(item.currentTime())
        return if (!sec.isNaN() && sec.isFinite() && sec >= 0.0) {
            (sec * 1000.0).toLong()
        } else {
            livePositionMs
        }
    }

    override fun play() {
        if (isPlaybackEnded) {
            isPlaybackEnded = false
            seekTo(0L)
        }
        wantToPlay = true
        avPlayer.play()
    }

    override fun pause() {
        wantToPlay = false
        avPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceAtLeast(0L)
        if (durationMs <= 0L || targetMs < durationMs - 2_000L) {
            isPlaybackEnded = false
        }
        val targetSec = targetMs / 1000.0
        livePositionMs = targetMs
        avPlayer.seekToTime(CMTimeMakeWithSeconds(targetSec, preferredTimescale = 1000))
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
        // Subtitles on iOS are rendered by Compose overlay in 0ms without touching AVPlayerItem!
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
            durationMs = knownDurationMs
            livePositionMs = targetResumeMs.coerceAtLeast(0L)
        }
        pendingResumeMs = targetResumeMs.coerceAtLeast(0L)
        wantToPlay = shouldAutoPlay

        loadJob?.cancel()
        loadJob = scope.launch {
            runCatching {
                val proxyBase = proxyServer.ensureStarted()
                val localPlaylistUrl = HlsManifestRewriter.buildLocalPlaylistUrl(proxyBase, m3u8Url)
                val nsUrl = NSURL.URLWithString(localPlaylistUrl)
                    ?: throw IllegalArgumentException("Invalid local HLS URL")
                val item = AVPlayerItem.playerItemWithURL(nsUrl)
                avPlayer.replaceCurrentItemWithPlayerItem(item)
                if (shouldAutoPlay) {
                    avPlayer.play()
                }
            }.onFailure { err ->
                logError("IosAvPlayerController", "Failed to start HLS stream", err)
                isBuffering = false
                playbackError = "Không thể khởi tạo trình phát iOS (${err.message ?: "lỗi mạng"})."
            }
        }
    }

    fun markPlaybackEnded() {
        isPlaying = false
        isBuffering = false
        wantToPlay = false
        isPlaybackEnded = true
    }

    fun syncStateTick() {
        val item = avPlayer.currentItem
        val status = avPlayer.timeControlStatus

        when (status) {
            AVPlayerTimeControlStatusPlaying -> {
                isPlaying = true
                isBuffering = false
            }
            AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> {
                isPlaying = true
                isBuffering = true
            }
            AVPlayerTimeControlStatusPaused -> {
                isPlaying = false
                isBuffering = (item != null && item.status == AVPlayerItemStatusUnknown && wantToPlay)
            }
        }

        if (item != null) {
            when (item.status) {
                AVPlayerItemStatusReadyToPlay -> {
                    val durSec = CMTimeGetSeconds(item.duration)
                    if (!durSec.isNaN() && durSec.isFinite() && durSec > 0.0) {
                        durationMs = (durSec * 1000.0).toLong()
                    }
                    if (pendingResumeMs > 0L) {
                        val seekTarget = pendingResumeMs
                        pendingResumeMs = 0L
                        seekTo(seekTarget)
                        if (wantToPlay) {
                            avPlayer.play()
                        }
                    }
                }
                AVPlayerItemStatusFailed -> {
                    isBuffering = false
                    isPlaying = false
                    wantToPlay = false
                    val reason = item.error?.localizedDescription ?: "Stream error"
                    playbackError = "Lỗi luồng phát video ($reason). Bạn có thể thử lại hoặc đổi Server khác."
                }
            }

            val curSec = CMTimeGetSeconds(item.currentTime())
            if (!curSec.isNaN() && curSec.isFinite() && curSec >= 0.0) {
                livePositionMs = (curSec * 1000.0).toLong()
            }

            if (!isPlaybackEnded &&
                status == AVPlayerTimeControlStatusPaused &&
                durationMs > 10_000L &&
                livePositionMs >= durationMs - 600L
            ) {
                markPlaybackEnded()
            }
        }
    }

    fun release() {
        loadJob?.cancel()
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        proxyServer.stop()
    }
}

@Composable
actual fun rememberPlatformPlayerController(
    repository: PhimBudRepository
): PlatformPlayerController {
    val scope = rememberCoroutineScope()
    val controller = remember(repository) {
        val avPlayer = AVPlayer()
        val proxy = IosHlsProxyServer(repository.apiClient.httpClient)
        IosAvPlayerController(
            avPlayer = avPlayer,
            proxyServer = proxy,
            scope = scope
        )
    }

    // Poll AVPlayer status & time 5x/sec without Objective-C KVO retain-cycle/dealloc hazards
    LaunchedEffect(controller) {
        while (isActive) {
            controller.syncStateTick()
            delay(200L)
        }
    }

    // Pause video when app enters background or iPhone screen is locked, and observe end of item
    DisposableEffect(controller) {
        val center = NSNotificationCenter.defaultCenter
        val bgObserver = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            controller.pause()
        }
        val resignObserver = center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            controller.pause()
        }
        val endObserver = center.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            val currentItem = controller.avPlayer.currentItem
            if (currentItem != null && notification?.`object` == currentItem) {
                controller.markPlaybackEnded()
            }
        }

        onDispose {
            center.removeObserver(bgObserver)
            center.removeObserver(resignObserver)
            center.removeObserver(endObserver)
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
    DisposableEffect(Unit) {
        // 1. Enable audio playback even when iPhone hardware Silent switch is muted
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        }

        // 2. Keep iPhone OLED screen awake during video playback
        UIApplication.sharedApplication.idleTimerDisabled = true

        // 3. Request Landscape orientation
        requestIosInterfaceOrientation(landscape = true)

        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
            requestIosInterfaceOrientation(landscape = false)
        }
    }
}

private fun requestIosInterfaceOrientation(landscape: Boolean) {
    runCatching {
        val majorVersion = UIDevice.currentDevice.systemVersion
            .substringBefore('.')
            .toIntOrNull() ?: 16

        if (majorVersion >= 16) {
            val scenes = UIApplication.sharedApplication.connectedScenes
            val windowScene = scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
            if (windowScene != null) {
                val mask = if (landscape) {
                    UIInterfaceOrientationMaskLandscape
                } else {
                    UIInterfaceOrientationMaskAllButUpsideDown
                }
                val prefs = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask)
                windowScene.requestGeometryUpdateWithPreferences(prefs, errorHandler = null)
            }
        }
        val targetOrientation = if (landscape) {
            UIInterfaceOrientationLandscapeRight
        } else {
            UIInterfaceOrientationPortrait
        }
        UIDevice.currentDevice.setValue(
            NSNumber(integer = targetOrientation),
            forKey = "orientation"
        )
        UIViewController.attemptRotationToDeviceOrientation()
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
    val iosController = controller as IosAvPlayerController
    val gravity = when (resizeMode) {
        VideoResizeMode.FIT -> AVLayerVideoGravityResizeAspect
        VideoResizeMode.ZOOM -> AVLayerVideoGravityResizeAspectFill
        VideoResizeMode.FILL -> AVLayerVideoGravityResize
    }

    val playerViewController = remember(iosController) {
        AVPlayerViewController().apply {
            player = iosController.avPlayer
            showsPlaybackControls = false
            view.backgroundColor = UIColor.blackColor
            view.userInteractionEnabled = false
        }
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

    Box(modifier = modifier) {
        UIKitView(
            factory = {
                playerViewController.videoGravity = gravity
                playerViewController.view
            },
            update = { _ ->
                playerViewController.videoGravity = gravity
            },
            modifier = Modifier.fillMaxSize()
        )

        if (parsedCues.isNotEmpty()) {
            IosSubtitleOverlay(
                iosController = iosController,
                parsedCues = parsedCues,
                controlsVisible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private val IosSubtitleTextStyle = TextStyle(
    shadow = Shadow(
        color = Color.Black,
        offset = Offset(1.5f, 1.5f),
        blurRadius = 4f
    )
)

@Composable
private fun IosSubtitleOverlay(
    iosController: IosAvPlayerController,
    parsedCues: List<SubtitleCue>,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val activeCueText by remember(iosController, parsedCues) {
        derivedStateOf {
            SrtParser.findActiveText(parsedCues, iosController.livePositionMs)
        }
    }

    if (!activeCueText.isNullOrBlank()) {
        val bottomPad = if (controlsVisible) 88.dp else 26.dp
        Box(
            modifier = modifier
                .padding(start = 32.dp, end = 32.dp, bottom = bottomPad)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = activeCueText.orEmpty(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                style = IosSubtitleTextStyle
            )
        }
    }
}
