package com.phimbud.mobile.core.repository

import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.HomeFeed
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.model.SearchItem
import com.phimbud.mobile.core.model.SearchNormalizer
import com.phimbud.mobile.core.model.Subtitle
import com.phimbud.mobile.core.model.TitleDetail
import com.phimbud.mobile.core.model.TtlLruCache
import com.phimbud.mobile.core.model.UserSession
import com.phimbud.mobile.core.model.WatchInfo
import com.phimbud.mobile.core.network.PhimBudApiClient
import com.phimbud.mobile.core.network.SessionExpiredException
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

class PhimBudRepository(val platformContext: PlatformContext) {

    companion object {
        private const val DETAIL_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    val apiClient = PhimBudApiClient(platformContext)

    private val _isLoggedIn = MutableStateFlow(apiClient.hasSavedSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userSession = MutableStateFlow(UserSession())
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    private val _homeFeed = MutableStateFlow(HomeFeed())
    val homeFeed: StateFlow<HomeFeed> = _homeFeed.asStateFlow()

    @Volatile
    private var cachedSearchIndex: List<SearchItem> = emptyList()
    private val searchIndexMutex = Mutex()
    private val homeFeedMutex = Mutex()

    private val detailCache = TtlLruCache<String, TitleDetail>(maxSize = 20, ttlMs = DETAIL_CACHE_TTL_MS)
    private val episodesCache = TtlLruCache<String, List<Episode>>(maxSize = 30, ttlMs = DETAIL_CACHE_TTL_MS)

    fun hasHomeFeedData(): Boolean {
        val f = _homeFeed.value
        return f.trending.isNotEmpty() || f.movies.isNotEmpty() || f.shows.isNotEmpty() || f.updated.isNotEmpty()
    }

    fun hasSearchIndex(): Boolean = cachedSearchIndex.isNotEmpty()

    fun isInWishlist(fid: String): Boolean = _homeFeed.value.wishlist.any { it.fid == fid }

    suspend fun login(email: String, pass: String): Result<Boolean> {
        val res = apiClient.login(email, pass)
        if (res.isSuccess) {
            _isLoggedIn.value = true
        }
        return res
    }

    fun logout() {
        apiClient.logout()
        detailCache.evictAll()
        episodesCache.evictAll()
        cachedSearchIndex = emptyList()
        _isLoggedIn.value = false
        _userSession.value = UserSession()
        _homeFeed.value = HomeFeed()
    }

    suspend fun loadHomeFeed(): Result<HomeFeed> = homeFeedMutex.withLock {
        val res = apiClient.getHomeFeed()
        res.onFailure { err ->
            if (err is SessionExpiredException) {
                logout()
            }
        }
        return res.map { (user, feed) ->
            _userSession.value = user
            _homeFeed.value = feed
            feed
        }
    }

    suspend fun getTitleDetail(fid: String, forceRefresh: Boolean = false): Result<TitleDetail> {
        if (!forceRefresh) {
            detailCache.get(fid)?.let { cached ->
                return Result.success(cached)
            }
        }
        val res = apiClient.getTitleDetail(fid)
        res.onFailure { err ->
            if (err is SessionExpiredException) logout()
        }
        res.onSuccess { detail ->
            detailCache.put(fid, detail)
        }
        return res
    }

    suspend fun getSeasonEpisodes(seasonFid: String, forceRefresh: Boolean = false): Result<List<Episode>> {
        if (!forceRefresh) {
            episodesCache.get(seasonFid)?.let { cached ->
                return Result.success(cached)
            }
        }
        val res = apiClient.getSeasonEpisodes(seasonFid)
        res.onSuccess { episodes ->
            episodesCache.put(seasonFid, episodes)
        }
        return res
    }

    suspend fun resolvePlayFid(fid: String): Result<String> {
        return apiClient.resolvePlayFid(fid)
    }

    suspend fun getWatchInfo(watchFid: String): Result<WatchInfo> {
        return apiClient.getWatchInfo(watchFid).onFailure { err ->
            if (err is SessionExpiredException) logout()
        }
    }

    suspend fun getVideoSrc(watchFid: String, server: String): Result<String> {
        return apiClient.getVideoSrc(watchFid, server)
    }

    suspend fun getSubtitles(watchFid: String): Result<List<Subtitle>> {
        return apiClient.getSubtitles(watchFid)
    }

    suspend fun downloadSubtitle(sub: Subtitle, watchFid: String = ""): Result<CachedSubtitle> {
        return apiClient.downloadDecryptedSubtitle(sub, watchFid)
    }

    suspend fun searchTitles(query: String): List<SearchItem> {
        if (cachedSearchIndex.isEmpty()) {
            searchIndexMutex.withLock {
                if (cachedSearchIndex.isEmpty()) {
                    val res = apiClient.getAllTitlesIndex()
                    if (res.isSuccess) {
                        cachedSearchIndex = res.getOrDefault(emptyList())
                    }
                }
            }
        }

        val snapshot = cachedSearchIndex
        if (snapshot.isEmpty()) return emptyList()
        if (query.isBlank()) return snapshot.take(30)

        return withContext(Dispatchers.Default) {
            val normalizedQuery = SearchNormalizer.normalize(query)
            val trimmedRaw = query.trim()
            snapshot.asSequence()
                .filter { item ->
                    (normalizedQuery.isNotEmpty() && (
                        item.normalizedNameVi.contains(normalizedQuery) ||
                            item.normalizedNameEn.contains(normalizedQuery)
                        )) || (item.imdbId != null && item.imdbId.contains(trimmedRaw, ignoreCase = true))
                }
                .take(50)
                .toList()
        }
    }

    suspend fun getBrowsePage(page: Int): Result<List<Movie>> {
        return apiClient.getBrowsePage(page)
    }

    suspend fun syncPlaytime(fid: String, seconds: Int) {
        apiClient.setPlaytime(fid, seconds)
    }

    suspend fun updateCollection(fid: String, status: String, movieSnapshot: Movie? = null) {
        val currentFeed = _homeFeed.value
        val updatedWishlist = if (status == "wishlist") {
            if (currentFeed.wishlist.any { it.fid == fid } || movieSnapshot == null) {
                currentFeed.wishlist
            } else {
                listOf(movieSnapshot) + currentFeed.wishlist
            }
        } else {
            currentFeed.wishlist.filterNot { it.fid == fid }
        }
        _homeFeed.value = currentFeed.copy(wishlist = updatedWishlist)
        apiClient.updateCollection(fid, status)
    }
}
