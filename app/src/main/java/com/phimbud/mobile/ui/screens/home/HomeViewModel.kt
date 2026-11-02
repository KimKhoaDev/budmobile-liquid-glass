package com.phimbud.mobile.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.HomeFeed
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val feed: HomeFeed) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(private val repository: PhimBudRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeFeed()
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val res = repository.loadHomeFeed()
            if (res.isSuccess) {
                _uiState.value = HomeUiState.Success(res.getOrThrow())
            } else {
                _uiState.value = HomeUiState.Error(
                    res.exceptionOrNull()?.message ?: "Không thể tải dữ liệu trang chủ"
                )
            }
        }
    }

    fun playMovie(movie: Movie, onPlayWatchFid: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.resolvePlayFid(movie.fid)
            val watchFid = res.getOrDefault(movie.fid)
            onPlayWatchFid(watchFid)
        }
    }
}
