package com.phimbud.mobile.core.repository

import android.content.Context
import com.phimbud.mobile.core.model.*
import com.phimbud.mobile.core.network.PhimBudApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.Normalizer

class PhimBudRepository(context: Context) {

    val apiClient = PhimBudApiClient(context)

    private val _isLoggedIn = MutableStateFlow(apiClient.hasSavedSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userSession = MutableStateFlow(UserSession())
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    private val _homeFeed = MutableStateFlow(HomeFeed())
    val homeFeed: StateFlow<HomeFeed> = _homeFeed.asStateFlow()

    private var cachedSearchIndex: List<SearchItem> = emptyList()

    suspend fun login(email: String, pass: String): Result<Boolean> {
        val res = apiClient.login(email, pass)
        if (res.isSuccess) {
            _isLoggedIn.value = true
            loadHomeFeed()
        }
        return res
    }

    fun logout() {
        apiClient.logout()
        _isLoggedIn.value = false
        _userSession.value = UserSession()
        _homeFeed.value = HomeFeed()
    }

    suspend fun loadHomeFeed(): Result<HomeFeed> {
        val res = apiClient.getHomeFeed()
        return res.map { (user, feed) ->
            _userSession.value = user
            _homeFeed.value = feed
            feed
        }
    }

    suspend fun getTitleDetail(fid: String): Result<TitleDetail> {
        return apiClient.getTitleDetail(fid)
    }

    suspend fun getSeasonEpisodes(seasonFid: String): Result<List<Episode>> {
        return apiClient.getSeasonEpisodes(seasonFid)
    }

    suspend fun resolvePlayFid(fid: String): Result<String> {
        return apiClient.resolvePlayFid(fid)
    }

    suspend fun getWatchInfo(watchFid: String): Result<WatchInfo> {
        return apiClient.getWatchInfo(watchFid)
    }

    suspend fun getVideoSrc(watchFid: String, server: String): Result<String> {
        return apiClient.getVideoSrc(watchFid, server)
    }

    suspend fun getSubtitles(watchFid: String): Result<List<Subtitle>> {
        return apiClient.getSubtitles(watchFid)
    }

    suspend fun downloadSubtitle(sub: Subtitle): Result<File> {
        return apiClient.downloadDecryptedSubtitle(sub)
    }

    suspend fun searchTitles(query: String): List<SearchItem> {
        if (cachedSearchIndex.isEmpty()) {
            val res = apiClient.getAllTitlesIndex()
            if (res.isSuccess) {
                cachedSearchIndex = res.getOrDefault(emptyList())
            }
        }

        if (query.isBlank()) return cachedSearchIndex.take(30)

        val normalizedQuery = normalizeForSearch(query)
        return cachedSearchIndex.filter { item ->
            normalizeForSearch(item.nameVi).contains(normalizedQuery) ||
            normalizeForSearch(item.nameEn).contains(normalizedQuery) ||
            (item.imdbId != null && item.imdbId.contains(query, ignoreCase = true))
        }.take(50)
    }

    suspend fun getBrowsePage(page: Int): Result<List<Movie>> {
        return apiClient.getBrowsePage(page)
    }

    suspend fun syncPlaytime(fid: String, seconds: Int) {
        apiClient.setPlaytime(fid, seconds)
    }

    suspend fun updateCollection(fid: String, status: String) {
        apiClient.updateCollection(fid, status)
    }

    private fun normalizeForSearch(input: String): String {
        val nfd = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(nfd, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
    }
}
