package com.phimbud.mobile.ui.screens.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phimbud.mobile.core.model.UserSession
import com.phimbud.mobile.core.platform.currentTimeMillis
import com.phimbud.mobile.core.repository.PhimBudRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyListViewModel(private val repository: PhimBudRepository) : ViewModel() {

    val userSession: StateFlow<UserSession> = repository.userSession
    val homeFeed = repository.homeFeed

    private val _selectedTab = MutableStateFlow(0) // 0: Wishlist, 1: Watching
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private var lastRefreshMs = 0L

    fun refresh(force: Boolean = false) {
        val now = currentTimeMillis()
        if (!force && lastRefreshMs > 0L && (now - lastRefreshMs) in 0L until 15_000L) {
            return
        }
        lastRefreshMs = now
        viewModelScope.launch {
            repository.loadCollection()
        }
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun logout() {
        repository.logout()
    }
}
