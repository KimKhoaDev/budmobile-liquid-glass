package com.phimbud.mobile.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.Subtitle
import com.phimbud.mobile.core.model.WatchInfo
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.Job
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

    private var syncJob: Job? = null
    var currentServer: String = "1"

    init {
        loadStream(watchFid, currentServer)
    }

    fun loadStream(fid: String, server: String = currentServer) {
        watchFid = fid
        currentServer = server

        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            val watchInfoRes = repository.getWatchInfo(fid)
            val watchInfo = watchInfoRes.getOrDefault(WatchInfo(fid = fid))

            val streamRes = repository.getVideoSrc(fid, server)
            if (streamRes.isFailure) {
                _uiState.value = PlayerUiState.Error(
                    streamRes.exceptionOrNull()?.message ?: "Không thể lấy link video"
                )
                return@launch
            }
            val m3u8Url = streamRes.getOrThrow()

            // Fetch subtitles
            val subsRes = repository.getSubtitles(fid)
            val subs = subsRes.getOrDefault(emptyList())

            // Default to Vietnamese subtitle
            val defaultSub = subs.firstOrNull { it.language.startsWith("vi") }
                ?: subs.firstOrNull { it.isDefault }
                ?: subs.firstOrNull()

            var subFile: File? = null
            if (defaultSub != null) {
                subFile = repository.downloadSubtitle(defaultSub).getOrNull()
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

    fun switchServer(server: String) {
        if (server == currentServer) return
        loadStream(watchFid, server)
    }

    fun selectSubtitle(subtitle: Subtitle?) {
        val current = _uiState.value as? PlayerUiState.Ready ?: return
        if (subtitle == null) {
            _uiState.value = current.copy(activeSubtitle = null, activeSubtitleFile = null)
            return
        }

        viewModelScope.launch {
            val fileRes = repository.downloadSubtitle(subtitle)
            val file = fileRes.getOrNull()
            _uiState.value = current.copy(
                activeSubtitle = subtitle,
                activeSubtitleFile = file
            )
        }
    }

    fun startProgressSync(getPositionSeconds: () -> Long) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (isActive) {
                delay(20000)
                val sec = getPositionSeconds().toInt()
                if (sec > 5) {
                    repository.syncPlaytime(watchFid, sec)
                }
            }
        }
    }

    fun stopProgressSync(finalPositionSeconds: Long) {
        syncJob?.cancel()
        syncJob = null
        if (finalPositionSeconds > 5) {
            viewModelScope.launch {
                repository.syncPlaytime(watchFid, finalPositionSeconds.toInt())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
    }
}
