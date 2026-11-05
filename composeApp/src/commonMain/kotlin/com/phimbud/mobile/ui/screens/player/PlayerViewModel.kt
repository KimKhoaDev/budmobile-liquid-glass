package com.phimbud.mobile.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.Season
import com.phimbud.mobile.core.model.Subtitle
import com.phimbud.mobile.core.model.TitleDetail
import com.phimbud.mobile.core.model.WatchInfo
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.repository.PhimBudRepository
import com.phimbud.mobile.player.IntroRecapDetector
import com.phimbud.mobile.player.SkipSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import kotlinx.coroutines.withContext

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Ready(
        val watchInfo: WatchInfo,
        val m3u8Url: String,
        val currentServer: String,
        val subtitles: List<Subtitle>,
        val activeSubtitle: Subtitle?,
        val activeSubtitleData: CachedSubtitle?,
        val skipSegments: List<SkipSegment> = emptyList(),
        val seasons: List<Season> = emptyList(),
        val selectedSeason: Season? = null,
        val episodes: List<Episode> = emptyList(),
        val isEpisodesLoading: Boolean = false
    ) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

class PlayerViewModel(
    val repository: PhimBudRepository,
    var watchFid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var episodesJob: Job? = null
    private var syncJob: Job? = null
    var currentServer: String = "1"

    init {
        loadStream(watchFid, currentServer)
    }

    fun loadStream(fid: String, server: String = currentServer) {
        val previousReady = _uiState.value as? PlayerUiState.Ready
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

                val subs = rawSubs.sortedWith(
                    compareByDescending<Subtitle> { it.language.startsWith("vi", ignoreCase = true) }
                        .thenByDescending { it.isDefault }
                        .thenBy { it.label }
                )

                val defaultSub = subs.firstOrNull { it.language.startsWith("vi", ignoreCase = true) }
                    ?: subs.firstOrNull { it.isDefault }
                    ?: subs.firstOrNull()

                var subData: CachedSubtitle? = null
                var detectedSkipSegments: List<SkipSegment> = emptyList()
                val isEpisode = watchInfo.showFid.isNotBlank() || watchInfo.seasonFid.isNotBlank() || watchInfo.nextTitleFid != null
                if (defaultSub != null) {
                    subData = repository.downloadSubtitle(defaultSub, fid).getOrNull()
                    if (subData != null) {
                        detectedSkipSegments = withContext(Dispatchers.Default) {
                            IntroRecapDetector.detectFromSrt(subData.content, isEpisode = isEpisode)
                        }
                    }
                }

                // Reuse already-loaded seasons/episodes if switching episodes within the same show/season
                val sameShow = previousReady != null &&
                    watchInfo.showFid.isNotBlank() &&
                    previousReady.watchInfo.showFid == watchInfo.showFid
                val sameSeason = sameShow &&
                    watchInfo.seasonFid.isNotBlank() &&
                    previousReady?.selectedSeason?.fid == watchInfo.seasonFid &&
                    previousReady.episodes.isNotEmpty()

                val initialSeasons = if (sameShow) previousReady?.seasons.orEmpty() else emptyList()
                val initialSelectedSeason = if (sameSeason) {
                    previousReady?.selectedSeason
                } else if (watchInfo.seasonFid.isNotBlank()) {
                    initialSeasons.firstOrNull { it.fid == watchInfo.seasonFid }
                        ?: Season(fid = watchInfo.seasonFid, number = watchInfo.seasonNumber)
                } else {
                    null
                }
                val initialEpisodes = if (sameSeason) previousReady?.episodes.orEmpty() else emptyList()

                _uiState.value = PlayerUiState.Ready(
                    watchInfo = watchInfo,
                    m3u8Url = m3u8Url,
                    currentServer = server,
                    subtitles = subs,
                    activeSubtitle = defaultSub,
                    activeSubtitleData = subData,
                    skipSegments = detectedSkipSegments,
                    seasons = initialSeasons,
                    selectedSeason = initialSelectedSeason,
                    episodes = initialEpisodes,
                    isEpisodesLoading = isEpisode && initialEpisodes.isEmpty()
                )

                if (isEpisode && !sameSeason) {
                    loadShowSeasonsAndEpisodes(watchInfo)
                }
            }
        }
    }

    private fun loadShowSeasonsAndEpisodes(watchInfo: WatchInfo) {
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            coroutineScope {
                val detailDeferred: Deferred<TitleDetail?>? = if (watchInfo.showFid.isNotBlank()) {
                    async { repository.getTitleDetail(watchInfo.showFid).getOrNull() }
                } else {
                    null
                }
                val directEpisodesDeferred: Deferred<List<Episode>>? = if (watchInfo.seasonFid.isNotBlank()) {
                    async { repository.getSeasonEpisodes(watchInfo.seasonFid).getOrDefault(emptyList()) }
                } else {
                    null
                }

                val detail = detailDeferred?.await()
                val resolvedSeasons = detail?.seasons?.takeIf { it.isNotEmpty() }
                    ?: if (watchInfo.seasonFid.isNotBlank()) {
                        listOf(Season(fid = watchInfo.seasonFid, number = watchInfo.seasonNumber))
                    } else {
                        emptyList()
                    }

                val targetSeason = resolvedSeasons.firstOrNull { it.fid == watchInfo.seasonFid }
                    ?: resolvedSeasons.firstOrNull { it.number == watchInfo.seasonNumber }
                    ?: resolvedSeasons.firstOrNull()

                val resolvedEpisodes = when {
                    directEpisodesDeferred != null && (targetSeason == null || targetSeason.fid == watchInfo.seasonFid) -> {
                        directEpisodesDeferred.await()
                    }
                    targetSeason != null -> {
                        repository.getSeasonEpisodes(targetSeason.fid).getOrDefault(emptyList())
                    }
                    else -> emptyList()
                }

                val current = _uiState.value as? PlayerUiState.Ready ?: return@coroutineScope
                if (current.watchInfo.fid == watchInfo.fid) {
                    _uiState.value = current.copy(
                        seasons = resolvedSeasons,
                        selectedSeason = targetSeason,
                        episodes = resolvedEpisodes,
                        isEpisodesLoading = false
                    )
                }
            }
        }
    }

    fun selectSeason(season: Season) {
        val current = _uiState.value as? PlayerUiState.Ready ?: return
        if (current.selectedSeason?.fid == season.fid && current.episodes.isNotEmpty()) return
        _uiState.value = current.copy(
            selectedSeason = season,
            isEpisodesLoading = true
        )
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            val eps = repository.getSeasonEpisodes(season.fid).getOrDefault(emptyList())
            val latest = _uiState.value as? PlayerUiState.Ready ?: return@launch
            if (latest.selectedSeason?.fid == season.fid) {
                _uiState.value = latest.copy(
                    episodes = eps,
                    isEpisodesLoading = false
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
            _uiState.value = current.copy(activeSubtitle = null, activeSubtitleData = null)
            return
        }

        val targetFid = watchFid
        viewModelScope.launch {
            val subData = repository.downloadSubtitle(subtitle, targetFid).getOrNull() ?: return@launch
            val latest = _uiState.value as? PlayerUiState.Ready ?: return@launch
            val isEpisode = latest.watchInfo.showFid.isNotBlank() || latest.watchInfo.seasonFid.isNotBlank() || latest.watchInfo.nextTitleFid != null
            val detectedSegments = withContext(Dispatchers.Default) {
                IntroRecapDetector.detectFromSrt(subData.content, isEpisode = isEpisode)
            }
            val latestAfterParse = _uiState.value as? PlayerUiState.Ready ?: return@launch
            if (watchFid == targetFid) {
                _uiState.value = latestAfterParse.copy(
                    activeSubtitle = subtitle,
                    activeSubtitleData = subData,
                    skipSegments = detectedSegments.ifEmpty { latestAfterParse.skipSegments }
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
                repository.loadHomeFeed()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        episodesJob?.cancel()
        syncJob?.cancel()
    }
}
