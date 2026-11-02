package com.phimbud.mobile.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.phimbud.mobile.player.PhimBudPlayerManager
import com.phimbud.mobile.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Force landscape in player
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val origOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity?.requestedOrientation = origOrientation
        }
    }

    val playerManager = remember { PhimBudPlayerManager(context) }
    val exoPlayer = remember { playerManager.createPlayer() }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

    // Start progress sync
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
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

    // Auto update seekbar position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Controls auto-hide timer
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Prepare player when state is Ready
    LaunchedEffect(uiState) {
        val ready = uiState as? PlayerUiState.Ready ?: return@LaunchedEffect
        val mediaSource = playerManager.buildMediaSource(ready.m3u8Url, ready.activeSubtitleFile)
        val resumePos = ready.watchInfo.lastCurrentTime?.times(1000L) ?: 0L

        exoPlayer.setMediaSource(mediaSource)
        if (resumePos > 0 && exoPlayer.currentPosition == 0L) {
            exoPlayer.seekTo(resumePos)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isLocked) {
                    controlsVisible = !controlsVisible
                } else {
                    // Tap when locked reveals lock button briefly
                    controlsVisible = true
                }
            }
    ) {
        // ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (uiState is PlayerUiState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(50.dp))
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                if (isLocked) {
                    // Only unlock button when locked
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(54.dp)
                            .background(NetflixCard.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Mở khóa màn hình",
                            tint = NetflixRed,
                            modifier = Modifier.size(28.dp)
                        )
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
                                    imageVector = Icons.Default.ArrowBack,
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
                                    text = "Server ${readyState?.currentServer ?: "1"}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Subtitles Button
                            IconButton(onClick = { showSubtitleDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Phụ đề",
                                    tint = Color.White
                                )
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
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
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
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration)) },
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

                    // Bottom Bar (Seekbar, Time, Next Episode)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        // Time labels & Next Episode
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

                            if (!readyState?.watchInfo?.nextTitleFid.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        viewModel.loadStream(readyState!!.watchInfo.nextTitleFid!!)
                                    }
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

                        // Slider
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { frac ->
                                currentPosition = (frac * duration).toLong()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(currentPosition)
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
            }
        }

        // Subtitle Picker Dialog
        if (showSubtitleDialog) {
            val ready = uiState as? PlayerUiState.Ready
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text("Chọn Phụ đề", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        // Option: Tắt phụ đề
                        TextButton(
                            onClick = {
                                viewModel.selectSubtitle(null)
                                showSubtitleDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tắt phụ đề",
                                color = if (ready?.activeSubtitle == null) NetflixRed else Color.White
                            )
                        }

                        ready?.subtitles?.forEach { sub ->
                            TextButton(
                                onClick = {
                                    viewModel.selectSubtitle(sub)
                                    showSubtitleDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = sub.label + if (sub.releaseName.isNotBlank()) " (${sub.releaseName})" else "",
                                    color = if (ready.activeSubtitle?.id == sub.id) NetflixRed else Color.White
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("Đóng", color = NetflixRed)
                    }
                },
                containerColor = NetflixDarkGray
            )
        }

        // Server Picker Dialog
        if (showServerDialog) {
            val ready = uiState as? PlayerUiState.Ready
            AlertDialog(
                onDismissRequest = { showServerDialog = false },
                title = { Text("Chọn Server phát", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf("1", "2", "3").forEach { srv ->
                            TextButton(
                                onClick = {
                                    viewModel.switchServer(srv)
                                    showServerDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Server $srv" + if (srv != "1") " (VIP)" else " (Mặc định)",
                                    color = if (ready?.currentServer == srv) NetflixRed else Color.White
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showServerDialog = false }) {
                        Text("Đóng", color = NetflixRed)
                    }
                },
                containerColor = NetflixDarkGray
            )
        }
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
