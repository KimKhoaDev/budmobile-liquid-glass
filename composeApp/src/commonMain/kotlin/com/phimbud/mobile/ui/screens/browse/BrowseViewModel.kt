package com.phimbud.mobile.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.BrowseFilter
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(private val repository: PhimBudRepository) : ViewModel() {

    private val _filter = MutableStateFlow(BrowseFilter())
    val filter: StateFlow<BrowseFilter> = _filter.asStateFlow()

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _scrollResetTrigger = MutableStateFlow(0)
    val scrollResetTrigger: StateFlow<Int> = _scrollResetTrigger.asStateFlow()

    private var nextPage: Int? = 1
    private var requestGeneration = 0L
    private var fetchJob: Job? = null

    init {
        loadNextPage()
    }

    fun updateFilter(newFilter: BrowseFilter) {
        if (_filter.value == newFilter && (_movies.value.isNotEmpty() || _isLoading.value)) {
            return
        }
        fetchJob?.cancel()
        val generation = ++requestGeneration
        _filter.value = newFilter
        nextPage = 1
        _movies.value = emptyList()
        _errorMessage.value = null
        _isLoading.value = false
        _scrollResetTrigger.value++
        loadPageInternal(page = 1, filterSnapshot = newFilter, generation = generation)
    }

    fun updateType(type: String?) {
        val adjustedDuration = if (type == "show") null else _filter.value.duration
        updateFilter(_filter.value.copy(type = type, duration = adjustedDuration))
    }

    fun updateGenre(genre: String?) {
        updateFilter(_filter.value.copy(genre = genre))
    }

    fun updateCountry(country: String?) {
        updateFilter(_filter.value.copy(country = country))
    }

    fun updateYear(year: String?) {
        updateFilter(_filter.value.copy(year = year))
    }

    fun updateDuration(duration: String?) {
        updateFilter(_filter.value.copy(duration = duration))
    }

    fun updateSort(sort: String?) {
        updateFilter(_filter.value.copy(sort = sort))
    }

    fun resetFilter() {
        updateFilter(BrowseFilter())
    }

    fun retry() {
        fetchJob?.cancel()
        val generation = ++requestGeneration
        val pageToLoad = nextPage ?: 1
        _errorMessage.value = null
        _isLoading.value = false
        loadPageInternal(page = pageToLoad, filterSnapshot = _filter.value, generation = generation)
    }

    fun loadNextPage() {
        val pageToLoad = nextPage ?: return
        if (_isLoading.value) return
        if (_errorMessage.value != null && _movies.value.isEmpty()) return
        loadPageInternal(page = pageToLoad, filterSnapshot = _filter.value, generation = requestGeneration)
    }

    private fun loadPageInternal(page: Int, filterSnapshot: BrowseFilter, generation: Long) {
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            if (page == 1) {
                _errorMessage.value = null
            }
            try {
                val res = repository.getBrowsePage(page, filterSnapshot)
                if (generation != requestGeneration) return@launch

                res.fold(
                    onSuccess = { pageResult ->
                        val incoming = pageResult.titles
                        if (page == 1) {
                            _movies.value = incoming.distinctBy { it.fid }
                        } else if (incoming.isNotEmpty()) {
                            _movies.value = (_movies.value + incoming).distinctBy { it.fid }
                        }
                        nextPage = if (incoming.isEmpty()) null else pageResult.nextPage
                    },
                    onFailure = { err ->
                        if (page == 1) {
                            _errorMessage.value = err.message ?: "Không thể tải danh sách phim"
                        } else {
                            nextPage = null
                        }
                    }
                )
            } finally {
                if (generation == requestGeneration) {
                    _isLoading.value = false
                }
            }
        }
    }
}
