package com.phimbud.mobile.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.Season
import com.phimbud.mobile.core.model.TitleDetail
import com.phimbud.mobile.core.repository.PhimBudRepository
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

                _uiState.value = DetailUiState.Success(
                    detail = detail,
                    episodes = episodes,
                    selectedSeason = firstSeason,
                    isInWishlist = false
                )
            } else {
                _uiState.value = DetailUiState.Error(
                    res.exceptionOrNull()?.message ?: "Không thể tải thông tin phim"
                )
            }
        }
    }

    fun selectSeason(season: Season) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            val episodesRes = repository.getSeasonEpisodes(season.fid)
            _uiState.value = current.copy(
                selectedSeason = season,
                episodes = episodesRes.getOrDefault(emptyList())
            )
        }
    }

    fun playFast(onPlayWatchFid: (String) -> Unit) {
        viewModelScope.launch {
            val current = _uiState.value as? DetailUiState.Success
            // If episodes are loaded, play first episode or resolvePlayFid
            val firstEpFid = current?.episodes?.firstOrNull()?.fid
            if (firstEpFid != null) {
                onPlayWatchFid(firstEpFid)
            } else {
                val res = repository.resolvePlayFid(fid)
                onPlayWatchFid(res.getOrDefault(fid))
            }
        }
    }

    fun toggleWishlist() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            val nextState = !current.isInWishlist
            _uiState.value = current.copy(isInWishlist = nextState)
            repository.updateCollection(fid, if (nextState) "wishlist" else "")
        }
    }
}
