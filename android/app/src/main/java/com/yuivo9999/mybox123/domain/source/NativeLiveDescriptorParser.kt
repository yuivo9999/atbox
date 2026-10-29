package com.yuivo9999.mybox123.domain.source

import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Data-layer parsers for legacy TV1/TXT and M3U live source descriptors.
 * This class only parses channel/stream metadata; it does not fetch sources or start playback.
 */
object NativeLiveDescriptorParser {
    const val MAX_INPUT_CHARS = 5 * 1024 * 1024

    fun parseTv1Metadata(text: String): List<JSONObject> {
        val normalized = normalizeInput(text)
        if (normalized.isEmpty()) return emptyList()
        val channels = mutableListOf<JSONObject>()
        val indexByKey = linkedMapOf<String, Int>()
        var category = "默认频道"
        normalized.split(Regex("\\r?\\n")).forEachIndexed { lineIndex, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.contains("#genre#", ignoreCase = true) || (line.startsWith("[") && line.endsWith("]"))) {
                val categoryText = line.replace(Regex("[,，]?\\s*#genre#.*$", RegexOption.IGNORE_CASE), "").trim()
                val parsedCategory = if (categoryText.startsWith("[") && categoryText.endsWith("]")) {
                    categoryText.substring(1, categoryText.length - 1).trim()
                } else categoryText
                if (parsedCategory.isNotEmpty()) category = parsedCategory
                return@forEachIndexed
            }
            if (line.startsWith("#") || line.startsWith("//")) return@forEachIndexed

            val commaIndex = line.indexOfAny(charArrayOf(',', '，'))
            val whitespaceParts = line.split(Regex("\\s+"), limit = 2)
            val name = if (commaIndex >= 0) line.substring(0, commaIndex).trim() else whitespaceParts.firstOrNull().orEmpty()
            if (name.isEmpty()) return@forEachIndexed
            val rawUrls = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else whitespaceParts.getOrNull(1).orEmpty().trim()
            val estimatedCount = maxOf(1, rawUrls.split('#').count { it.isNotBlank() })
            val key = "$category:::$name"
            val existingIndex = indexByKey[key]
            if (existingIndex != null) {
                val existing = channels[existingIndex]
                existing.optJSONObject("deferredRef")?.optJSONArray("lineIndices")?.put(lineIndex)
                existing.put("estimatedStreamCount", existing.optInt("estimatedStreamCount", 1) + estimatedCount)
            } else {
                val order = channels.size
                val lineIndices = JSONArray().put(lineIndex)
                val channel = JSONObject()
                    .put("sourceItemId", "txt-${order + 1}-$name")
                    .put("canonicalId", name)
                    .put("channelKey", name)
                    .put("name", name)
                    .put("logo", "")
                    .put("categoryId", category)
                    .put("category", category)
                    .put("sourceOrder", order)
                    .put("streams", JSONArray())
                    .put("epg", JSONArray())
                    .put("currentProgram", JSONObject.NULL)
                    .put("upcomingProgram", JSONObject.NULL)
                    .put("deferredRef", JSONObject().put("lineIndex", lineIndex).put("lineIndices", lineIndices))
                    .put("estimatedStreamCount", estimatedCount)
                indexByKey[key] = order
                channels += channel
            }
        }
        return channels
    }

    /** Mirrors liveModels.normalizeProtocol/live streamSniffer URL hints; unknown URLs stay auto. */
    fun inferLiveProtocolFromUrl(urlValue: String): String {
        val url = urlValue.trim()
        val lower = url.lowercase()
        return when {
            Regex("^rtmps?://", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "rtmp"
            Regex("^rtsps?://", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "rtsp"
            Regex("(?:\\.m3u8[a-z0-9_-]*(?:$|[?#])|\\.m3u(?:$|[?#])|/hls(?:/|$)|/playlist(?:\\.m3u8)?(?:$|[?#])|/pltv/|/tvod/)", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "hls"
            Regex("\\.flv(?:$|[?#])", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "flv"
            Regex("\\.ts(?:$|[?#])", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "ts"
            Regex("\\.mp4(?:$|[?#])", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "mp4"
            Regex("\\.mpd(?:$|[?#])", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "dash"
            else -> "auto"
        }
    }

    /** Legacy normalizeProtocol preserves non-generic explicit protocol names, otherwise infers. */
    fun normalizeLiveProtocol(protocolValue: String?, url: String): String {
        val explicit = protocolValue.orEmpty().trim().lowercase()
        if (explicit in setOf("hls/m3u8", "application/vnd.apple.mpegurl", "application/x-mpegurl")) return "hls"
        if (explicit.isNotEmpty() && explicit !in setOf("http", "https", "http/mp4", "unknown", "auto")) return explicit
        return inferLiveProtocolFromUrl(url)
    }

    /** Mirrors legacy getEPG overlap filtering; timestamps remain in their source format. */
    fun filterEpgByRange(programmes: List<JSONObject>, rangeStartAt: String? = null, rangeEndAt: String? = null): List<JSONObject> {
        val start = rangeStartAt.orEmpty()
        val end = rangeEndAt.orEmpty()
        return programmes.filter { programme ->
            val programmeEnd = programme.optString("endAt").ifBlank { programme.optString("endTime") }
            val programmeStart = programme.optString("startAt").ifBlank { programme.optString("startTime") }
            (start.isBlank() || programmeEnd >= start) && (end.isBlank() || programmeStart <= end)
        }
    }

    fun parseTv1LineStreams(lineValue: String): List<JSONObject> {
        val line = lineValue.trim()
        if (line.isEmpty()) return emptyList()
        val commaIndex = line.indexOfAny(charArrayOf(',', '，'))
        val whitespaceParts = line.split(Regex("\\s+"), limit = 2)
        val rawUrls = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else whitespaceParts.getOrNull(1).orEmpty().trim()
        if (rawUrls.isEmpty()) return emptyList()
        return rawUrls.split('#').mapIndexedNotNull { index, candidate ->
            var url = candidate.trim()
            if (url.isEmpty()) return@mapIndexedNotNull null
            var label = "线路 ${index + 1}"
            if (url.contains('$')) {
                val parts = url.split('$')
                url = parts.firstOrNull()?.trim().orEmpty()
                val customLabel = parts.drop(1).joinToString("$").trim()
                if (customLabel.isNotEmpty()) label = customLabel
            }
            if (url.isEmpty()) return@mapIndexedNotNull null
            // Match the legacy TXT live parser's accepted stream forms. This is metadata
            // validation only; it does not authorize playback or bypass the HTTP URL policy.
            val accepted = Regex("(?i)^(?:https?|rtmp|rtsp|p2p|mitv|mms)://.+$").matches(url) ||
                Regex("(?i)^.+\\.(?:m3u8|flv|mp4)(?:[?#].*)?$").matches(url)
            if (!accepted) return@mapIndexedNotNull null
            JSONObject().put("url", url).put("label", label).put("quality", "").put("resolution", "")
        }
    }

    /** Mirrors legacy parseJSONLive: accepts a top-level array or channels/data/list envelope. */
    fun parseJsonLive(input: String): List<JSONObject> {
        require(input.length <= MAX_INPUT_CHARS) { "LIVE_SOURCE_INPUT_TOO_LARGE" }
        val root: Any = try { org.json.JSONTokener(input.removePrefix("\uFEFF")).nextValue() }
        catch (_: Exception) { throw IllegalArgumentException("LIVE_SOURCE_JSON_INVALID") }
        val items = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("channels") ?: root.optJSONArray("data") ?: root.optJSONArray("list") ?: JSONArray()
            else -> JSONArray()
        }
        val result = mutableListOf<JSONObject>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val rawStreams = item.optJSONArray("streams") ?: item.optJSONArray("urls")
            val streams = JSONArray()
            if (rawStreams != null) {
                for (streamIndex in 0 until rawStreams.length()) {
                    val raw = rawStreams.opt(streamIndex)
                    val stream = when (raw) {
                        is String -> JSONObject().put("url", raw)
                        is JSONObject -> JSONObject(raw.toString()).apply {
                            if (!has("url") || optString("url").isBlank()) put("url", optString("mediaUrl"))
                            if (!has("label") || optString("label").isBlank()) put("label", optString("name"))
                        }
                        else -> null
                    }
                    if (stream != null && stream.optString("url").isNotBlank()) {
                        // Keep the legacy normalizeStream contract while retaining any extension fields.
                        if (!stream.has("label")) stream.put("label", "")
                        if (!stream.has("quality")) stream.put("quality", "")
                        if (!stream.has("resolution")) stream.put("resolution", "")
                        if (!stream.has("headers") || stream.isNull("headers")) stream.put("headers", JSONObject())
                        if (!stream.has("cookies") || stream.isNull("cookies")) stream.put("cookies", "")
                        if (!stream.has("referer") || stream.isNull("referer")) stream.put("referer", "")
                        if (!stream.has("userAgent") || stream.isNull("userAgent")) stream.put("userAgent", "")
                        if (!stream.has("expiresAt")) stream.put("expiresAt", JSONObject.NULL)
                        streams.put(stream)
                    }
                }
            } else if (item.optString("url").isNotBlank()) {
                streams.put(JSONObject().put("url", item.optString("url")))
            }
            val sourceItemId = firstNonBlank(item, "sourceItemId", "id", "channelId").ifBlank { "item-${index + 1}" }
            val canonicalId = firstNonBlank(item, "canonicalId", "globalId", "externalId")
            val channelKey = firstNonBlank(item, "channelKey", "id", "channelId")
            val name = firstNonBlank(item, "name", "title").ifBlank { "频道 ${index + 1}" }
            result += JSONObject()
                .put("sourceItemId", sourceItemId)
                .put("canonicalId", canonicalId)
                .put("channelKey", channelKey)
                .put("name", name)
                .put("logo", firstNonBlank(item, "logo", "icon"))
                .put("categoryId", firstNonBlank(item, "categoryId", "categoryKey", "category"))
                .put("category", firstNonBlank(item, "category", "group", "categoryName").ifBlank { "未分类" })
                .put("description", item.optString("description"))
                .put("sourceOrder", index)
                .put("streams", streams)
                .put("epg", item.optJSONArray("epg") ?: JSONArray())
                .put("currentProgram", item.opt("currentProgram") ?: JSONObject.NULL)
                .put("upcomingProgram", item.opt("upcomingProgram") ?: JSONObject.NULL)
        }
        return result
    }

    private fun firstNonBlank(item: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = item.optString(key).trim()
            if (value.isNotEmpty() && value != "null") return value
        }
        return ""
    }

    fun parseM3u(text: String): List<JSONObject> {
        val lines = normalizeInput(text).split(Regex("\\r?\\n")).map(String::trim)
        val channels = mutableListOf<JSONObject>()
        var pending: JSONObject? = null
        for (line in lines) {
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pending = parseExtInf(line)
                continue
            }
            if (line.startsWith("#")) continue
            val metadata = pending ?: continue
            val index = channels.size
            val title = metadata.optString("title").ifBlank { metadata.optString("tvg-name").ifBlank { "频道 ${index + 1}" } }
            val tvgId = metadata.optString("tvg-id")
            val tvgName = metadata.optString("tvg-name")
            val stream = JSONObject()
                .put("url", line)
                .put("label", tvgName.ifBlank { "主线路" })
                .put("quality", metadata.optString("tvg-quality"))
                .put("resolution", metadata.optString("tvg-resolution"))
            channels += JSONObject()
                .put("sourceItemId", tvgId.ifBlank { title.ifBlank { "item-${index + 1}" } })
                .put("canonicalId", tvgId)
                .put("channelKey", tvgId.ifBlank { title })
                .put("name", title)
                .put("logo", metadata.optString("tvg-logo"))
                .put("category", metadata.optString("group-title").ifBlank { "未分类" })
                .put("sourceOrder", index)
                .put("stream", stream)
            pending = null
        }
        return channels
    }

    /** Parses legacy XML live channel/source descriptors with a hardened XML parser. */
    fun parseXmlLive(text: String): List<JSONObject> {
        val document = parseSafeXml(text)
        val nodes = document.getElementsByTagName("channel")
        val result = ArrayList<JSONObject>(nodes.length)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) ?: continue
            val attrs = node.attributes
            fun attr(name: String): String = attrs?.getNamedItem(name)?.nodeValue.orEmpty()
            fun childText(name: String): String {
                val children = node.childNodes ?: return ""
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex) ?: continue
                    if (child.nodeName == name) return child.textContent?.trim().orEmpty()
                }
                return ""
            }
            val sourceItemId = attr("id").ifBlank { "item-${index + 1}" }
            val displayName = attr("display-name").ifBlank { childText("display-name") }.ifBlank { "频道 ${index + 1}" }
            val streams = JSONArray()
            val children = node.childNodes
            for (childIndex in 0 until children.length) {
                val child = children.item(childIndex) ?: continue
                if (child.nodeName !in setOf("stream", "url", "source")) continue
                val childAttrs = child.attributes
                fun streamAttr(name: String): String = childAttrs?.getNamedItem(name)?.nodeValue.orEmpty()
                val url = streamAttr("url").ifBlank { streamAttr("src") }.ifBlank { child.textContent?.trim().orEmpty() }
                if (url.isBlank()) continue
                val expiresAt = streamAttr("expires-at").ifBlank { streamAttr("expiresAt") }
                val stream = JSONObject()
                    .put("url", url)
                    .put("label", streamAttr("label").ifBlank { streamAttr("name") })
                    .put("quality", streamAttr("quality"))
                    .put("resolution", streamAttr("resolution"))
                    .put("protocol", streamAttr("protocol"))
                    .put("expiresAt", if (expiresAt.isBlank()) JSONObject.NULL else expiresAt)
                streamAttr("priority").toIntOrNull()?.let { stream.put("priority", it) }
                streams.put(stream)
            }
            val icon = (0 until (node.childNodes?.length ?: 0)).mapNotNull { node.childNodes.item(it) }
                .firstOrNull { it.nodeName == "icon" }?.attributes?.getNamedItem("src")?.nodeValue.orEmpty()
            result += JSONObject()
                .put("sourceItemId", sourceItemId)
                .put("canonicalId", attr("canonical-id").ifBlank { attr("global-id") }.ifBlank { attr("id") })
                .put("channelKey", attr("id"))
                .put("name", displayName)
                .put("logo", icon)
                .put("category", attr("group").ifBlank { childText("group") }.ifBlank { "未分类" })
                .put("sourceOrder", index)
                .put("streams", streams)
                .put("epg", JSONArray())
                .put("currentProgram", JSONObject.NULL)
                .put("upcomingProgram", JSONObject.NULL)
        }
        return result
    }

    /** Parses legacy XMLTV programme records with external entities and DTDs disabled. */
    fun parseXmlEpg(text: String): List<JSONObject> {
        require(text.length <= MAX_INPUT_CHARS) { "LIVE_SOURCE_INPUT_TOO_LARGE" }
        if (text.isBlank()) return emptyList()
        require(!Regex("<!\\s*DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "LIVE_EPG_XML_INVALID" }
        val document = parseSafeXml(text)
        val nodes = document.getElementsByTagName("programme")
        val result = ArrayList<JSONObject>(nodes.length)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) ?: continue
            val attrs = node.attributes
            fun attr(name: String): String = attrs?.getNamedItem(name)?.nodeValue.orEmpty()
            fun childText(name: String): String {
                val children = node.childNodes ?: return ""
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex) ?: continue
                    if (child.nodeName == name) return child.textContent?.trim().orEmpty()
                }
                return ""
            }
            val id = attr("id").ifBlank { "program-${index + 1}" }
            val start = attr("start")
            val end = attr("stop")
            val title = childText("title")
            result += JSONObject()
                .put("programId", id)
                .put("channelRef", attr("channel"))
                .put("startTime", start)
                .put("endTime", end)
                .put("startAt", start)
                .put("endAt", end)
                .put("name", title)
                .put("title", title)
                .put("description", childText("desc"))
                .put("category", attr("category"))
        }
        return result
    }

    private fun parseSafeXml(text: String): org.w3c.dom.Document {
        require(text.length <= MAX_INPUT_CHARS) { "LIVE_SOURCE_INPUT_TOO_LARGE" }
        require(!Regex("<!\\s*DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "LIVE_EPG_XML_INVALID" }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isXIncludeAware = false
        factory.setExpandEntityReferences(false)
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        return try {
            factory.newDocumentBuilder().parse(InputSource(StringReader(text.removePrefix("\uFEFF"))))
        } catch (_: Exception) {
            throw IllegalArgumentException("LIVE_EPG_XML_INVALID")
        }
    }

    private fun parseExtInf(line: String): JSONObject {
        val comma = line.indexOf(',')
        val metadataPart = if (comma >= 0) line.substring(0, comma) else line
        val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val attributes = JSONObject()
        val regex = Regex("([\\w-]+)=\"([^\"]*)\"")
        regex.findAll(metadataPart).forEach { match -> attributes.put(match.groupValues[1], match.groupValues[2]) }
        attributes.put("title", title.ifBlank { attributes.optString("tvg-name") })
        return attributes
    }

    private fun normalizeInput(text: String): String {
        require(text.length <= MAX_INPUT_CHARS) { "LIVE_SOURCE_INPUT_TOO_LARGE" }
        return text.removePrefix("\uFEFF")
    }
}
