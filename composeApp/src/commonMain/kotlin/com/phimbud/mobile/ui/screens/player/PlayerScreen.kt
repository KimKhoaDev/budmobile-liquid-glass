package com.phimbud.mobile.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.core.platform.formatPlaybackTime
import com.phimbud.mobile.player.PlatformPlayerController
import com.phimbud.mobile.player.PlatformPlayerWindowEffect
import com.phimbud.mobile.player.PlatformVideoSurface
import com.phimbud.mobile.player.VideoResizeMode
import com.phimbud.mobile.player.rememberPlatformPlayerController
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixRed
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val controller = rememberPlatformPlayerController(viewModel.repository)

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

    PlatformPlayerWindowEffect(
        showSubtitleDialog = showSubtitleDialog,
        showServerDialog = showServerDialog,
        controlsVisible = controlsVisible
    )

    val isInPipMode = controller.isInPipMode
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            controlsVisible = false
        }
    }

    // Start / stop 20s progress sync with server
    DisposableEffect(controller) {
        viewModel.startProgressSync { controller.currentPositionMs() / 1000L }
        onDispose {
            viewModel.stopProgressSync(controller.currentPositionMs() / 1000L)
        }
    }

    // Auto-hide HUD timer (pauses while dialogs are open)
    LaunchedEffect(
        controlsVisible,
        controller.isPlaying,
        isLocked,
        showSubtitleDialog,
        showServerDialog,
        controlsInteractionTick
    ) {
        if (controlsVisible && !showSubtitleDialog && !showServerDialog && (controller.isPlaying || isLocked)) {
            delay(if (isLocked) 2800L else 4000L)
            controlsVisible = false
        }
    }

    // Load or update HLS stream & subtitle when Ready state changes
    LaunchedEffect(uiState) {
        val ready = uiState as? PlayerUiState.Ready ?: return@LaunchedEffect
        val knownDurationMs = (ready.watchInfo.durationSeconds * 1000.0).toLong().coerceAtLeast(0L)
        val resumePosMs = ready.watchInfo.lastCurrentTime?.times(1000L) ?: 0L
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

    val readyState = uiState as? PlayerUiState.Ready

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
        // Native Video Surface (ExoPlayer on Android, AVPlayer + Subtitle Overlay on iOS)
        PlatformVideoSurface(
            controller = controller,
            resizeMode = resizeMode,
            activeSubtitle = readyState?.activeSubtitleData,
            controlsVisible = controlsVisible,
            modifier = Modifier.fillMaxSize()
        )

        // Loading / Buffering indicator
        if (uiState is PlayerUiState.Loading || (uiState is PlayerUiState.Ready && controller.isBuffering)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(50.dp))
            }
        }

        // Error state overlay (handles both API errors and HLS playback errors)
        val playbackErr = controller.playbackError
        val activeErrorMessage = (uiState as? PlayerUiState.Error)?.message ?: playbackErr
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
            visible = controlsVisible && !isInPipMode && activeErrorMessage == null,
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

                    val knownDurMs = readyState?.watchInfo?.durationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L) ?: 0L
                    PlayerBottomBar(
                        controller = controller,
                        isPlaying = controller.isPlaying,
                        controlsVisible = controlsVisible,
                        knownDuration = knownDurMs.coerceAtLeast(controller.durationMs),
                        nextTitleFid = readyState?.watchInfo?.nextTitleFid,
                        onScrubActivity = { controlsInteractionTick++ },
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

        // Subtitle Picker Dialog
        if (showSubtitleDialog && !isInPipMode) {
            val subtitles = readyState?.subtitles ?: emptyList()

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
private fun PlayerBottomBar(
    controller: PlatformPlayerController,
    isPlaying: Boolean,
    controlsVisible: Boolean,
    knownDuration: Long,
    nextTitleFid: String?,
    onScrubActivity: () -> Unit,
    onNextEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPosition by remember { mutableLongStateOf(controller.currentPositionMs().coerceAtLeast(0L)) }
    var duration by remember(knownDuration) {
        mutableLongStateOf(knownDuration.coerceAtLeast(controller.durationMs.coerceAtLeast(0L)))
    }
    var isScrubbing by remember { mutableStateOf(false) }

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
