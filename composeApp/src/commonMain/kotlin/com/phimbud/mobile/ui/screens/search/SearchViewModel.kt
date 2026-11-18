package com.phimbud.mobile.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.SearchItem
import com.phimbud.mobile.core.platform.KeyValueStore
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

    private val store = KeyValueStore(repository.platformContext, STORE_NAME)
    private val _recent = MutableStateFlow(loadRecent())

    /** Most recent searches first, persisted across launches. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

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

    /** Remembers the current query (called on keyboard "search" and when a result is opened). */
    fun saveCurrentQuery() {
        val q = _query.value.trim()
        if (q.length < 2) return
        val next = (listOf(q) + _recent.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENT)
        _recent.value = next
        persistRecent(next)
    }

    fun removeRecent(query: String) {
        val next = _recent.value.filterNot { it == query }
        _recent.value = next
        persistRecent(next)
    }

    fun clearRecent() {
        _recent.value = emptyList()
        persistRecent(emptyList())
    }

    private fun loadRecent(): List<String> =
        store.getString(KEY_RECENT)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_RECENT)
            .orEmpty()

    private fun persistRecent(list: List<String>) {
        store.putString(KEY_RECENT, list.joinToString("\n"))
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

    private companion object {
        const val STORE_NAME = "phimbud_search"
        const val KEY_RECENT = "recent_queries"
        const val MAX_RECENT = 8
    }
}
