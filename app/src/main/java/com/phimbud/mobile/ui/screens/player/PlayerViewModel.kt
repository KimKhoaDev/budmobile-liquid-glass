package com.phimbud.mobile.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.Subtitle
import com.phimbud.mobile.core.model.WatchInfo
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Ready(
        val watchInfo: WatchInfo,
        val m3u8Url: String,
        val currentServer: String,
        val subtitles: List<Subtitle>,
        val activeSubtitle: Subtitle?,
        val activeSubtitleFile: File?
    ) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

class PlayerViewModel(
    private val repository: PhimBudRepository,
    var watchFid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var syncJob: Job? = null
    var currentServer: String = "1"

    init {
        loadStream(watchFid, currentServer)
    }

    fun loadStream(fid: String, server: String = currentServer) {
        watchFid = fid
        currentServer = server

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            coroutineScope {
                val watchInfoDeferred = async { repository.getWatchInfo(fid) }
                val streamDeferred = async { repository.getVideoSrc(fid, server) }
                val subsDeferred = async { repository.getSubtitles(fid) }

                val streamRes = streamDeferred.await()
                if (streamRes.isFailure) {
                    _uiState.value = PlayerUiState.Error(
                        streamRes.exceptionOrNull()?.message ?: "Không thể lấy link video"
                    )
                    watchInfoDeferred.cancel()
                    subsDeferred.cancel()
                    return@coroutineScope
                }
                val m3u8Url = streamRes.getOrThrow()
                val watchInfo = watchInfoDeferred.await().getOrDefault(WatchInfo(fid = fid))
                val rawSubs = subsDeferred.await().getOrDefault(emptyList())

                // Always prioritize Vietnamese subtitles first, then default, then by label
                val subs = rawSubs.sortedWith(
                    compareByDescending<Subtitle> { it.language.startsWith("vi", ignoreCase = true) }
                        .thenByDescending { it.isDefault }
                        .thenBy { it.label }
                )

                // Default to Vietnamese subtitle
                val defaultSub = subs.firstOrNull { it.language.startsWith("vi", ignoreCase = true) }
                    ?: subs.firstOrNull { it.isDefault }
                    ?: subs.firstOrNull()

                var subFile: File? = null
                if (defaultSub != null) {
                    subFile = repository.downloadSubtitle(defaultSub, fid).getOrNull()
                }

                _uiState.value = PlayerUiState.Ready(
                    watchInfo = watchInfo,
                    m3u8Url = m3u8Url,
                    currentServer = server,
                    subtitles = subs,
                    activeSubtitle = defaultSub,
                    activeSubtitleFile = subFile
                )
            }
        }
    }

    fun playNextEpisode(nextFid: String, previousPositionSeconds: Long) {
        val prevFid = watchFid
        if (previousPositionSeconds > 5 && prevFid.isNotBlank() && prevFid != nextFid) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                repository.syncPlaytime(prevFid, previousPositionSeconds.toInt())
            }
        }
        loadStream(nextFid, currentServer)
    }

    fun switchServer(server: String) {
        if (server == currentServer && _uiState.value is PlayerUiState.Ready) return
        loadStream(watchFid, server)
    }

    fun selectSubtitle(subtitle: Subtitle?) {
        val current = _uiState.value as? PlayerUiState.Ready ?: return
        if (subtitle == null) {
            _uiState.value = current.copy(activeSubtitle = null, activeSubtitleFile = null)
            return
        }

        val targetFid = watchFid
        viewModelScope.launch {
            val file = repository.downloadSubtitle(subtitle, targetFid).getOrNull() ?: return@launch
            val latest = _uiState.value as? PlayerUiState.Ready ?: return@launch
            if (watchFid == targetFid) {
                _uiState.value = latest.copy(
                    activeSubtitle = subtitle,
                    activeSubtitleFile = file
                )
            }
        }
    }

    fun startProgressSync(getPositionSeconds: () -> Long) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (isActive) {
                delay(20000)
                if (_uiState.value is PlayerUiState.Ready) {
                    val sec = getPositionSeconds().toInt()
                    if (sec > 5) {
                        repository.syncPlaytime(watchFid, sec)
                    }
                }
            }
        }
    }

    fun stopProgressSync(finalPositionSeconds: Long) {
        syncJob?.cancel()
        syncJob = null
        val fidSnapshot = watchFid
        if (finalPositionSeconds > 5 && fidSnapshot.isNotBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                repository.syncPlaytime(fidSnapshot, finalPositionSeconds.toInt())
                // Refresh "Tiếp tục xem" row after updating watch progress, matching web navigation back to Home
                repository.loadHomeFeed()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
    }
}
