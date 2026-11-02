package com.phimbud.mobile.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(private val repository: PhimBudRepository) : ViewModel() {

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentPage = 1
    private var hasMore = true

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        if (_isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.getBrowsePage(currentPage)
            if (res.isSuccess) {
                val newItems = res.getOrDefault(emptyList())
                if (newItems.isEmpty()) {
                    hasMore = false
                } else {
                    _movies.value = (_movies.value + newItems).distinctBy { it.fid }
                    currentPage++
                }
            } else {
                hasMore = false
            }
            _isLoading.value = false
        }
    }
}
