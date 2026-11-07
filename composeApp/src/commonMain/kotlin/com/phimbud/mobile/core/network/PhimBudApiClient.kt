package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.crypto.SubtitleCipher
import com.phimbud.mobile.core.model.BrowseFilterOptions
import com.phimbud.mobile.core.model.BrowsePageResult
import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.GenreTag
import com.phimbud.mobile.core.model.HomeFeed
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.model.SearchItem
import com.phimbud.mobile.core.model.SearchNormalizer
import com.phimbud.mobile.core.model.Season
import com.phimbud.mobile.core.model.Subtitle
import com.phimbud.mobile.core.model.TitleDetail
import com.phimbud.mobile.core.model.UserSession
import com.phimbud.mobile.core.model.WatchInfo
import com.phimbud.mobile.core.platform.CachedSubtitle
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.core.platform.SubtitleCache
import com.phimbud.mobile.core.platform.logError
import com.phimbud.mobile.core.platform.utcDateHourKeyFromEpochMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SessionExpiredException(message: String = "Phiên đăng nhập đã hết hạn") : Exception(message)

class PhimBudApiClient(context: PlatformContext) {

    val cookieStore = PersistentCookieStore(context)
    val hashResolver = RemoteHashResolver(context)
    private val subtitleCache = SubtitleCache(context)
    val httpEngine = PlatformHttpEngine(context, cookieStore)

    val httpClient: HttpClient
        get() = httpEngine.httpClient

    private val noRedirectClient: HttpClient
        get() = httpEngine.noRedirectHttpClient

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun hasSavedSession(): Boolean = cookieStore.hasSession()

    fun logout() {
        cookieStore.clear()
    }

    suspend fun login(email: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val res = noRedirectClient.submitForm(
                url = "https://phimbud.com/auth/login",
                formParameters = Parameters.build {
                    append("email", email)
                    append("password", pass)
                    append("currentUrl", "")
                }
            ) {
                header("Origin", "https://phimbud.com")
                header("Referer", "https://phimbud.com/auth/login")
                header("User-Agent", BrowserHeaders.USER_AGENT)
            }

            val setCookies = res.headers.getAll("Set-Cookie")
            if (!setCookies.isNullOrEmpty()) {
                cookieStore.saveFromSetCookieHeaders("phimbud.com", setCookies)
            }

            val status = res.status.value
            if (status in 200..399 && cookieStore.hasSession()) {
                true
            } else {
                throw Exception("Đăng nhập thất bại. Kiểm tra lại email và mật khẩu.")
            }
        }
    }

    suspend fun getHomeFeed(): Result<Pair<UserSession, HomeFeed>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = httpClient.get("https://phimbud.com/__data.json") {
                header("Referer", "https://phimbud.com/")
            }
            if (res.status.value == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                throw SessionExpiredException()
            }
            val text = res.bodyAsText()
            if (text.isBlank()) throw Exception("Empty response")

            withContext(Dispatchers.Default) {
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
                    if (text.contains("/auth/login")) throw SessionExpiredException()
                    throw it
                }
                if (root["type"]?.jsonPrimitive?.contentOrNull == "redirect" &&
                    root["location"]?.jsonPrimitive?.contentOrNull?.contains("/auth/login") == true
                ) {
                    throw SessionExpiredException()
                }
                val nodes = root["nodes"]?.jsonArray ?: throw Exception("No nodes in data")

                // Parse UserSession from Node 1
                var userSession = UserSession()
                if (nodes.size > 1 && nodes[1] !is JsonNull) {
                    val node1Data = nodes[1].jsonObject["data"]?.jsonArray
                    if (node1Data != null) {
                        val resolved = DevalueCodec.unflatten(node1Data) as? JsonObject
                        if (resolved != null) {
                            userSession = UserSession(
                                userId = resolved["userId"]?.jsonPrimitive?.longOrNull ?: 0,
                                userName = resolved["userName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                userEmail = resolved["userEmail"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                canWatch = resolved["canWatch"]?.jsonPrimitive?.booleanOrNull ?: true,
                                isAdmin = resolved["isAdmin"]?.jsonPrimitive?.booleanOrNull ?: false,
                                isVip = true
                            )
                        }
                    }
                }

                // Parse HomeFeed from Node 2
                val feed = if (nodes.size > 2 && nodes[2] !is JsonNull) {
                    val node2Data = nodes[2].jsonObject["data"]?.jsonArray
                    if (node2Data != null) {
                        val resolved = DevalueCodec.unflatten(node2Data) as? JsonObject
                        if (resolved != null) {
                            HomeFeed(
                                trending = parseMovieList(resolved["trendingTitles"]),
                                watching = parseMovieList(resolved["watching"]),
                                updated = parseMovieList(resolved["updated"]),
                                shows = parseMovieList(resolved["shows"]),
                                movies = parseMovieList(resolved["movies"]),
                                wishlist = parseMovieList(resolved["wishlist"])
                            )
                        } else HomeFeed()
                    } else HomeFeed()
                } else HomeFeed()

                Pair(userSession, feed)
            }
        }
    }

    private fun parseMovieList(elem: JsonElement?): List<Movie> {
        val array = elem as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val fid = obj["fid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Movie(
                fid = fid,
                nameEn = obj["nameEn"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                nameVi = obj["nameVi"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                intro = obj["intro"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                tmdbPoster = obj["tmdbPoster"]?.jsonPrimitive?.contentOrNull,
                tmdbBackdrop = obj["tmdbBackdrop"]?.jsonPrimitive?.contentOrNull,
                translation = obj["translation"]?.jsonPrimitive?.contentOrNull ?: "subtitle",
                year = obj["year"]?.jsonPrimitive?.intOrNull ?: 0,
                imdbRating = obj["imdbRating"]?.jsonPrimitive?.doubleOrNull
            )
        }
    }

    suspend fun resolvePlayFid(fid: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val res = noRedirectClient.get("https://phimbud.com/api/play/$fid") {
                header("Referer", "https://phimbud.com/")
            }
            val loc = res.headers["Location"]
            if (!loc.isNullOrBlank()) {
                val parts = loc.trimEnd('/').split('/')
                parts.last()
            } else {
                fid
            }
        }
    }

    suspend fun getWatchInfo(watchFid: String): Result<WatchInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val res = httpClient.get("https://phimbud.com/watch/$watchFid/__data.json") {
                header("Referer", "https://phimbud.com/watch/$watchFid")
            }
            if (res.status.value == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                throw SessionExpiredException()
            }
            val text = res.bodyAsText()
            if (text.isBlank()) throw Exception("Empty response")

            val root = json.parseToJsonElement(text).jsonObject
            if (root["type"]?.jsonPrimitive?.contentOrNull == "redirect" &&
                root["location"]?.jsonPrimitive?.contentOrNull?.contains("/auth/login") == true
            ) {
                throw SessionExpiredException()
            }
            val nodes = root["nodes"]?.jsonArray ?: throw Exception("No nodes in watch data")

            var info = WatchInfo(fid = watchFid)
            if (nodes.size > 2 && nodes[2] !is JsonNull) {
                val data = nodes[2].jsonObject["data"]?.jsonArray
                if (data != null) {
                    val resolved = DevalueCodec.unflatten(data) as? JsonObject
                    if (resolved != null) {
                        val titleObj = resolved["title"] as? JsonObject
                        val mediaInfo = titleObj?.get("mediainfoParsed") as? JsonObject

                        info = WatchInfo(
                            fid = titleObj?.get("fid")?.jsonPrimitive?.contentOrNull ?: watchFid,
                            showFid = titleObj?.get("showFid")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            showNameEn = titleObj?.get("showNameEn")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            showNameVi = titleObj?.get("showNameVi")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            seasonFid = titleObj?.get("seasonFid")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            seasonNumber = titleObj?.get("seasonNumber")?.jsonPrimitive?.intOrNull ?: 1,
                            episodeNumber = titleObj?.get("number")?.jsonPrimitive?.intOrNull ?: 1,
                            nextTitleFid = resolved["nextTitleFid"]?.jsonPrimitive?.contentOrNull,
                            prevTitleFid = resolved["prevTitleFid"]?.jsonPrimitive?.contentOrNull,
                            isVip = resolved["isVip"]?.jsonPrimitive?.booleanOrNull ?: false,
                            lastCurrentTime = resolved["lastCurrentTime"]?.jsonPrimitive?.intOrNull,
                            durationSeconds = mediaInfo?.get("duration")?.jsonPrimitive?.doubleOrNull ?: 0.0
                        )
                    }
                }
            }
            info
        }
    }

    suspend fun getTitleDetail(fid: String): Result<TitleDetail> = withContext(Dispatchers.IO) {
        runCatching {
            var targetUrl = "https://phimbud.com/title/a~$fid/__data.json"
            val res1 = httpClient.get(targetUrl) {
                header("Referer", "https://phimbud.com/")
            }
            if (res1.status.value == 401 || res1.request.url.encodedPath.contains("/auth/login")) {
                throw SessionExpiredException()
            }
            var text = res1.bodyAsText()

            if (text.contains(""""type":"redirect"""")) {
                val redirObj = json.parseToJsonElement(text).jsonObject
                val loc = redirObj["location"]?.jsonPrimitive?.contentOrNull
                if (!loc.isNullOrBlank()) {
                    if (loc.contains("/auth/login")) {
                        throw SessionExpiredException()
                    }
                    targetUrl = "https://phimbud.com${loc.trimEnd('/')}/__data.json"
                    val res2 = httpClient.get(targetUrl) {
                        header("Referer", "https://phimbud.com/")
                    }
                    if (res2.status.value == 401 || res2.request.url.encodedPath.contains("/auth/login")) {
                        throw SessionExpiredException()
                    }
                    text = res2.bodyAsText()
                }
            }

            val root = json.parseToJsonElement(text).jsonObject
            val nodes = root["nodes"]?.jsonArray ?: throw Exception("No nodes in title data")

            var detail = TitleDetail(fid = fid, nameEn = "", nameVi = "")
            if (nodes.size > 2 && nodes[2] !is JsonNull) {
                val data = nodes[2].jsonObject["data"]?.jsonArray
                if (data != null) {
                    val resolved = DevalueCodec.unflatten(data) as? JsonObject
                    if (resolved != null) {
                        val titleObj = resolved["title"] as? JsonObject
                        val genresArray = resolved["genres"] as? JsonArray
                        val seasonsArray = resolved["seasons"] as? JsonArray
                        val relatedArray = resolved["relatedTitles"] as? JsonArray

                        val genreTags = genresArray?.mapNotNull { g ->
                            val gObj = g as? JsonObject ?: return@mapNotNull null
                            val name = gObj["nameVi"]?.jsonPrimitive?.contentOrNull
                                ?: gObj["name"]?.jsonPrimitive?.contentOrNull
                                ?: return@mapNotNull null
                            val rawSlug = gObj["slug"]?.jsonPrimitive?.contentOrNull
                            val slug = rawSlug?.takeIf { it.isNotBlank() }
                                ?: BrowseFilterOptions.resolveGenreSlug(name)
                                ?: return@mapNotNull null
                            GenreTag(name = name, slug = slug)
                        } ?: emptyList()

                        val genres = genreTags.map { it.name }

                        val seasons = seasonsArray?.mapNotNull { s ->
                            val sObj = s as? JsonObject ?: return@mapNotNull null
                            val sfid = sObj["fid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val num = sObj["number"]?.jsonPrimitive?.intOrNull ?: 1
                            Season(
                                fid = sfid,
                                number = num,
                                seasonFull = sObj["seasonFull"]?.jsonPrimitive?.booleanOrNull ?: false,
                                episodeCount = sObj["episodeCount"]?.jsonPrimitive?.contentOrNull ?: "0"
                            )
                        } ?: emptyList()

                        val related = parseMovieList(relatedArray)
                        val parsedYear = titleObj?.get("year")?.jsonPrimitive?.intOrNull
                            ?: titleObj?.get("publishDate")?.jsonPrimitive?.contentOrNull?.take(4)?.toIntOrNull()
                            ?: 0

                        detail = TitleDetail(
                            fid = titleObj?.get("fid")?.jsonPrimitive?.contentOrNull ?: fid,
                            nameEn = titleObj?.get("nameEn")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            nameVi = titleObj?.get("nameVi")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            type = titleObj?.get("type")?.jsonPrimitive?.contentOrNull ?: "show",
                            intro = titleObj?.get("intro")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            tmdbPoster = titleObj?.get("tmdbPoster")?.jsonPrimitive?.contentOrNull,
                            tmdbBackdrop = titleObj?.get("tmdbBackdrop")?.jsonPrimitive?.contentOrNull,
                            imdbRating = titleObj?.get("imdbRating")?.jsonPrimitive?.doubleOrNull,
                            genres = genres,
                            genreTags = genreTags,
                            seasons = seasons,
                            relatedTitles = related,
                            year = parsedYear
                        )
                    }
                }
            }
            detail
        }
    }

    suspend fun getSeasonEpisodes(seasonFid: String): Result<List<Episode>> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("getEpisodes")
            val payload = DevalueCodec.encodeSingleArg(seasonFid)
            val url = "https://phimbud.com/_app/remote/$hash/getEpisodes?payload=$payload"

            val res = httpClient.get(url) {
                header("Referer", "https://phimbud.com/")
            }
            val text = res.bodyAsText()
            if (text.isBlank()) throw Exception("Empty response")

            val resolved = DevalueCodec.extractRpcResult(text) as? JsonObject
                ?: throw Exception("Failed to extract getEpisodes RPC result")

            val episodesArray = resolved["episodes"] as? JsonArray ?: JsonArray(emptyList())
            val releasesArray = resolved["releases"] as? JsonArray ?: JsonArray(emptyList())

            val releaseMap = mutableMapOf<Int, JsonObject>()
            for (rel in releasesArray) {
                val relObj = rel as? JsonObject ?: continue
                val num = relObj["number"]?.jsonPrimitive?.intOrNull ?: continue
                releaseMap[num] = relObj
            }

            val list = mutableListOf<Episode>()
            for (ep in episodesArray) {
                val epObj = ep as? JsonObject ?: continue
                val fid = epObj["fid"]?.jsonPrimitive?.contentOrNull ?: continue
                val num = epObj["number"]?.jsonPrimitive?.intOrNull ?: 1
                val rel = releaseMap[num]

                list.add(
                    Episode(
                        fid = fid,
                        number = num,
                        name = rel?.get("name")?.jsonPrimitive?.contentOrNull ?: "Tập $num",
                        airDate = rel?.get("airDate")?.jsonPrimitive?.contentOrNull,
                        tmdbPoster = rel?.get("tmdbPoster")?.jsonPrimitive?.contentOrNull
                    )
                )
            }
            list.sortedBy { it.number }
        }
    }

    suspend fun getVideoSrc(watchFid: String, server: String = "1"): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("getVideoSrc")
            val payload = DevalueCodec.encodeVideoSrc(watchFid, server)
            val url = "https://phimbud.com/_app/remote/$hash/getVideoSrc?payload=$payload"

            val res = httpClient.get(url) {
                header("Referer", "https://phimbud.com/watch/$watchFid")
                header("x-sveltekit-pathname", "/watch/$watchFid")
            }
            val text = res.bodyAsText()
            if (text.isBlank()) throw Exception("Empty response")

            val resolved = DevalueCodec.extractRpcResult(text) as? JsonObject
                ?: throw Exception("Failed to extract getVideoSrc RPC result")

            val src = resolved["src"]?.jsonPrimitive?.contentOrNull
            if (!src.isNullOrBlank()) {
                src
            } else {
                val err = resolved["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown video error"
                throw Exception("Server báo lỗi: $err")
            }
        }
    }

    suspend fun getSubtitles(watchFid: String): Result<List<Subtitle>> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("getSubtitles")
            val payload = DevalueCodec.encodeSingleArg(watchFid)
            val url = "https://phimbud.com/_app/remote/$hash/getSubtitles?payload=$payload"

            val res = httpClient.get(url) {
                header("Referer", "https://phimbud.com/watch/$watchFid")
                header("x-sveltekit-pathname", "/watch/$watchFid")
            }
            val text = res.bodyAsText()
            if (text.isBlank()) throw Exception("Empty response")

            val resolved = DevalueCodec.extractRpcResult(text) as? JsonArray
                ?: throw Exception("Failed to extract getSubtitles RPC result")

            val list = mutableListOf<Subtitle>()
            for (item in resolved) {
                val obj = item as? JsonObject ?: continue
                val id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L
                val subsceneId = obj["subsceneId"]?.jsonPrimitive?.longOrNull ?: 0L
                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: "vi"
                val fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull ?: continue
                val relArray = obj["releaseNames"] as? JsonArray
                val relName = relArray?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
                val isDef = obj["isDefault"]?.jsonPrimitive?.booleanOrNull ?: false

                val label = when (lang) {
                    "vi", "vi-VN" -> "Tiếng Việt"
                    "en" -> "English"
                    "ko" -> "Tiếng Hàn"
                    "zh", "zh-Hans", "zh-Hant" -> "Tiếng Trung"
                    else -> lang.uppercase()
                }

                list.add(
                    Subtitle(
                        id = id,
                        subsceneId = subsceneId,
                        language = lang,
                        fileName = fileName,
                        releaseName = relName,
                        isDefault = isDef,
                        label = label
                    )
                )
            }
            list
        }
    }

    suspend fun downloadDecryptedSubtitle(sub: Subtitle, watchFid: String = ""): Result<CachedSubtitle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeHash = sub.fileName.hashCode().toUInt().toString(16)
                val cacheFileName = "${sub.id}_${sub.subsceneId}_${safeHash}_${sub.language}.srt"
                subtitleCache.readIfCached(cacheFileName)?.let { cached ->
                    return@runCatching cached
                }

                val url = "https://phimbud.com/api/subtitle/${sub.subsceneId}/${sub.fileName}"
                val referer = if (watchFid.isNotBlank()) {
                    "https://phimbud.com/watch/$watchFid"
                } else {
                    "https://phimbud.com/"
                }
                val res = httpClient.get(url) {
                    header("Referer", referer)
                }
                if (!res.status.isSuccess()) {
                    throw Exception("HTTP ${res.status.value}: ${res.status.description}")
                }
                val base64Text = res.bodyAsText()
                if (base64Text.isBlank()) throw Exception("Empty subtitle")

                val decryptedSrt = SubtitleCipher.decrypt(sub.fileName, base64Text)
                subtitleCache.write(cacheFileName, decryptedSrt)
            }.onFailure { err ->
                logError("PhimBudApiClient", "Failed to download subtitle: ${sub.label} (${sub.fileName})", err)
            }
        }

    suspend fun getAllTitlesIndex(): Result<List<SearchItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val timeKey = utcDateHourKeyFromEpochMillis()
            val url = "https://phimbud.com/api/titles/$timeKey"
            val res = httpClient.get(url) {
                header("Referer", "https://phimbud.com/")
            }
            if (!res.status.isSuccess()) {
                throw Exception("HTTP ${res.status.value}: ${res.status.description}")
            }
            val text = res.bodyAsText()
            if (text.isBlank()) throw Exception("Empty search index")

            withContext(Dispatchers.Default) {
                val array = json.parseToJsonElement(text).jsonArray
                val list = ArrayList<SearchItem>(array.size)
                for (row in array) {
                    val r = row as? JsonArray ?: continue
                    if (r.size >= 7) {
                        val nameEn = r[1].jsonPrimitive.contentOrNull.orEmpty()
                        val nameVi = r[2].jsonPrimitive.contentOrNull.orEmpty()
                        list.add(
                            SearchItem(
                                fid = r[0].jsonPrimitive.contentOrNull.orEmpty(),
                                nameEn = nameEn,
                                nameVi = nameVi,
                                tmdbPoster = r[3].jsonPrimitive.contentOrNull,
                                translation = r[4].jsonPrimitive.contentOrNull ?: "subtitle",
                                imdbId = r[5].jsonPrimitive.contentOrNull,
                                year = r[6].jsonPrimitive.intOrNull ?: 0,
                                normalizedNameVi = SearchNormalizer.normalize(nameVi),
                                normalizedNameEn = SearchNormalizer.normalize(nameEn)
                            )
                        )
                    }
                }
                list
            }
        }
    }

    suspend fun getBrowsePage(
        page: Int = 1,
        type: String? = null,
        genre: String? = null,
        country: String? = null,
        year: String? = null,
        duration: String? = null,
        sort: String? = null
    ): Result<BrowsePageResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URLBuilder("https://phimbud.com/api/browse").apply {
                parameters.append("page", page.toString())
                if (!type.isNullOrBlank()) parameters.append("type", type)
                if (!genre.isNullOrBlank()) parameters.append("genre", genre)
                if (!country.isNullOrBlank()) parameters.append("country", country)
                if (!year.isNullOrBlank()) parameters.append("year", year)
                if (!duration.isNullOrBlank()) parameters.append("duration", duration)
                if (!sort.isNullOrBlank()) parameters.append("sort", sort)
            }.buildString()
            val res = httpClient.get(url) {
                header("Referer", "https://phimbud.com/browse")
            }
            if (res.status.value == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                throw SessionExpiredException()
            }
            if (!res.status.isSuccess()) {
                throw Exception("HTTP ${res.status.value}: ${res.status.description}")
            }
            val text = res.bodyAsText()
            if (text.isBlank()) throw Exception("Empty browse response")

            val obj = json.parseToJsonElement(text).jsonObject
            val titlesArray = obj["titles"] as? JsonArray ?: JsonArray(emptyList())
            val titles = parseMovieList(titlesArray)
            val nextPage = if (obj.containsKey("nextPage")) {
                val nextEl = obj["nextPage"]
                if (nextEl == null || nextEl is JsonNull) null else nextEl.jsonPrimitive.intOrNull
            } else {
                if (titles.isNotEmpty()) page + 1 else null
            }
            BrowsePageResult(titles = titles, nextPage = nextPage)
        }
    }

    suspend fun setPlaytime(fid: String, seconds: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("setPlaytime")
            val payload = DevalueCodec.encodePlaytime(fid, seconds)
            val body = """{"payload":"$payload","refreshes":[]}"""

            httpClient.post("https://phimbud.com/_app/remote/$hash/setPlaytime") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://phimbud.com")
                header("Referer", "https://phimbud.com/watch/$fid")
                header("x-sveltekit-pathname", "/watch/$fid")
                setBody(body)
            }
        }
    }

    suspend fun addToCollection(fid: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("addToCollection")
            val payload = DevalueCodec.encodeAddToCollection(fid, status)
            val statePayload = DevalueCodec.encodeSingleArg(fid)
            val refreshKey = "$hash/getCollectionState/$statePayload"
            val body = """{"payload":"$payload","refreshes":["$refreshKey"]}"""

            val res = httpClient.post("https://phimbud.com/_app/remote/$hash/addToCollection") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://phimbud.com")
                header("Referer", "https://phimbud.com/title/$fid")
                header("x-sveltekit-pathname", "/title/$fid")
                setBody(body)
            }

            if (res.status.value == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                throw SessionExpiredException()
            }

            val text = res.bodyAsText()
            if (text.contains(""""type":"redirect"""")) {
                val redirObj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                val loc = redirObj?.get("location")?.jsonPrimitive?.contentOrNull
                if (loc?.contains("/auth/login") == true) {
                    throw SessionExpiredException()
                }
            }

            if (!res.status.isSuccess()) {
                throw Exception("Thao tác danh sách thất bại (${res.status.value})")
            }
        }
    }

    suspend fun updateCollection(fid: String, status: String): Result<Unit> = addToCollection(fid, status)

    suspend fun getCollectionState(fid: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("getCollectionState")
            val payload = DevalueCodec.encodeSingleArg(fid)
            val url = "https://phimbud.com/_app/remote/$hash/getCollectionState?payload=$payload"

            val res = httpClient.get(url) {
                header("Referer", "https://phimbud.com/title/$fid")
                header("x-sveltekit-pathname", "/title/$fid")
            }

            if (res.status.value == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                throw SessionExpiredException()
            }

            val text = res.bodyAsText()
            if (text.contains(""""type":"redirect"""")) {
                val redirObj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                val loc = redirObj?.get("location")?.jsonPrimitive?.contentOrNull
                if (loc?.contains("/auth/login") == true) {
                    throw SessionExpiredException()
                }
            }

            val resolved = DevalueCodec.extractRpcResult(text)
            resolved?.jsonPrimitive?.contentOrNull
        }
    }

    suspend fun getCollection(): Result<Pair<List<Movie>, List<Movie>>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = httpClient.get("https://phimbud.com/collection/__data.json") {
                header("Referer", "https://phimbud.com/")
            }
            if (res.status.value == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                throw SessionExpiredException()
            }
            val text = res.bodyAsText()
            if (text.contains(""""type":"redirect"""")) {
                val redirObj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                val loc = redirObj?.get("location")?.jsonPrimitive?.contentOrNull
                if (loc?.contains("/auth/login") == true) {
                    throw SessionExpiredException()
                }
            }

            val root = json.parseToJsonElement(text).jsonObject
            val nodes = root["nodes"]?.jsonArray ?: throw Exception("No nodes in collection data")
            if (nodes.size > 2 && nodes[2] !is JsonNull) {
                val nodeData = nodes[2].jsonObject["data"]?.jsonArray
                if (nodeData != null) {
                    val resolved = DevalueCodec.unflatten(nodeData) as? JsonObject
                    if (resolved != null) {
                        val itemsArray = resolved["items"] as? JsonArray
                        val wishlist = mutableListOf<Movie>()
                        val watching = mutableListOf<Movie>()
                        itemsArray?.forEach { elem ->
                            val obj = elem as? JsonObject ?: return@forEach
                            val status = obj["status"]?.jsonPrimitive?.contentOrNull
                            val fid = obj["fid"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                            val movie = Movie(
                                fid = fid,
                                nameEn = obj["nameEn"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                nameVi = obj["nameVi"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                intro = obj["intro"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                tmdbPoster = obj["tmdbPoster"]?.jsonPrimitive?.contentOrNull,
                                tmdbBackdrop = obj["tmdbBackdrop"]?.jsonPrimitive?.contentOrNull,
                                translation = obj["translation"]?.jsonPrimitive?.contentOrNull ?: "subtitle",
                                year = obj["year"]?.jsonPrimitive?.intOrNull ?: 0,
                                imdbRating = obj["imdbRating"]?.jsonPrimitive?.doubleOrNull
                            )
                            when (status) {
                                "wishlist" -> wishlist.add(movie)
                                "watching" -> watching.add(movie)
                            }
                        }
                        return@runCatching Pair(wishlist, watching)
                    }
                }
            }
            Pair(emptyList(), emptyList())
        }
    }
}
