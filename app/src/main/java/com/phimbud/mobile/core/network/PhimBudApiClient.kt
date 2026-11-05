package com.phimbud.mobile.core.network

import android.content.Context
import com.phimbud.mobile.core.crypto.SubtitleCipher
import com.phimbud.mobile.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SessionExpiredException(message: String = "Phiên đăng nhập đã hết hạn") : IOException(message)

class PhimBudApiClient(private val context: Context) {

    val cookieJar = PersistentCookieJar(context)
    val hashResolver = RemoteHashResolver(context)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(BrowserHeadersInterceptor())
        .build()

    private val noRedirectClient: OkHttpClient by lazy {
        okHttpClient.newBuilder().followRedirects(false).build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "https://phimbud.com/"
    )

    fun hasSavedSession(): Boolean = cookieJar.hasSession()

    fun logout() {
        cookieJar.clear()
    }

    suspend fun login(email: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder()
                .add("email", email)
                .add("password", pass)
                .add("currentUrl", "")
                .build()

            val req = Request.Builder()
                .url("https://phimbud.com/auth/login")
                .post(formBody)
                .addHeader("Origin", "https://phimbud.com")
                .addHeader("Referer", "https://phimbud.com/auth/login")
                .addHeader("User-Agent", baseHeaders["User-Agent"]!!)
                .build()

            val isOk = okHttpClient.newCall(req).execute().use { res ->
                res.isSuccessful && cookieJar.hasSession()
            }
            if (isOk) {
                true
            } else {
                throw IOException("Đăng nhập thất bại. Kiểm tra lại email và mật khẩu.")
            }
        }
    }

    suspend fun getHomeFeed(): Result<Pair<UserSession, HomeFeed>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://phimbud.com/__data.json")
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val text = okHttpClient.newCall(req).execute().use { res ->
                if (res.code == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                    throw SessionExpiredException()
                }
                res.body?.string() ?: throw IOException("Empty response")
            }

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
                val nodes = root["nodes"]?.jsonArray ?: throw IOException("No nodes in data")

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
                year = obj["year"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }
    }

    suspend fun resolvePlayFid(fid: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Direct play endpoint redirects to episode watch page: /api/play/:fid -> /watch/:watchFid
            val req = Request.Builder()
                .url("https://phimbud.com/api/play/$fid")
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val loc = noRedirectClient.newCall(req).execute().use { res ->
                res.header("Location")
            }
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
            val req = Request.Builder()
                .url("https://phimbud.com/watch/$watchFid/__data.json")
                .header("Referer", "https://phimbud.com/watch/$watchFid")
                .build()

            val text = okHttpClient.newCall(req).execute().use { res ->
                if (res.code == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                    throw SessionExpiredException()
                }
                res.body?.string() ?: throw IOException("Empty response")
            }
            val root = json.parseToJsonElement(text).jsonObject
            if (root["type"]?.jsonPrimitive?.contentOrNull == "redirect" &&
                root["location"]?.jsonPrimitive?.contentOrNull?.contains("/auth/login") == true
            ) {
                throw SessionExpiredException()
            }
            val nodes = root["nodes"]?.jsonArray ?: throw IOException("No nodes in watch data")

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
            val req1 = Request.Builder()
                .url(targetUrl)
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            var text = okHttpClient.newCall(req1).execute().use { res ->
                if (res.code == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                    throw SessionExpiredException()
                }
                res.body?.string().orEmpty()
            }

            // Check if returned redirect JSON: {"type":"redirect","location":"/title/slug~fid"}
            if (text.contains(""""type":"redirect"""")) {
                val redirObj = json.parseToJsonElement(text).jsonObject
                val loc = redirObj["location"]?.jsonPrimitive?.contentOrNull
                if (!loc.isNullOrBlank()) {
                    if (loc.contains("/auth/login")) {
                        throw SessionExpiredException()
                    }
                    targetUrl = "https://phimbud.com${loc.trimEnd('/')}/__data.json"
                    val req2 = Request.Builder()
                        .url(targetUrl)
                        .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .build()
                    text = okHttpClient.newCall(req2).execute().use { res ->
                        if (res.code == 401 || res.request.url.encodedPath.contains("/auth/login")) {
                            throw SessionExpiredException()
                        }
                        res.body?.string().orEmpty()
                    }
                }
            }

            val root = json.parseToJsonElement(text).jsonObject
            val nodes = root["nodes"]?.jsonArray ?: throw IOException("No nodes in title data")

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

                        val genres = genresArray?.mapNotNull { g ->
                            (g as? JsonObject)?.get("nameVi")?.jsonPrimitive?.contentOrNull
                        } ?: emptyList()

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
                            seasons = seasons,
                            relatedTitles = related
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

            val req = Request.Builder()
                .url(url)
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val text = okHttpClient.newCall(req).execute().use { res ->
                res.body?.string() ?: throw IOException("Empty response")
            }
            val resolved = DevalueCodec.extractRpcResult(text) as? JsonObject
                ?: throw IOException("Failed to extract getEpisodes RPC result")

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

            val req = Request.Builder()
                .url(url)
                .header("Referer", "https://phimbud.com/watch/$watchFid")
                .header("x-sveltekit-pathname", "/watch/$watchFid")
                .build()

            val text = okHttpClient.newCall(req).execute().use { res ->
                res.body?.string() ?: throw IOException("Empty response")
            }
            val resolved = DevalueCodec.extractRpcResult(text) as? JsonObject
                ?: throw IOException("Failed to extract getVideoSrc RPC result")

            val src = resolved["src"]?.jsonPrimitive?.contentOrNull
            if (!src.isNullOrBlank()) {
                src
            } else {
                val err = resolved["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown video error"
                throw IOException("Server báo lỗi: $err")
            }
        }
    }

    suspend fun getSubtitles(watchFid: String): Result<List<Subtitle>> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("getSubtitles")
            val payload = DevalueCodec.encodeSingleArg(watchFid)
            val url = "https://phimbud.com/_app/remote/$hash/getSubtitles?payload=$payload"

            val req = Request.Builder()
                .url(url)
                .header("Referer", "https://phimbud.com/watch/$watchFid")
                .header("x-sveltekit-pathname", "/watch/$watchFid")
                .build()

            val text = okHttpClient.newCall(req).execute().use { res ->
                res.body?.string() ?: throw IOException("Empty response")
            }
            val resolved = DevalueCodec.extractRpcResult(text) as? JsonArray
                ?: throw IOException("Failed to extract getSubtitles RPC result")

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

    suspend fun downloadDecryptedSubtitle(sub: Subtitle, watchFid: String = ""): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
            val safeHash = sub.fileName.hashCode().toUInt().toString(16)
            val file = File(dir, "${sub.id}_${sub.subsceneId}_${safeHash}_${sub.language}.srt")
            if (file.exists() && file.length() > 0) {
                return@runCatching file
            }

            val url = "https://phimbud.com/api/subtitle/${sub.subsceneId}/${sub.fileName}"
            val req = Request.Builder()
                .url(url)
                .header("Referer", if (watchFid.isNotBlank()) "https://phimbud.com/watch/$watchFid" else "https://phimbud.com/")
                .build()

            val base64Text = okHttpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IOException("HTTP ${res.code}: ${res.message}")
                }
                res.body?.string() ?: throw IOException("Empty subtitle")
            }
            val decryptedSrt = SubtitleCipher.decrypt(sub.fileName, base64Text)

            val tmpFile = File(dir, "${file.name}.tmp")
            tmpFile.writeText(decryptedSrt, Charsets.UTF_8)
            if (!tmpFile.renameTo(file)) {
                file.writeText(decryptedSrt, Charsets.UTF_8)
                tmpFile.delete()
            }
            file
        }.onFailure { err ->
            android.util.Log.e("PhimBudApiClient", "Failed to download subtitle: ${sub.label} (${sub.fileName})", err)
        }
    }

    suspend fun getAllTitlesIndex(): Result<List<SearchItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val dateStr = sdf.format(cal.time)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val timeKey = "$dateStr-$hour.js"

            val url = "https://phimbud.com/api/titles/$timeKey"
            val req = Request.Builder()
                .url(url)
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val text = okHttpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IOException("HTTP ${res.code}: ${res.message}")
                }
                res.body?.string() ?: throw IOException("Empty search index")
            }

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

    suspend fun getBrowsePage(page: Int = 1): Result<List<Movie>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://phimbud.com/api/browse?page=$page"
            val req = Request.Builder()
                .url(url)
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val text = okHttpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IOException("HTTP ${res.code}: ${res.message}")
                }
                res.body?.string() ?: throw IOException("Empty browse response")
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val titlesArray = obj["titles"] as? JsonArray ?: JsonArray(emptyList())

            parseMovieList(titlesArray)
        }
    }

    suspend fun setPlaytime(fid: String, seconds: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("setPlaytime")
            val payload = DevalueCodec.encodePlaytime(fid, seconds)
            val body = """{"payload":"$payload","refreshes":[]}""".toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://phimbud.com/_app/remote/$hash/setPlaytime")
                .post(body)
                .header("Origin", "https://phimbud.com")
                .header("Referer", "https://phimbud.com/watch/$fid")
                .header("x-sveltekit-pathname", "/watch/$fid")
                .build()

            okHttpClient.newCall(req).execute().use { }
        }
    }

    suspend fun updateCollection(fid: String, status: String) = withContext(Dispatchers.IO) {
        runCatching {
            val hash = hashResolver.getHash("updateCollection")
            val payload = DevalueCodec.encodeCollection(fid, status)
            val body = """{"payload":"$payload","refreshes":[]}""".toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://phimbud.com/_app/remote/$hash/updateCollection")
                .post(body)
                .header("Origin", "https://phimbud.com")
                .header("Referer", "https://phimbud.com/title/$fid")
                .header("x-sveltekit-pathname", "/title/$fid")
                .build()

            okHttpClient.newCall(req).execute().use { }
        }
    }
}
