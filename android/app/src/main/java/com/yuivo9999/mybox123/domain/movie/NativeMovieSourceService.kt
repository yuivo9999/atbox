package com.yuivo9999.mybox123.domain.movie

import com.yuivo9999.mybox123.core.network.NativeHttpClient
import com.yuivo9999.mybox123.core.network.NativeHttpUrlPolicy
import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.movie.NativeTvBoxJsonParser
import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.data.source.NativeContentSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native HTTP VOD and safe TVBox JSON adapter entry point. It deliberately does not execute
 * arbitrary code from `tvboxExt`; DRPY/JAR definitions stay behind NativeTvBoxRuntime.
 */
class NativeMovieSourceService(
    private val sourceRepository: NativeContentSourceRepository,
    private val httpClient: NativeHttpClient,
) {
    suspend fun getList(
        sourceId: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        categoryId: String? = null,
    ): NativeMoviePage = load(sourceId, Query(page.coerceAtLeast(1), pageSize.coerceIn(1, MAX_PAGE_SIZE), categoryId, null))

    suspend fun search(
        sourceId: String,
        keyword: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        categoryId: String? = null,
    ): NativeMoviePage = load(sourceId, Query(page.coerceAtLeast(1), pageSize.coerceIn(1, MAX_PAGE_SIZE), categoryId, keyword.trim()))

    suspend fun getCategories(sourceId: String): List<NativeMovieCategory> = withContext(Dispatchers.IO) {
        val source = requireMovieSource(sourceId)
        val raw = fetchRaw(source, Query(page = 1, pageSize = 1, categoryId = null, keyword = null, categoriesOnly = true))
        NativeTvBoxJsonParser.extractCategories(raw.body)
    }

    suspend fun getDetail(sourceId: String, itemId: String): NativeMovieItem? = withContext(Dispatchers.IO) {
        val normalizedId = itemId.trim()
        if (normalizedId.isEmpty()) return@withContext null
        val source = requireMovieSource(sourceId)
        val fetched = fetchRaw(source, Query(page = 1, pageSize = MAX_PAGE_SIZE, categoryId = null, keyword = null, itemId = normalizedId))
        NativeTvBoxJsonParser.parse(fetched.body, source.sourceId, buildSourceMetadata(source)).firstOrNull { movie ->
            movie.sourceItemId == normalizedId || movie.contentId == normalizedId ||
                movie.toJson().optString("canonicalId") == normalizedId ||
                movie.toJson().optString("legacyContentId") == normalizedId
        }
    }

    private suspend fun load(sourceId: String, query: Query): NativeMoviePage = withContext(Dispatchers.IO) {
        val source = requireMovieSource(sourceId)
        val fetched = fetchRaw(source, query)
        val sourceMetadata = buildSourceMetadata(source)
        val parsed = NativeTvBoxJsonParser.parse(fetched.body, source.sourceId, sourceMetadata)
        val serverPaged = fetched.serverPaged
        val filtered = if (!serverPaged && !query.keyword.isNullOrBlank()) {
            val needle = query.keyword.lowercase()
            parsed.filter { movie ->
                val json = movie.toJson()
                listOf("title", "subtitle", "description", "category", "titleEn").any { key ->
                    json.optString(key).lowercase().contains(needle)
                }
            }
        } else parsed
        val categoryFiltered = if (!serverPaged && !query.categoryId.isNullOrBlank()) {
            filtered.filter { movie ->
                val json = movie.toJson()
                json.optString("sourceCategoryId") == query.categoryId ||
                    json.optJSONArray("sourceCategoryIds")?.let { array ->
                        (0 until array.length()).any { array.optString(it) == query.categoryId }
                    } == true
            }
        } else filtered
        val pageItems = if (serverPaged) categoryFiltered else {
            val start = ((query.page - 1) * query.pageSize).coerceAtMost(categoryFiltered.size)
            categoryFiltered.drop(start).take(query.pageSize)
        }
        val total = if (serverPaged) {
            NativeTvBoxJsonParser.extractTotal(fetched.body) ?: categoryFiltered.size.toLong()
        } else categoryFiltered.size.toLong()
        NativeMoviePage(source.sourceId, query.page, query.pageSize, total, pageItems, fetched.responseStatus)
    }

    private suspend fun fetchRaw(source: NativeContentSource, query: Query): FetchResult {
        if (source.sourceType != SOURCE_MOVIE) throw IllegalArgumentException("SOURCE_TYPE_NOT_MOVIE")
        if (!source.enabled) throw IllegalStateException("SOURCE_DISABLED")
        val config = JSONObject(source.rawJson)
        val route = NativeMovieAdapterRoutingPolicy.resolve(
            adapterType = config.stringValue("adapterType"),
            sourceCapability = config.stringValue("sourceCapability"),
            tvboxAdapterKind = config.stringValue("tvboxAdapterKind"),
            tvboxRequiresJar = config.optBoolean("tvboxRequiresJar", false),
        )
        return when (route) {
            NativeMovieAdapterRoutingPolicy.Route.SAFE_JSON_EXT -> fetchSafeTvBoxExt(config, source, query)
            NativeMovieAdapterRoutingPolicy.Route.HTTP_VOD -> fetchHttpSource(config, source, query)
        }
    }

    private suspend fun fetchSafeTvBoxExt(config: JSONObject, source: NativeContentSource, query: Query): FetchResult {
        val kind = config.stringValue("tvboxAdapterKind").ifBlank { "ext" }
        if (kind != "ext") throw IllegalStateException("TVBOX_EXTENSION_RUNTIME_REQUIRED:$kind")
        val format = config.stringValue("tvboxExtFormat")
        val supportedFormats = setOf("", "json-vod", "remote-json", "remote-resource", "inline-json")
        if (format !in supportedFormats) throw IllegalArgumentException("TVBOX_EXT_FORMAT_UNSUPPORTED:$format")
        val extValue = config.opt("tvboxExt")
        if (extValue == null || extValue === JSONObject.NULL) throw IllegalArgumentException("TVBOX_EXT_JSON_EMPTY")
        val metadata = buildSourceMetadata(source)
        if (extValue is JSONObject || extValue is JSONArray) {
            val raw = extValue.toString()
            check(raw.length <= NativeTvBoxJsonParser.MAX_JSON_CHARACTERS) { "TVBOX_EXT_JSON_TOO_LARGE" }
            return FetchResult(raw, responseStatus = null, serverPaged = false)
        }
        if (extValue !is String) throw IllegalArgumentException("TVBOX_EXT_JSON_INVALID")
        val ext = extValue.trim().removePrefix("\uFEFF")
        if (NativeHttpUrlPolicy.normalize(ext) != null) {
            val looksLikeJsonResource = ext.substringBefore('#').substringBefore('?').endsWith(".json", ignoreCase = true)
            if (format != "remote-resource" && !looksLikeJsonResource) {
                throw IllegalArgumentException("TVBOX_EXT_REMOTE_JSON_NOT_ALLOWED")
            }
            return executeHttp(config, source, ext, query, forceStaticJson = true)
        }
        if (!ext.startsWith("{") && !ext.startsWith("[")) throw IllegalArgumentException("TVBOX_EXT_JSON_INVALID")
        check(ext.length <= NativeTvBoxJsonParser.MAX_JSON_CHARACTERS) { "TVBOX_EXT_JSON_TOO_LARGE" }
        // Parse here too so malformed inline content fails at the adapter boundary.
        NativeTvBoxJsonParser.parse(ext, source.sourceId, metadata)
        return FetchResult(ext, responseStatus = null, serverPaged = false)
    }

    private suspend fun fetchHttpSource(config: JSONObject, source: NativeContentSource, query: Query): FetchResult {
        if (config.has("localContent") && !config.isNull("localContent")) {
            val localValue = config.opt("localContent")
            val body = when (localValue) {
                is JSONObject, is JSONArray -> localValue.toString()
                is String -> localValue.removePrefix("\uFEFF").trim()
                else -> throw IllegalArgumentException("MOVIE_SOURCE_LOCAL_CONTENT_INVALID")
            }
            if (body.isEmpty()) throw IllegalStateException("MOVIE_SOURCE_EMPTY_RESPONSE")
            if (body.length > NativeTvBoxJsonParser.MAX_JSON_CHARACTERS) throw IllegalArgumentException("MOVIE_SOURCE_RESPONSE_TOO_LARGE")
            NativeMovieResponseContract.validateJsonSyntax(body)
            return FetchResult(body, responseStatus = null, serverPaged = false)
        }
        val endpoint = source.sourceRef.trim().ifBlank { config.stringValue("endpoint") }
        if (NativeHttpUrlPolicy.normalize(endpoint) == null) throw IllegalArgumentException("MOVIE_SOURCE_ENDPOINT_INVALID")
        return executeHttp(config, source, endpoint, query, forceStaticJson = false)
    }

    private suspend fun executeHttp(
        config: JSONObject,
        source: NativeContentSource,
        endpoint: String,
        query: Query,
        forceStaticJson: Boolean,
    ): FetchResult {
        val parsedEndpoint = endpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("MOVIE_SOURCE_ENDPOINT_INVALID")
        val apiEndpoint = !forceStaticJson && isCatVodEndpoint(parsedEndpoint)
        val requestUrl = NativeMovieRequestUrlPolicy.build(
            endpoint = endpoint,
            categoriesOnly = query.categoriesOnly,
            categoryId = query.categoryId,
            itemId = query.itemId,
            page = query.page,
            pageSize = query.pageSize,
            keyword = query.keyword,
            forceStaticJson = forceStaticJson,
        )

        val headers = readHeaders(config)
        val timeout = config.optLong("timeoutMs", NativeHttpClient.DEFAULT_TIMEOUT_MS)
            .coerceIn(1_000L, 120_000L)
        val response = httpClient.execute(
            NativeHttpClient.NativeHttpRequest(
                url = requestUrl,
                headers = headers,
                timeoutMs = timeout,
                maxResponseBytes = NativeTvBoxJsonParser.MAX_JSON_CHARACTERS,
            ),
        )
        if (!response.ok) {
            val failureCode = NativeMovieResponseContract.requestFailureCode(response.errorCode, response.status)
            throw IllegalStateException(failureCode)
        }
        val body = NativeMovieResponseContract.normalizeBody(
            response.body,
            response.headers,
            NativeTvBoxJsonParser.MAX_JSON_CHARACTERS,
        )
        NativeMovieResponseContract.validateJsonSyntax(body)
        return FetchResult(body, response.status, serverPaged = apiEndpoint)
    }

    private fun readHeaders(config: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        config.optJSONObject("headers")?.let { json ->
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)
                if (value != null && value !== JSONObject.NULL && key.isNotBlank()) result[key] = value.toString()
            }
        }
        fun addIfMissing(key: String, value: String) {
            if (value.isNotBlank() && result.keys.none { it.equals(key, ignoreCase = true) }) result[key] = value
        }
        addIfMissing("User-Agent", config.stringValue("userAgent"))
        addIfMissing("Referer", config.stringValue("referer"))
        addIfMissing("Cookie", config.stringValue("cookies"))
        return result
    }

    private fun buildSourceMetadata(source: NativeContentSource): JSONObject {
        val sourceJson = JSONObject(source.rawJson)
        val result = JSONObject()
        val keys = listOf(
            "sourceCapability", "adapterType", "tvboxAdapterKind", "tvboxRequiresJar", "tvboxJar",
            "tvboxType", "playerType", "headers", "userAgent", "referer", "cookies", "tvboxIJKProfiles", "tvboxParseConfig",
        )
        keys.forEach { key ->
            if (sourceJson.has(key) && !sourceJson.isNull(key)) result.put(key, sourceJson.opt(key))
        }
        result.put("sourceCapability", sourceJson.stringValue("sourceCapability").ifBlank {
            if (sourceJson.stringValue("adapterType") == ADAPTER_TVBOX_EXTENSION) "tvbox-extension" else "http-vod"
        })
        return result
    }

    private fun requireMovieSource(sourceId: String): NativeContentSource {
        val id = sourceId.trim()
        require(id.isNotEmpty()) { "SOURCE_ID_REQUIRED" }
        return sourceRepository.getAll().firstOrNull { it.sourceId == id }
            ?: throw IllegalArgumentException("SOURCE_NOT_FOUND")
    }

    private fun isCatVodEndpoint(url: HttpUrl): Boolean {
        val path = url.encodedPath.lowercase()
        return path.contains("api.php") || path.contains("provide/vod") || path.contains("/vod/")
    }

    private fun JSONObject.stringValue(key: String): String =
        if (!has(key) || isNull(key)) "" else optString(key, "")

    private data class Query(
        val page: Int,
        val pageSize: Int,
        val categoryId: String?,
        val keyword: String?,
        val categoriesOnly: Boolean = false,
        val itemId: String? = null,
    )

    private data class FetchResult(val body: String, val responseStatus: Int?, val serverPaged: Boolean)

    companion object {
        const val SOURCE_MOVIE = "movie"
        const val ADAPTER_HTTP_VOD = "http-vod"
        const val ADAPTER_TVBOX_EXTENSION = "tvbox-extension"
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}

data class NativeMoviePage(
    val sourceId: String,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val items: List<NativeMovieItem>,
    val httpStatus: Int?,
)
