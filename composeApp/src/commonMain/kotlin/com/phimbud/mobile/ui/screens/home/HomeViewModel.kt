package com.phimbud.mobile.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.HomeFeed
import com.phimbud.mobile.core.model.HomeFilterMode
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.model.Top10CategoryFilter
import com.phimbud.mobile.core.model.Top10Lists
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

    private val _uiState = MutableStateFlow<HomeUiState>(
        if (repository.hasHomeFeedData()) {
            HomeUiState.Success(repository.homeFeed.value)
        } else {
            HomeUiState.Loading
        }
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _filterMode = MutableStateFlow(HomeFilterMode.ALL)
    val filterMode: StateFlow<HomeFilterMode> = _filterMode.asStateFlow()

    private val _top10Category = MutableStateFlow(Top10CategoryFilter.ALL)
    val top10Category: StateFlow<Top10CategoryFilter> = _top10Category.asStateFlow()

    val top10Lists: StateFlow<Top10Lists> = repository.top10Lists

    private val _isTop10Loading = MutableStateFlow(false)
    val isTop10Loading: StateFlow<Boolean> = _isTop10Loading.asStateFlow()

    init {
        viewModelScope.launch {
            repository.homeFeed.collect { feed ->
                if (repository.hasHomeFeedData()) {
                    _uiState.value = HomeUiState.Success(feed)
                }
            }
        }
        if (!repository.hasHomeFeedData()) {
            loadHomeFeed()
        }
        loadTop10(forceRefresh = false)
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            if (!repository.hasHomeFeedData()) {
                _uiState.value = HomeUiState.Loading
            }
            val res = repository.loadHomeFeed()
            if (res.isSuccess) {
                _uiState.value = HomeUiState.Success(res.getOrThrow())
            } else if (!repository.hasHomeFeedData()) {
                _uiState.value = HomeUiState.Error(
                    res.exceptionOrNull()?.message ?: "Không thể tải dữ liệu trang chủ"
                )
            }
        }
    }

    fun loadTop10(forceRefresh: Boolean = false) {
        if (_isTop10Loading.value) return
        viewModelScope.launch {
            _isTop10Loading.value = true
            try {
                repository.loadTop10Lists(forceRefresh = forceRefresh)
            } finally {
                _isTop10Loading.value = false
            }
        }
    }

    fun selectFilterMode(mode: HomeFilterMode) {
        _filterMode.value = mode
        if (mode != HomeFilterMode.ALL && !top10Lists.value.hasAnyData && !_isTop10Loading.value) {
            loadTop10(forceRefresh = true)
        }
    }

    fun selectTop10Category(category: Top10CategoryFilter) {
        _top10Category.value = category
    }

    private var isResolvingPlay = false

    fun playMovie(movie: Movie, onPlayWatchFid: (String) -> Unit) {
        if (isResolvingPlay) return
        viewModelScope.launch {
            isResolvingPlay = true
            try {
                val res = repository.resolvePlayFid(movie.fid)
                val watchFid = res.getOrDefault(movie.fid)
                onPlayWatchFid(watchFid)
            } finally {
                isResolvingPlay = false
            }
        }
    }
}
