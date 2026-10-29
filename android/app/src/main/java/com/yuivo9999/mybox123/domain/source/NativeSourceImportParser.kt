package com.yuivo9999.mybox123.domain.source

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/**
 * Parses the source-file formats used by the legacy sourceConfigService.
 * It imports source definitions only; it never executes scripts embedded in a file.
 */
object NativeSourceImportParser {
    const val MAX_IMPORT_CHARS = 5 * 1024 * 1024

    fun parseText(text: String, fileName: String = "source"): List<JSONObject> {
        val raw = text.removePrefix("\uFEFF")
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "SOURCE_IMPORT_EMPTY" }
        require(raw.length <= MAX_IMPORT_CHARS) { "SOURCE_IMPORT_TOO_LARGE" }

        val safeName = fileName.trim().ifEmpty { "source" }
        val lowerName = safeName.lowercase(Locale.ROOT)
        val bundleId = "bundle_local_${stableHash(safeName.trim().lowercase(Locale.ROOT))}"
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".m3u") ||
            trimmed.contains("#genre#", ignoreCase = true) || trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
            val format = if (lowerName.endsWith(".m3u") || trimmed.startsWith("#EXTM3U", ignoreCase = true)) "m3u" else "txt"
            val name = safeName.substringBeforeLast('.', safeName).ifBlank { "本地直播源" }
            val sourceType = "live"
            val sourceId = "source_${stableHash("$bundleId|$sourceType|$name")}"
            val sourceRef = "local://${encodeComponent(name)}"
            return listOf(JSONObject()
                .put("sourceId", sourceId)
                .put("bundleId", bundleId)
                .put("sourceKey", "local|$sourceType|${stableHash(name)}")
                .put("name", name)
                .put("sourceType", sourceType)
                .put("sourceRef", sourceRef)
                .put("url", sourceRef)
                .put("localContent", raw)
                .put("localFormat", format)
                .put("liveMode", if (trimmed.contains("#genre#", ignoreCase = true)) "tv1" else "generic")
                .put("enabled", true)
                .put("status", "未测试")
                .put("createdAt", System.currentTimeMillis()))
        }

        val parsed = try {
            JSONTokener(cleanRelaxedJson(trimmed)).nextValue()
        } catch (_: Exception) {
            throw IllegalArgumentException("SOURCE_IMPORT_JSON_INVALID")
        }
        if (parsed is JSONArray) {
            // Round-trip source arrays produced by native/legacy source export without treating
            // them as a single local JSON feed. Arbitrary JSON arrays keep the legacy local-source path.
            val exportedSources = (0 until parsed.length()).mapNotNull { parsed.optJSONObject(it) }
            val isSourceConfigArray = parsed.length() > 0 && exportedSources.size == parsed.length() && exportedSources.all { item ->
                val type = item.optString("sourceType", "").trim().lowercase(Locale.ROOT)
                val id = item.optString("sourceId", item.optString("id", "")).trim()
                val ref = item.optString("sourceRef", item.optString("url", "")).trim()
                type in setOf("movie", "live") && id.isNotBlank() && ref.isNotBlank()
            }
            if (isSourceConfigArray) {
                return exportedSources.mapIndexed { index, item ->
                    val source = JSONObject(item.toString())
                    val sourceId = source.optString("sourceId", source.optString("id", "")).trim()
                    val type = source.optString("sourceType").trim().lowercase(Locale.ROOT)
                    val ref = source.optString("sourceRef", source.optString("url", "")).trim()
                    source.put("sourceId", sourceId)
                        .put("sourceType", type)
                        .put("sourceRef", ref)
                        .put("url", ref)
                        .put("bundleId", source.optString("bundleId").ifBlank { bundleId })
                        .put("sourceKey", source.optString("sourceKey").ifBlank { "$type|$sourceId" })
                        .put("order", source.optInt("order", index + 1))
                }
            }
            val name = safeName.substringBeforeLast('.', safeName).ifBlank { "本地影视源" }
            val sourceId = "source_${stableHash("$bundleId|movie|$name")}"
            val sourceRef = "local://${encodeComponent(name)}"
            return listOf(JSONObject()
                .put("sourceId", sourceId)
                .put("bundleId", bundleId)
                .put("sourceKey", "local|movie|${stableHash(name)}")
                .put("name", name)
                .put("sourceType", "movie")
                .put("sourceRef", sourceRef)
                .put("url", sourceRef)
                .put("localContent", parsed.toString())
                .put("localFormat", "json")
                .put("enabled", true)
                .put("status", "未测试")
                .put("createdAt", System.currentTimeMillis()))
        }
        if (parsed !is JSONObject) throw IllegalArgumentException("SOURCE_IMPORT_TVBOX_EMPTY")

        val result = mutableListOf<JSONObject>()
        val sites = parsed.optJSONArray("sites")
        if (sites != null) {
            for (index in 0 until sites.length()) {
                val site = sites.optJSONObject(index) ?: continue
                val api = site.optString("api", "").trim()
                val key = site.optString("key", "").trim().ifBlank { api.ifBlank { index.toString() } }
                val name = site.optString("name", "").trim().ifBlank { key.ifBlank { "TVBox影视-${index + 1}" } }
                val classification = "$name $key $api ${legacyJsString(site.opt("ext"))}"
                val type = if (classification.contains(Regex("直播|体育赛事|赛事直播|网红直播|310直播")) ||
                    classification.contains(Regex("(?:直播|sports|justlive)\\.(?:js|py|json)(?:$|[?#])", RegexOption.IGNORE_CASE))) "live" else "movie"
                val sourceIdentity = site.optString("key", "").ifBlank { api.ifBlank { index.toString() } }
                val sourceIdHash = stableHash("$bundleId|$type|$sourceIdentity")
                val sourceId = "tvbox_${type}_$sourceIdHash"
                val siteType = site.opt("type").let { rawType ->
                    when (rawType) {
                        is Number -> rawType.toDouble().takeIf { it.isFinite() } ?: 0.0
                        null, JSONObject.NULL -> 0.0
                        else -> rawType.toString().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
                    }
                }
                val normalizedTvboxType: Any = if (!site.has("type")) JSONObject.NULL else site.opt("type").let { rawType ->
                    when (rawType) {
                        null, JSONObject.NULL -> 0.0 // Legacy JS Number(null) === 0.
                        is Number -> rawType.toDouble().takeIf { it.isFinite() } ?: JSONObject.NULL
                        is Boolean -> if (rawType) 1.0 else 0.0
                        else -> rawType.toString().trim().let { value ->
                            if (value.isEmpty()) 0.0 else value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: JSONObject.NULL
                        }
                    }
                }
                val hasJar = site.has("jar") && !site.isNull("jar") && site.optString("jar", "").isNotBlank()
                val hasExt = site.has("ext") && !site.isNull("ext")
                val isDrpy = Regex("^drpy(?:_js)?_", RegexOption.IGNORE_CASE).containsMatchIn(site.optString("key", "")) ||
                    Regex("(?:^|/)drpy2(?:\\.min)?\\.js(?:$|[?#])", RegexOption.IGNORE_CASE).containsMatchIn(api)
                val isCsp = api.startsWith("csp_", true)
                val directVod = siteType == 1.0 && (api.startsWith("http://", true) || api.startsWith("https://", true)) &&
                    Regex("(?:api\\.php/)?provide/vod(?:/|\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(api)
                val kind = when { directVod && hasJar -> "http-vod-with-jar"; directVod -> "http-vod"; isDrpy -> "drpy-js"; isCsp -> "csp"; hasJar -> "jar"; hasExt -> "ext"; else -> "unknown" }
                val extValue = site.opt("ext")
                val extText = if (extValue is String) extValue.trim() else ""
                val safeExtJson = hasExt && ((extValue is JSONObject && hasVodArray(extValue)) ||
                    (extValue is String && ((extText.startsWith("{") || extText.startsWith("[")) ||
                    ((extText.startsWith("http://", true) || extText.startsWith("https://", true)) && Regex("\\.json(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(extText)))))
                val tvboxLiveProvider = type == "live" && !directVod
                val supportedHttp = type == "movie" && directVod
                val supportedExtJson = type == "movie" && kind == "ext" && safeExtJson
                val source = JSONObject()
                    .put("sourceId", sourceId)
                    .put("bundleId", bundleId)
                    .put("sourceKey", "$type|$key")
                    .put("name", name)
                    .put("sourceType", type)
                    .put("sourceRef", if (tvboxLiveProvider) "" else api)
                    .put("url", if (tvboxLiveProvider) "" else api)
                    .put("enabled", true)
                    .put("status", if (supportedHttp || supportedExtJson) "未测试" else "待适配")
                    .put("sourceCapability", when { tvboxLiveProvider -> "tvbox-live-provider"; directVod -> "direct-http-vod"; isDrpy -> "tvbox-drpy-js"; isCsp -> "tvbox-csp"; hasJar -> "tvbox-jar"; hasExt -> "tvbox-ext"; else -> "tvbox-extension" })
                    .put("adapterType", when { tvboxLiveProvider -> "tvbox-live-extension"; directVod -> "http-vod"; else -> "tvbox-extension" })
                    .put("tvboxLiveProvider", tvboxLiveProvider)
                    .put("tvboxAdapterKind", kind)
                    .put("tvboxRequiresJar", hasJar)
                    .put("tvboxType", normalizedTvboxType)
                    .put("tvboxKey", site.optString("key", "").trim())
                    .put("tvboxApi", api)
                    .put("tvboxDefinition", JSONObject(site.toString()))
                    .put("tvboxUnsupportedReason", if (supportedHttp || supportedExtJson) JSONObject.NULL else "导入已保留定义；扩展类型/能力需由对应原生运行时验证")
                    .put("createdAt", System.currentTimeMillis())
                if (hasExt) {
                    val format = when {
                        extValue is JSONObject && hasVodArray(extValue) -> "json-vod"
                        extValue is JSONObject -> "config-object"
                        extValue is JSONArray -> "unknown"
                        extValue !is String -> "unknown"
                        extText.startsWith("http://", true) || extText.startsWith("https://", true) -> when {
                            Regex("\\.json(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(extText) -> "remote-json"
                            Regex("\\.(?:js|mjs)(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(extText) -> "javascript"
                            Regex("\\.py(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(extText) -> "python"
                            else -> "remote-resource"
                        }
                        extText.startsWith("{") || extText.startsWith("[") -> "inline-json"
                        Regex("^[A-Za-z0-9+/=_-]{24,}$").matches(extText) -> "opaque-string"
                        else -> "inline-script"
                    }
                    source.put("tvboxExtFormat", format)
                }
                val parseConfig = normalizeTVBoxParseConfig(parsed)
                if (parseConfig.length() > 0) source.put("tvboxParseConfig", parseConfig)
                val ijkProfiles = normalizeTVBoxIJKProfiles(parsed.optJSONArray("ijk"))
                if (ijkProfiles.length() > 0) source.put("tvboxIJKProfiles", ijkProfiles)
                if (site.has("jar")) source.put("tvboxJar", site.opt("jar"))
                if (site.has("ext")) source.put("tvboxExt", site.opt("ext"))
                normalizeCategories(site.opt("categories"))?.let { source.put("categories", it) }
                result += source
            }
        }
        val lives = parsed.optJSONArray("lives")
        if (lives != null) {
            for (index in 0 until lives.length()) {
                val live = lives.optJSONObject(index) ?: continue
                val name = live.optString("name", "").trim().ifBlank { "TVBox直播-${index + 1}" }
                val refs = linkedSetOf<String>()
                addHttpRefs(refs, live.optString("url", ""))
                live.optJSONArray("urls")?.let { array -> for (i in 0 until array.length()) addHttpRefs(refs, array.optString(i, "")) }
                live.optJSONArray("channels")?.let { channels ->
                    for (i in 0 until channels.length()) {
                        val channel = channels.optJSONObject(i) ?: continue
                        addHttpRefs(refs, channel.optString("url", ""))
                        channel.optJSONArray("urls")?.let { urls -> for (j in 0 until urls.length()) addHttpRefs(refs, urls.optString(j, "")) }
                    }
                }
                refs.forEachIndexed { refIndex, ref ->
                    val key = live.optString("key", "").ifBlank { live.optString("name", "").ifBlank { index.toString() } }
                    val liveSource = JSONObject()
                        .put("sourceId", "tvbox_live_${stableHash("$bundleId|live|$key|$ref")}")
                        .put("bundleId", bundleId)
                        .put("sourceKey", "live|$key|$ref")
                        .put("name", if (refs.size > 1) "$name · 线路 ${refIndex + 1}" else name)
                        .put("sourceType", "live")
                        .put("sourceCapability", "direct-live")
                        .put("adapterType", "live-reference")
                        .put("sourceRef", ref)
                        .put("url", ref)
                        .put("liveMode", if (Regex("\\.txt(?:[?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(ref)) "tv1" else "generic")
                        .put("enabled", true)
                        .put("status", "未测试")
                        .put("createdAt", System.currentTimeMillis())
                    val liveProfiles = normalizeTVBoxIJKProfiles(parsed.optJSONArray("ijk"))
                    if (liveProfiles.length() > 0) liveSource.put("tvboxIJKProfiles", liveProfiles)
                    val liveParseConfig = normalizeTVBoxParseConfig(parsed)
                    if (liveParseConfig.length() > 0) liveSource.put("tvboxParseConfig", liveParseConfig)
                    result += liveSource
                }
            }
        }
        if (result.isEmpty()) throw IllegalArgumentException("SOURCE_IMPORT_TVBOX_EMPTY")
        return result
    }

    /** Mirrors legacy normalizeTVBoxParseConfig without enabling execution of imported rules. */
    private fun normalizeTVBoxParseConfig(parsed: JSONObject): JSONObject {
        val result = JSONObject()
        val parses = JSONArray()
        parsed.optJSONArray("parses")?.let { values ->
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val name = item.optString("name", "").trim().ifBlank { "解析-${index + 1}" }
                val url = item.optString("url", "").trim()
                val typeRaw = item.opt("type")
                val type = when (typeRaw) {
                    is Number -> typeRaw.toInt()
                    null, JSONObject.NULL -> 0
                    else -> typeRaw.toString().toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt() ?: 0
                }
                val ext = JSONObject()
                item.optJSONObject("ext")?.let { rawExt ->
                    val flags = JSONArray()
                    rawExt.optJSONArray("flag")?.let { list ->
                        for (i in 0 until list.length()) list.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { flags.put(it) }
                    }
                    val header = rawExt.optJSONObject("header") ?: JSONObject()
                    ext.put("flag", flags).put("header", JSONObject(header.toString()))
                }
                parses.put(JSONObject().put("id", item.optString("name", "").trim().ifBlank { "parse-${index + 1}" })
                    .put("name", name).put("type", type).put("url", url).put("ext", ext))
            }
        }
        if (parses.length() > 0) result.put("parses", parses)

        val flags = JSONArray()
        val seenFlags = linkedSetOf<String>()
        parsed.optJSONArray("flags")?.let { values ->
            for (index in 0 until values.length()) {
                val flag = values.optString(index, "").replace(Regex("\\r?\\n"), "").trim()
                if (flag.isNotEmpty() && seenFlags.add(flag)) flags.put(flag)
            }
        }
        if (flags.length() > 0) result.put("flags", flags)

        val rules = JSONArray()
        parsed.optJSONArray("rules")?.let { values ->
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val rule = normalizeStringArray(item.optJSONArray("rule"))
                val filter = normalizeStringArray(item.optJSONArray("filter"))
                val host = item.optString("host", "").trim()
                if (host.isNotEmpty() || rule.length() > 0) rules.put(JSONObject().put("host", host).put("rule", rule).put("filter", filter))
            }
        }
        if (rules.length() > 0) result.put("rules", rules)

        val ads = JSONArray()
        parsed.optJSONArray("ads")?.let { values ->
            for (index in 0 until values.length()) values.optString(index, "").trim().takeIf { it.isNotEmpty() }?.let { ads.put(it) }
        }
        if (ads.length() > 0) result.put("ads", ads)

        val doh = JSONArray()
        parsed.optJSONArray("doh")?.let { values ->
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val name = item.optString("name", "").trim()
                val url = item.optString("url", "").trim()
                val hosts = normalizeStringArray(item.optJSONArray("hosts"))
                val ips = normalizeStringArray(item.optJSONArray("ips"))
                val regex = normalizeStringArray(item.optJSONArray("regex"))
                if (name.isNotEmpty() || url.isNotEmpty() || hosts.length() > 0) {
                    doh.put(JSONObject().put("name", name).put("url", url).put("hosts", hosts).put("ips", ips).put("regex", regex))
                }
            }
        }
        if (doh.length() > 0) result.put("doh", doh)
        return result
    }

    /** Mirrors legacy normalizeTVBoxIJKProfiles: object keyed by group, valid options only. */
    private fun normalizeTVBoxIJKProfiles(profiles: JSONArray?): JSONObject {
        val result = JSONObject()
        if (profiles == null) return result
        for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            val group = profile.optString("group", "").trim()
            if (group.isEmpty()) continue
            val options = JSONArray()
            profile.optJSONArray("options")?.let { values ->
                for (optionIndex in 0 until values.length()) {
                    val option = values.optJSONObject(optionIndex) ?: continue
                    val category = option.opt("category").let { raw ->
                        when (raw) {
                            is Number -> raw.toDouble().takeIf { it.isFinite() }?.toInt()
                            null, JSONObject.NULL -> null
                            else -> raw.toString().toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
                        }
                    }
                    val name = option.optString("name", "").trim()
                    val value = if (!option.has("value") || option.isNull("value")) "" else option.opt("value").toString()
                    if (category != null && category in 1..4 && name.isNotEmpty() && value.isNotEmpty()) {
                        options.put(JSONObject().put("category", category).put("name", name).put("value", value))
                    }
                }
            }
            if (options.length() > 0) result.put(group, options)
        }
        return result
    }

    /** Legacy TVBox configs may provide categories as either an array or a delimited string. */
    private fun normalizeCategories(value: Any?): JSONArray? {
        if (value is JSONArray) return JSONArray().apply {
            for (index in 0 until value.length()) value.optString(index, "").trim().takeIf { it.isNotEmpty() }?.let { put(it) }
        }
        if (value is String) {
            if (value.isBlank()) return null // Legacy parser omits an empty categories string.
            return JSONArray().apply {
                value.split(',', '，', '#', '|').map(String::trim).filter(String::isNotEmpty).forEach { put(it) }
            }
        }
        return null
    }

    private fun normalizeStringArray(values: JSONArray?): JSONArray = JSONArray().apply {
        if (values == null) return@apply
        for (index in 0 until values.length()) {
            val value = values.optString(index, "").trim()
            if (value.isNotEmpty()) put(value)
        }
    }

    private fun hasVodArray(value: JSONObject): Boolean {
        val keys = listOf("movies", "vod", "list", "data", "result")
        if (keys.any { value.optJSONArray(it) != null }) return true
        val data = value.optJSONObject("data")
        if (data?.optJSONArray("list") != null) return true
        val result = value.optJSONObject("result")
        return result?.optJSONArray("list") != null
    }

    /** Matches JavaScript String(value) for values used only in legacy source classification. */
    private fun legacyJsString(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject -> "[object Object]"
        is JSONArray -> (0 until value.length()).joinToString(",") { index -> legacyJsString(value.opt(index)) }
        else -> value.toString()
    }

    private fun addHttpRefs(target: MutableSet<String>, raw: String) {
        val value = raw.trim()
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) target += value
        else if (value.startsWith("proxy://", true)) {
            // Capture only the ext parameter. A greedy match previously swallowed later proxy
            // options (e.g. &type=0), making an otherwise valid encoded URL unusable.
            val encoded = Regex("(?:[?&]|^)ext=([^&]*)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)
            val decoded = encoded?.let { runCatching { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
            if (decoded?.startsWith("http://", true) == true || decoded?.startsWith("https://", true) == true) target += decoded
        }
    }

    private fun cleanRelaxedJson(input: String): String {
        val noComments = StringBuilder(input.length)
        var inString = false
        var escaped = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val next = input.getOrNull(i + 1)
            if (inString) {
                noComments.append(when { c == '\n' -> "\\n"; c == '\r' -> "\\r"; c == '\t' -> "\\t"; c.code < 0x20 -> " "; else -> c })
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
                i++
            } else if (c == '"') {
                inString = true; noComments.append(c); i++
            } else if (c == '/' && next == '/') {
                i += 2; while (i < input.length && input[i] != '\n' && input[i] != '\r') i++
            } else if (c == '/' && next == '*') {
                i += 2
                while (i + 1 < input.length && !(input[i] == '*' && input[i + 1] == '/')) i++
                i = (i + 2).coerceAtMost(input.length)
            } else { noComments.append(c); i++ }
        }
        val braceTrimmed = trimMismatchedBraces(noComments.toString())
        val out = StringBuilder(braceTrimmed.length)
        inString = false; escaped = false; i = 0
        while (i < braceTrimmed.length) {
            val c = braceTrimmed[i]
            if (inString) {
                out.append(c)
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
                i++
            } else if (c == '"') { inString = true; out.append(c); i++ }
            else if (c == ',') {
                var j = i + 1
                while (j < braceTrimmed.length && braceTrimmed[j].isWhitespace()) j++
                if (j < braceTrimmed.length && (braceTrimmed[j] == '}' || braceTrimmed[j] == ']')) i++ else { out.append(c); i++ }
            } else { out.append(c); i++ }
        }
        return out.toString()
    }

    /** Legacy recovery keeps the first complete top-level JSON value and drops trailing garbage. */
    private fun trimMismatchedBraces(input: String): String {
        var balance = 0
        var inString = false
        var escaped = false
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (inString) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') inString = false
                index++
                continue
            }
            when (char) {
                '"' -> inString = true
                '{', '[' -> balance++
                '}', ']' -> {
                    balance--
                    if (balance == 0) return input.substring(0, index + 1)
                }
            }
            index++
        }
        return input
    }

    private fun stableHash(value: String): String {
        var hash = 2166136261L
        value.forEach { char -> hash = ((hash xor char.code.toLong()) * 16777619L) and 0xffffffffL }
        return hash.toString(36)
    }

    private fun encodeComponent(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
