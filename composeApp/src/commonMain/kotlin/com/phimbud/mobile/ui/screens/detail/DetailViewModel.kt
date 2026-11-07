package com.phimbud.mobile.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.model.Season
import com.phimbud.mobile.core.model.TitleDetail
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val detail: TitleDetail,
        val episodes: List<Episode>,
        val selectedSeason: Season?,
        val isInWishlist: Boolean
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class DetailViewModel(
    private val repository: PhimBudRepository,
    private val fid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var seasonJob: Job? = null

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            val res = repository.getTitleDetail(fid)
            if (res.isSuccess) {
                val detail = res.getOrThrow()
                val firstSeason = detail.seasons.firstOrNull()
                val episodes = if (firstSeason != null) {
                    repository.getSeasonEpisodes(firstSeason.fid).getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val initialWishlist = repository.isInWishlist(detail.fid) || repository.isInWishlist(fid)
                _uiState.value = DetailUiState.Success(
                    detail = detail,
                    episodes = episodes,
                    selectedSeason = firstSeason,
                    isInWishlist = initialWishlist
                )

                // Sync authoritative wishlist state with server
                viewModelScope.launch {
                    val targetFid = detail.fid.ifBlank { fid }
                    val stateRes = repository.getCollectionState(targetFid)
                    if (stateRes.isSuccess) {
                        val serverStatus = stateRes.getOrNull()
                        val isServerWishlist = serverStatus == "wishlist"
                        val latest = _uiState.value as? DetailUiState.Success ?: return@launch
                        if (latest.isInWishlist != isServerWishlist) {
                            _uiState.value = latest.copy(isInWishlist = isServerWishlist)
                        }
                    }
                }
            } else {
                _uiState.value = DetailUiState.Error(
                    res.exceptionOrNull()?.message ?: "Không thể tải thông tin phim"
                )
            }
        }
    }

    fun selectSeason(season: Season) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        if (current.selectedSeason?.fid == season.fid) return
        _uiState.value = current.copy(selectedSeason = season)
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            val episodesRes = repository.getSeasonEpisodes(season.fid)
            val latest = _uiState.value as? DetailUiState.Success ?: return@launch
            if (latest.selectedSeason?.fid == season.fid) {
                _uiState.value = latest.copy(
                    episodes = episodesRes.getOrDefault(emptyList())
                )
            }
        }
    }

    private var isResolvingPlay = false

    fun playFast(onPlayWatchFid: (String) -> Unit) {
        if (isResolvingPlay) return
        viewModelScope.launch {
            isResolvingPlay = true
            try {
                val current = _uiState.value as? DetailUiState.Success
                val firstEpFid = current?.episodes?.firstOrNull()?.fid
                val resolvedFid = repository.resolvePlayFid(fid).getOrNull()
                val targetWatchFid = when {
                    !resolvedFid.isNullOrBlank() && resolvedFid != fid -> resolvedFid
                    firstEpFid != null -> firstEpFid
                    !resolvedFid.isNullOrBlank() -> resolvedFid
                    else -> fid
                }
                onPlayWatchFid(targetWatchFid)
            } finally {
                isResolvingPlay = false
            }
        }
    }

    fun toggleWishlist() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        val detail = current.detail
        val nextState = !current.isInWishlist
        _uiState.value = current.copy(isInWishlist = nextState)
        val movieSnapshot = Movie(
            fid = detail.fid,
            nameEn = detail.nameEn,
            nameVi = detail.nameVi,
            intro = detail.intro,
            tmdbPoster = detail.tmdbPoster,
            tmdbBackdrop = detail.tmdbBackdrop,
            year = detail.year,
            imdbRating = detail.imdbRating,
            genres = detail.genres
        )
        viewModelScope.launch {
            val res = repository.addToCollection(
                fid = detail.fid.ifBlank { fid },
                status = if (nextState) "wishlist" else "",
                movieSnapshot = movieSnapshot
            )
            if (res.isFailure) {
                val latest = _uiState.value as? DetailUiState.Success ?: return@launch
                _uiState.value = latest.copy(isInWishlist = !nextState)
            }
        }
    }
}
