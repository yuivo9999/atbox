package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.core.network.NativeHttpClient
import com.yuivo9999.mybox123.core.network.NativeHttpUrlPolicy
import com.yuivo9999.mybox123.data.source.NativeContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Format sniffing parity with legacy liveAdapters.js: explicit source format wins, then HTTP
 * Content-Type, then body signatures, and finally the URL-derived fallback.
 */
internal fun detectNativeLiveFormat(explicit: String?, urlHint: String, contentType: String?, body: String): String {
    if (!explicit.isNullOrBlank()) return explicit
    val mime = contentType.orEmpty().substringBefore(';').trim().lowercase()
    val trimmed = body.trimStart().removePrefix("\uFEFF")
    val detected = when {
        mime.contains("mpegurl") || mime.contains("m3u") -> "m3u"
        mime.contains("xml") -> if (Regex("<\\s*programme(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) "xmltv" else "xml"
        mime.contains("json") -> "json"
        trimmed.startsWith("#EXTM3U", ignoreCase = true) -> "m3u"
        trimmed.startsWith("<?xml", ignoreCase = true) || trimmed.startsWith("<tv", ignoreCase = true) ->
            if (Regex("<\\s*programme(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) "xmltv" else "xml"
        trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
        Regex("#genre#", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> "txt"
        else -> null
    }
    return detected ?: urlHint
}

/**
 * SourceId-scoped native live descriptor loader. It fetches bounded source text or uses imported
 * localContent, dispatches the format parser, and persists descriptor/EPG results in source-owned
 * caches. It intentionally does not instantiate a player or resolve deferred TV1 streams.
 */
class NativeLiveDescriptorService(
    private val httpClient: NativeHttpClient,
    private val cacheStore: NativeLiveCacheStore,
    /** Production wiring supplies a repository-backed lookup; null is retained for isolated parser tests. */
    private val currentSourceLookup: ((String) -> NativeContentSource?)? = null,
    private val sourceRegistryService: NativeSourceRegistryService? = null,
) {
    private fun requireCurrentSource(requested: NativeContentSource): NativeContentSource {
        val lookup = currentSourceLookup ?: return NativeLiveSourceDefinitionPolicy.requireCurrent(requested, requested)
        val current = lookup(requested.sourceId)
        return sourceRegistryService?.validateCurrentLiveSource(requested, current)
            ?: NativeLiveSourceDefinitionPolicy.requireCurrent(requested, current)
    }

    private fun requireCurrentSourceId(sourceId: String): NativeContentSource? {
        val lookup = currentSourceLookup ?: return null
        val current = lookup(sourceId) ?: throw IllegalArgumentException("SOURCE_NOT_FOUND")
        require(current.sourceType == "live") { "SOURCE_TYPE_NOT_LIVE" }
        require(current.enabled) { "SOURCE_DISABLED" }
        return current
    }

    suspend fun loadChannels(source: NativeContentSource, forceRefresh: Boolean = false): List<JSONObject> = withContext(Dispatchers.IO) {
        val currentSource = requireCurrentSource(source)
        val config = currentSource.toJson()
        val explicitFormat = explicitFormat(config)
        val configuredFormat = explicitFormat ?: detectFormat(config)
        require(configuredFormat != FORMAT_XMLTV) { "LIVE_SOURCE_IS_EPG_NOT_CHANNELS" }
        val cacheKey = stableHash(config.toString())
        val generation = cacheStore.generation()
        if (!forceRefresh) {
            val cached = cacheStore.getIfGeneration(currentSource.sourceId, "channels", cacheKey, generation)?.optJSONArray("items")
            if (cached != null) {
                val cachedChannels = cached.objectsCopy()
                val fingerprint = NativeLiveSourceDefinitionPolicy.fingerprint(currentSource)
                if (cachedChannels.all { it.optString("sourceDescriptorFingerprint") == fingerprint }) {
                    requireCurrentSource(source)
                    require(cacheStore.generation() == generation) { "LIVE_SOURCE_CHANGED_DURING_LOAD" }
                    return@withContext cachedChannels
                }
                // Ignore cache entries written by older schema versions that lacked source ownership.
            }
            require(cacheStore.generation() == generation) { "LIVE_SOURCE_CHANGED_DURING_LOAD" }
        }
        val response = readBody(currentSource, config)
        val body = response.first
        val format = detectNativeLiveFormat(explicitFormat, configuredFormat, response.second, body)
        require(format != FORMAT_XMLTV) { "LIVE_SOURCE_IS_EPG_NOT_CHANNELS" }
        val parsed = when (format) {
            FORMAT_JSON -> NativeLiveDescriptorParser.parseJsonLive(body)
            FORMAT_XML -> {
                // Legacy liveAdapters.js parses both channel descriptors and XMLTV programmes
                // from the same XML document, attaching programmes by channel id/reference.
                val programmes = NativeLiveDescriptorParser.parseXmlEpg(body)
                NativeLiveDescriptorParser.parseXmlLive(body).map { channel ->
                    val channelId = channel.optString("sourceItemId")
                    val channelKey = channel.optString("channelKey")
                    val matching = programmes.filter { programme ->
                        val ref = programme.optString("channelRef")
                        ref.isNotBlank() && (ref == channelId || ref == channelKey)
                    }
                    channel.put("epg", JSONArray(matching))
                }
            }
            FORMAT_M3U -> NativeLiveDescriptorParser.parseM3u(body).mapIndexed { index, channel ->
                if (!channel.has("streams") && channel.optJSONObject("stream") != null) {
                    channel.put("streams", JSONArray().put(channel.optJSONObject("stream")))
                }
                channel.put("epg", channel.optJSONArray("epg") ?: JSONArray())
                    .put("currentProgram", JSONObject.NULL)
                    .put("upcomingProgram", JSONObject.NULL)
                    .put("sourceOrder", index)
                channel
            }
            else -> NativeLiveDescriptorParser.parseTv1Metadata(body)
        }
        val sourceAnnotated = parsed.mapIndexed { index, channel ->
            val copy = JSONObject(channel.toString())
            val identity = channelIdentity(copy, index)
            copy.put("sourceId", currentSource.sourceId)
                .put("sourceChannelId", "${currentSource.sourceId}:$identity")
                .put("sourceDescriptorFingerprint", NativeLiveSourceDefinitionPolicy.fingerprint(currentSource))
                .put("sourceOrder", copy.optInt("sourceOrder", index))
            copy
        }
        requireCurrentSource(source)
        require(cacheStore.putIfGeneration(currentSource.sourceId, "channels", cacheKey, JSONObject().put("items", JSONArray(sourceAnnotated)), generation)) {
            "LIVE_SOURCE_CHANGED_DURING_LOAD"
        }
        sourceAnnotated.map { JSONObject(it.toString()) }
    }

    suspend fun loadStreams(source: NativeContentSource, channel: JSONObject, forceRefresh: Boolean = false): List<JSONObject> = withContext(Dispatchers.IO) {
        val currentSource = requireCurrentSource(source)
        val config = currentSource.toJson()
        val channelSourceId = channel.optString("sourceId").trim()
        val declaredSourceChannelId = channel.optString("sourceChannelId").trim()
        require(channelSourceId.isBlank() || channelSourceId == currentSource.sourceId) { "LIVE_CHANNEL_SOURCE_MISMATCH" }
        require(declaredSourceChannelId.isBlank() || declaredSourceChannelId.startsWith("${currentSource.sourceId}:")) {
            "LIVE_CHANNEL_SOURCE_MISMATCH"
        }
        val channelFingerprint = channel.optString("sourceDescriptorFingerprint").trim()
        require(channelFingerprint.isNotBlank() && channelFingerprint == NativeLiveSourceDefinitionPolicy.fingerprint(currentSource)) {
            "LIVE_CHANNEL_SOURCE_STALE"
        }
        val channelKey = channel.optString("sourceItemId").ifBlank { channel.optString("channelKey") }.ifBlank { channel.optString("name") }
        require(channelKey.isNotBlank()) { "LIVE_CHANNEL_ID_REQUIRED" }
        // Include the channel descriptor, not only its display/id fields: duplicate IDs/names and
        // same-source channels with different deferred references must not share a stream cache.
        val identity = channelIdentity(channel, channel.optInt("sourceOrder", -1))
        val sourceChannelId = channel.optString("sourceChannelId").ifBlank { "${currentSource.sourceId}:$identity" }
        val cacheKey = stableHash(config.toString() + "|" + identity)
        val generation = cacheStore.generation()
        if (!forceRefresh) {
            val cached = cacheStore.getIfGeneration(currentSource.sourceId, "streams", cacheKey, generation)?.optJSONArray("items")
            if (cached != null) {
                requireCurrentSource(source)
                require(cacheStore.generation() == generation) { "LIVE_SOURCE_CHANGED_DURING_LOAD" }
                return@withContext cached.objectsCopy()
            }
            require(cacheStore.generation() == generation) { "LIVE_SOURCE_CHANGED_DURING_LOAD" }
        }
        val deferred = channel.optJSONObject("deferredRef")
        val streams = if (deferred != null) {
            val body = readBody(currentSource, config).first
            val lines = body.removePrefix("\uFEFF").split(Regex("\\r?\\n"))
            val indices = deferred.optJSONArray("lineIndices") ?: JSONArray().apply {
                if (deferred.has("lineIndex")) put(deferred.optInt("lineIndex", -1))
            }
            val raw = mutableListOf<JSONObject>()
            for (index in 0 until indices.length()) {
                val lineIndex = indices.optInt(index, -1)
                val line = lines.getOrNull(lineIndex) ?: continue
                raw += NativeLiveDescriptorParser.parseTv1LineStreams(line)
            }
            raw.mapIndexed { index, stream ->
                val label = stream.optString("label").let { if (it == "线路 1" || Regex("^线路 \\d+$").matches(it)) "线路 ${index + 1}" else it }
                stream.put("label", label)
                    .put("streamId", "stream:$sourceChannelId:${index + 1}")
                    .put("sourceId", currentSource.sourceId)
                    .put("sourceItemId", channel.optString("sourceItemId").ifBlank { channelKey })
                    .put("sourceChannelId", sourceChannelId)
                    .put("priority", index)
                    .put("status", "unknown")
                    .put("lastCheckedAt", JSONObject.NULL)
                    .put("failureCode", JSONObject.NULL)
                    .put("failureCount", 0)
                    .put("updatedAt", System.currentTimeMillis())
                stream.put("protocol", NativeLiveDescriptorParser.normalizeLiveProtocol(stream.optString("protocol"), stream.optString("url")))
                stream
            }
        } else {
            val array = channel.optJSONArray("streams")
            if (array != null) array.objectsCopy() else channel.optJSONObject("stream")?.let { listOf(JSONObject(it.toString())) } ?: emptyList()
        }
        val sourceAnnotatedStreams = streams.mapIndexed { index, stream ->
            JSONObject(stream.toString())
                .put("sourceId", currentSource.sourceId)
                .put("sourceChannelId", sourceChannelId)
                .put("sourceItemId", channel.optString("sourceItemId").ifBlank { channelKey })
                .put("streamId", stream.optString("streamId").ifBlank { "stream:$sourceChannelId:${index + 1}" })
                .put("priority", if (stream.opt("priority") is Number) stream.optDouble("priority") else -index.toDouble())
                .put("protocol", NativeLiveDescriptorParser.normalizeLiveProtocol(stream.optString("protocol"), stream.optString("url")))
        }
        requireCurrentSource(source)
        require(cacheStore.putIfGeneration(currentSource.sourceId, "streams", cacheKey, JSONObject().put("items", JSONArray(sourceAnnotatedStreams)), generation)) {
            "LIVE_SOURCE_CHANGED_DURING_LOAD"
        }
        sourceAnnotatedStreams.map { JSONObject(it.toString()) }
    }

    suspend fun getCachedEpg(
        sourceId: String,
        cacheKey: String,
        rangeStartAt: String? = null,
        rangeEndAt: String? = null,
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        require(sourceId.isNotBlank()) { "LIVE_EPG_SOURCE_ID_REQUIRED" }
        requireCurrentSourceId(sourceId)
        val generation = cacheStore.generation()
        val cached = cacheStore.getIfGeneration(sourceId, "epg", cacheKey, generation) ?: return@withContext emptyList()
        val items = cached.optJSONArray("items") ?: return@withContext emptyList()
        val current = requireCurrentSourceId(sourceId)
        if (current != null && cached.optString("sourceDescriptorFingerprint") != NativeLiveSourceDefinitionPolicy.fingerprint(current)) {
            return@withContext emptyList()
        }
        require(cacheStore.generation() == generation) { "LIVE_SOURCE_CHANGED_DURING_LOAD" }
        NativeLiveDescriptorParser.filterEpgByRange(items.objectsCopy(), rangeStartAt, rangeEndAt)
    }

    suspend fun parseAndCacheEpg(source: NativeContentSource, cacheKey: String, xmltvText: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val currentSource = requireCurrentSource(source)
        require(cacheKey.isNotBlank()) { "LIVE_EPG_CACHE_KEY_REQUIRED" }
        val generation = cacheStore.generation()
        val programmes = NativeLiveDescriptorParser.parseXmlEpg(xmltvText).map { programme ->
            JSONObject(programme.toString()).put("sourceId", currentSource.sourceId)
        }
        requireCurrentSource(source)
        require(cacheStore.putIfGeneration(currentSource.sourceId, "epg", cacheKey, JSONObject()
            .put("sourceDescriptorFingerprint", NativeLiveSourceDefinitionPolicy.fingerprint(currentSource))
            .put("items", JSONArray(programmes)), generation)) {
            "LIVE_SOURCE_CHANGED_DURING_LOAD"
        }
        programmes.map { JSONObject(it.toString()) }
    }

    /** Returns body plus response Content-Type; local explicit formats are handled before sniffing. */
    private fun readBody(source: NativeContentSource, config: JSONObject): Pair<String, String?> {
        val localContent = if (config.has("localContent") && !config.isNull("localContent")) config.optString("localContent") else null
        if (localContent != null) {
            require(localContent.length <= NativeLiveDescriptorParser.MAX_INPUT_CHARS) { "LIVE_SOURCE_INPUT_TOO_LARGE" }
            return localContent to null
        }
        val url = source.sourceRef.trim().ifBlank { config.optString("url").trim() }
        require(NativeHttpUrlPolicy.normalize(url) != null) { "LIVE_SOURCE_URL_INVALID" }
        val headers = linkedMapOf<String, String>()
        val headerObject = config.optJSONObject("headers")
        if (headerObject != null) {
            val keys = headerObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = headerObject.optString(key).trim()
                if (key.isNotBlank() && value.isNotEmpty()) headers[key] = value
            }
        }
        val response = httpClient.execute(
            NativeHttpClient.NativeHttpRequest(
                url = url,
                headers = headers,
                timeoutMs = NativeHttpClient.DEFAULT_TIMEOUT_MS,
                maxResponseBytes = NativeHttpClient.DEFAULT_MAX_RESPONSE_BYTES,
            ),
        )
        if (!response.ok) throw IllegalStateException("LIVE_SOURCE_REQUEST_FAILED:${response.errorCode ?: "HTTP_${response.status}"}")
        if (response.body.length > NativeLiveDescriptorParser.MAX_INPUT_CHARS) throw IllegalArgumentException("LIVE_SOURCE_INPUT_TOO_LARGE")
        val contentType = response.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        return response.body to contentType
    }

    private fun explicitFormat(config: JSONObject): String? {
        val explicit = config.optString("localFormat").trim().lowercase()
        if (explicit in setOf(FORMAT_JSON, FORMAT_M3U, FORMAT_TXT, FORMAT_TV1, FORMAT_XML, FORMAT_XMLTV)) return explicit
        val mode = config.optString("liveMode").trim().lowercase()
        return mode.takeIf { it == FORMAT_TV1 }
    }


    private fun detectFormat(config: JSONObject): String {
        val ref = config.optString("sourceRef").ifBlank { config.optString("url") }.lowercase()
        return when {
            ref.substringBefore('?').substringBefore('#').endsWith(".m3u8") || ref.substringBefore('?').substringBefore('#').endsWith(".m3u") -> FORMAT_M3U
            ref.contains("xmltv") -> FORMAT_XMLTV
            ref.substringBefore('?').substringBefore('#').endsWith(".xml") -> FORMAT_XML
            ref.substringBefore('?').substringBefore('#').endsWith(".json") -> FORMAT_JSON
            else -> FORMAT_TXT
        }
    }

    private fun channelIdentity(channel: JSONObject, fallbackOrder: Int): String {
        val identity = JSONObject()
            .put("sourceItemId", channel.optString("sourceItemId"))
            .put("channelKey", channel.optString("channelKey"))
            .put("canonicalId", channel.optString("canonicalId"))
            .put("name", channel.optString("name"))
            .put("sourceOrder", channel.optInt("sourceOrder", fallbackOrder))
            .put("deferredRef", channel.opt("deferredRef") ?: JSONObject.NULL)
            .put("streams", channel.opt("streams") ?: JSONObject.NULL)
            .put("stream", channel.opt("stream") ?: JSONObject.NULL)
        return stableHash(identity.toString())
    }

    private fun stableHash(value: String): String {
        var hash = 2166136261L
        value.forEach { char -> hash = ((hash xor char.code.toLong()) * 16777619L) and 0xffffffffL }
        return hash.toString(36)
    }

    private fun JSONArray.objectsCopy(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it)?.let { item -> JSONObject(item.toString()) } }

    companion object {
        private const val FORMAT_JSON = "json"
        private const val FORMAT_M3U = "m3u"
        private const val FORMAT_TXT = "txt"
        private const val FORMAT_TV1 = "tv1"
        private const val FORMAT_XML = "xml"
        private const val FORMAT_XMLTV = "xmltv"
    }
}
