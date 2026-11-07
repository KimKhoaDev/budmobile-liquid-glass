package com.phimbud.mobile.core.repository

import com.phimbud.mobile.core.model.BrowseFilter
import com.phimbud.mobile.core.model.BrowsePageResult
import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.HomeFeed
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.model.SearchItem
import com.phimbud.mobile.core.model.SearchNormalizer
import com.phimbud.mobile.core.model.Subtitle
import com.phimbud.mobile.core.model.TitleDetail
import com.phimbud.mobile.core.model.Top10CatalogMatcher
import com.phimbud.mobile.core.model.Top10Lists
import com.phimbud.mobile.core.model.TtlLruCache
import com.phimbud.mobile.core.model.UserSession
import com.phimbud.mobile.core.model.WatchInfo
import com.phimbud.mobile.core.network.PhimBudApiClient
import com.phimbud.mobile.core.network.SessionExpiredException
import com.phimbud.mobile.core.network.Top10Service
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.platform.KeyValueStore
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.core.platform.currentTimeMillis
import com.phimbud.mobile.core.platform.utcDateHourKeyFromEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

class PhimBudRepository(val platformContext: PlatformContext) {

    private data class CatalogIndexSnapshot(
        val timeKey: String = "",
        val items: List<SearchItem> = emptyList(),
        val byFid: Map<String, SearchItem> = emptyMap()
    ) {
        companion object {
            val EMPTY = CatalogIndexSnapshot()

            fun fromItems(timeKey: String, items: List<SearchItem>): CatalogIndexSnapshot {
                if (items.isEmpty()) return EMPTY
                val map = HashMap<String, SearchItem>(items.size * 2)
                for (item in items) {
                    if (item.fid.isNotEmpty()) {
                        map[item.fid] = item
                    }
                }
                return CatalogIndexSnapshot(timeKey = timeKey, items = items, byFid = map)
            }
        }
    }

    companion object {
        private const val DETAIL_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val TOP10_CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val TOP10_CACHE_KEY = "top10_lists_v1"

        private val cacheJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val apiClient = PhimBudApiClient(platformContext)
    private val top10Service by lazy { Top10Service(apiClient.httpEngine.publicHttpClient) }
    private val top10CacheStore by lazy { KeyValueStore(platformContext, "phimbud_top10_cache") }

    private val _isLoggedIn = MutableStateFlow(apiClient.hasSavedSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userSession = MutableStateFlow(UserSession())
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    private val _homeFeed = MutableStateFlow(HomeFeed())
    val homeFeed: StateFlow<HomeFeed> = _homeFeed.asStateFlow()

    private val _top10Lists = MutableStateFlow(Top10Lists())
    val top10Lists: StateFlow<Top10Lists> = _top10Lists.asStateFlow()

    @Volatile
    private var top10DiskHydrated = false
    private val top10HydrateMutex = Mutex()

    @Volatile
    private var cachedCatalog: CatalogIndexSnapshot = CatalogIndexSnapshot.EMPTY
    private val searchIndexMutex = Mutex()
    private val homeFeedCallMutex = Mutex()
    private var inFlightHomeFeed: Deferred<Result<HomeFeed>>? = null
    private val top10Mutex = Mutex()

    private val detailCache = TtlLruCache<String, TitleDetail>(maxSize = 20, ttlMs = DETAIL_CACHE_TTL_MS)
    private val episodesCache = TtlLruCache<String, List<Episode>>(maxSize = 30, ttlMs = DETAIL_CACHE_TTL_MS)

    init {
        if (_isLoggedIn.value) {
            backgroundScope.launch {
                ensureTop10DiskHydrated()
            }
        }
    }

    private suspend fun ensureTop10DiskHydrated() {
        if (top10DiskHydrated) return
        top10HydrateMutex.withLock {
            if (top10DiskHydrated) return
            val cached = withContext(Dispatchers.IO) {
                val raw = top10CacheStore.getString(TOP10_CACHE_KEY) ?: return@withContext null
                withContext(Dispatchers.Default) {
                    runCatching { cacheJson.decodeFromString<Top10Lists>(raw) }.getOrNull()
                }
            }
            if (cached != null && cached.hasAnyData && !_top10Lists.value.hasAnyData) {
                _top10Lists.value = cached
            }
            top10DiskHydrated = true
        }
    }

    private suspend fun saveTop10ToDisk(lists: Top10Lists) {
        val encoded = withContext(Dispatchers.Default) {
            runCatching { cacheJson.encodeToString(lists) }.getOrNull()
        } ?: return
        withContext(Dispatchers.IO) {
            runCatching { top10CacheStore.putString(TOP10_CACHE_KEY, encoded) }
        }
    }

    fun hasHomeFeedData(): Boolean {
        val f = _homeFeed.value
        return f.trending.isNotEmpty() || f.movies.isNotEmpty() || f.shows.isNotEmpty() || f.updated.isNotEmpty()
    }

    fun hasSearchIndex(): Boolean = cachedCatalog.items.isNotEmpty()

    fun isInWishlist(fid: String): Boolean = _homeFeed.value.wishlist.any { it.fid == fid }

    fun getCachedSeasonEpisodes(seasonFid: String): List<Episode>? = episodesCache.get(seasonFid)

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
        top10CacheStore.clear()
        cachedCatalog = CatalogIndexSnapshot.EMPTY
        top10DiskHydrated = true
        _isLoggedIn.value = false
        _userSession.value = UserSession()
        _homeFeed.value = HomeFeed()
        _top10Lists.value = Top10Lists()
    }

    suspend fun loadHomeFeed(): Result<HomeFeed> {
        val deferred = homeFeedCallMutex.withLock {
            inFlightHomeFeed?.takeIf { it.isActive } ?: backgroundScope.async {
                try {
                    val res = apiClient.getHomeFeed()
                    res.onFailure { err ->
                        if (err is SessionExpiredException) {
                            logout()
                        }
                    }
                    res.map { (user, feed) ->
                        _userSession.value = user
                        _homeFeed.value = feed
                        feed
                    }
                } finally {
                    homeFeedCallMutex.withLock {
                        inFlightHomeFeed = null
                    }
                }
            }.also { created ->
                inFlightHomeFeed = created
            }
        }
        return deferred.await()
    }

    private suspend fun ensureSearchIndexLoaded(): CatalogIndexSnapshot {
        val currentHourKey = utcDateHourKeyFromEpochMillis()
        val snap = cachedCatalog
        if (snap.items.isNotEmpty() && snap.timeKey == currentHourKey) {
            return snap
        }
        return searchIndexMutex.withLock {
            val latestSnap = cachedCatalog
            if (latestSnap.items.isNotEmpty() && latestSnap.timeKey == currentHourKey) {
                return@withLock latestSnap
            }
            val res = apiClient.getAllTitlesIndex(currentHourKey)
            res.onFailure { err ->
                if (err is SessionExpiredException) logout()
            }
            if (res.isSuccess) {
                val list = res.getOrDefault(emptyList())
                if (list.isNotEmpty()) {
                    val indexed = withContext(Dispatchers.Default) {
                        CatalogIndexSnapshot.fromItems(currentHourKey, list)
                    }
                    cachedCatalog = indexed
                    return@withLock indexed
                }
            }
            latestSnap
        }
    }

    private suspend fun resolveCatalogSnapshotFastFromRamOrDisk(): CatalogIndexSnapshot {
        val ram = cachedCatalog
        if (ram.items.isNotEmpty()) return ram
        val fromDisk = apiClient.readCachedTitlesIndexFromDisk() ?: return CatalogIndexSnapshot.EMPTY
        return searchIndexMutex.withLock {
            if (cachedCatalog.items.isNotEmpty()) {
                cachedCatalog
            } else {
                val indexed = withContext(Dispatchers.Default) {
                    CatalogIndexSnapshot.fromItems(fromDisk.first, fromDisk.second)
                }
                cachedCatalog = indexed
                indexed
            }
        }
    }

    suspend fun loadTop10Lists(forceRefresh: Boolean = false): Top10Lists = top10Mutex.withLock {
        ensureTop10DiskHydrated()
        val now = currentTimeMillis()
        val current = _top10Lists.value
        if (!forceRefresh && current.hasAnyData && (now - current.updatedAtEpochMs) in 0L until TOP10_CACHE_TTL_MS) {
            return current
        }

        val resolved = coroutineScope {
            val catalogDeferred = async { ensureSearchIndexLoaded().items }
            val chartsDeferred = async { top10Service.fetchRawCharts() }

            val catalog = catalogDeferred.await()
            val rawCharts = chartsDeferred.await()

            if (catalog.isNotEmpty() && !rawCharts.isEmpty) {
                withContext(Dispatchers.Default) {
                    Top10CatalogMatcher.buildTop10Lists(
                        raw = rawCharts,
                        catalog = catalog,
                        updatedAtEpochMs = currentTimeMillis()
                    )
                }
            } else {
                null
            }
        }

        if (resolved != null && resolved.hasAnyData) {
            _top10Lists.value = resolved
            saveTop10ToDisk(resolved)
            return resolved
        }

        return current
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
        return res.map { detail ->
            val finalDetail = if (detail.year <= 0) {
                val fastIndex = resolveCatalogSnapshotFastFromRamOrDisk()
                val fallbackYear = fastIndex.byFid[fid]?.year
                    ?: fastIndex.byFid[detail.fid]?.year
                    ?: 0
                if (fallbackYear > 0) detail.copy(year = fallbackYear) else detail
            } else {
                detail
            }
            detailCache.put(fid, finalDetail)
            finalDetail
        }
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
        val snapshot = ensureSearchIndexLoaded().items
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

    suspend fun getBrowsePage(page: Int, filter: BrowseFilter = BrowseFilter()): Result<BrowsePageResult> {
        val res = apiClient.getBrowsePage(
            page = page,
            type = filter.type,
            genre = filter.genre,
            country = filter.country,
            year = filter.year,
            duration = filter.duration,
            sort = filter.sort
        )
        res.onFailure { err ->
            if (err is SessionExpiredException) logout()
        }
        return res
    }

    suspend fun syncPlaytime(fid: String, seconds: Int) {
        apiClient.setPlaytime(fid, seconds)
    }

    fun syncPlaytimeInBackground(fid: String, seconds: Int, refreshFeedAfter: Boolean = false) {
        if (seconds <= 5 || fid.isBlank()) return
        backgroundScope.launch {
            syncPlaytime(fid, seconds)
            if (refreshFeedAfter) {
                loadHomeFeed()
            }
        }
    }

    suspend fun getCollectionState(fid: String): Result<String?> {
        val res = apiClient.getCollectionState(fid)
        res.onFailure { err ->
            if (err is SessionExpiredException) logout()
        }
        return res
    }

    suspend fun loadCollection(): Result<Pair<List<Movie>, List<Movie>>> {
        val res = apiClient.getCollection()
        res.onFailure { err ->
            if (err is SessionExpiredException) logout()
        }
        res.onSuccess { (wishlist, watching) ->
            val current = _homeFeed.value
            _homeFeed.value = current.copy(
                wishlist = wishlist,
                watching = if (watching.isNotEmpty()) watching else current.watching
            )
        }
        return res
    }

    suspend fun addToCollection(fid: String, status: String, movieSnapshot: Movie? = null): Result<Unit> {
        val res = apiClient.addToCollection(fid, status)
        res.onFailure { err ->
            if (err is SessionExpiredException) logout()
        }
        if (res.isSuccess) {
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
        }
        return res
    }

    suspend fun updateCollection(fid: String, status: String, movieSnapshot: Movie? = null) {
        addToCollection(fid, status, movieSnapshot)
    }
}
