package com.phimbud.mobile.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.SearchItem
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: PhimBudRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchItem>>(emptyList())
    val results: StateFlow<List<SearchItem>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null

    init {
        searchJob = viewModelScope.launch {
            executeSearch("")
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(if (repository.hasSearchIndex()) 80L else 180L)
            executeSearch(newQuery)
        }
    }

    private suspend fun executeSearch(q: String) {
        val showSpinner = !repository.hasSearchIndex()
        if (showSpinner) {
            _isLoading.value = true
        }
        try {
            val items = repository.searchTitles(q)
            _results.value = items
        } finally {
            if (_isLoading.value) {
                _isLoading.value = false
            }
        }
    }
}
