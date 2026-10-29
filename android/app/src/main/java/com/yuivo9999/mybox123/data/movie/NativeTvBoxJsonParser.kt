package com.yuivo9999.mybox123.data.movie

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/**
 * Native counterpart of the existing JSON/TVBox VOD normalization path.
 * Unknown input fields are retained on every normalized item and episode.
 */
object NativeTvBoxJsonParser {
    const val MAX_JSON_CHARACTERS = 5 * 1024 * 1024
    private const val MAX_ITEMS = 10_000

    fun parse(rawValue: String, sourceId: String, sourceMetadata: JSONObject = JSONObject()): List<NativeMovieItem> {
        require(sourceId.isNotBlank()) { "MOVIE_SOURCE_ID_REQUIRED" }
        val trimmed = rawValue.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("TVBOX_EXT_JSON_EMPTY")
        if (trimmed.length > MAX_JSON_CHARACTERS) throw IllegalArgumentException("TVBOX_EXT_JSON_TOO_LARGE")

        val root = try {
            val tokener = JSONTokener(trimmed)
            val value = tokener.nextValue()
            if (tokener.nextClean() != '\u0000') throw JSONException("Trailing data")
            value
        } catch (error: JSONException) {
            throw IllegalArgumentException("TVBOX_EXT_JSON_INVALID", error)
        }
        val array = findItemArray(root) ?: throw IllegalArgumentException("TVBOX_EXT_JSON_NOT_VOD_DATA")
        if (array.length() > MAX_ITEMS) throw IllegalArgumentException("TVBOX_EXT_JSON_TOO_MANY_ITEMS")

        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(normalizeItem(sourceId, item, index, sourceMetadata))
            }
        }
    }

    fun extractTotal(rawValue: String): Long? {
        return try {
            val value = JSONTokener(rawValue.removePrefix("\uFEFF").trim()).nextValue()
            val root = value as? JSONObject ?: return null
            sequenceOf(root, root.optJSONObject("data"), root.optJSONObject("result"))
                .filterNotNull()
                .mapNotNull { candidate ->
                    val raw = candidate.opt("total") ?: candidate.opt("totalCount") ?: candidate.opt("count")
                    (raw as? Number)?.toLong() ?: raw?.toString()?.toLongOrNull()
                }
                .firstOrNull { it >= 0L }
        } catch (_: Exception) {
            null
        }
    }

    fun extractCategories(rawValue: String): List<NativeMovieCategory> {
        val root = try { JSONTokener(rawValue.removePrefix("\uFEFF").trim()).nextValue() as? JSONObject }
        catch (_: Exception) { null }
        if (root == null) return emptyList()
        val candidates = listOf(root.opt("class"), root.opt("classes"), root.opt("categories"), root.opt("type"), root.optJSONObject("data")?.opt("class"))
        val array = candidates.firstNotNullOfOrNull { it as? JSONArray } ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                val itemObject = item as? JSONObject
                val name = when {
                    itemObject != null -> firstNonBlank(itemObject, "type_name", "name", "label", "title")
                    item != null && item !== JSONObject.NULL -> item.toString().trim()
                    else -> ""
                }
                if (name.isBlank()) continue
                val id = if (itemObject == null) (index + 1).toString() else {
                    firstNonBlank(itemObject, "type_id", "typeId", "id", "value").ifBlank { (index + 1).toString() }
                }
                add(NativeMovieCategory(id = id, name = name, rawJson = itemObject?.toString() ?: JSONObject().put("name", name).toString()))
            }
        }
    }

    private fun findItemArray(value: Any?): JSONArray? {
        if (value is JSONArray) return value
        val root = value as? JSONObject ?: return null
        val directKeys = listOf("movies", "vod", "list", "data", "result")
        for (key in directKeys) {
            val candidate = root.opt(key)
            if (candidate is JSONArray) return candidate
        }
        root.optJSONObject("data")?.optJSONArray("list")?.let { return it }
        root.optJSONObject("result")?.optJSONArray("list")?.let { return it }
        return null
    }

    private fun normalizeItem(sourceId: String, source: JSONObject, index: Int, sourceMetadata: JSONObject): NativeMovieItem {
        val sourceItemId = firstNonBlank(source, "sourceItemId", "vod_id", "id").ifBlank { "item-${index + 1}" }
        val canonicalId = firstNonBlank(source, "canonicalId", "globalId", "externalId", "tmdbId", "imdbId", "vod_id", "id")
        val title = firstNonBlank(source, "title", "name", "vod_name").ifBlank { "内容 ${index + 1}" }
        val typeId = firstNonBlank(source, "type_id", "typeId")
        val category = firstNonBlank(source, "type_name", "category", "vod_class", "group", "categoryName")
        val rawDescription = firstNonNullString(source, "description", "desc", "vod_content")
        val description = rawDescription.replace(Regex("</?[^>]+(>|$)"), "").replace("&nbsp;", " ").trim()
        val actors = readActors(source)
        val contentId = "content:" + (canonicalId.takeIf { it.isNotBlank() }?.let { "canonical:${it.trim().lowercase(Locale.ROOT)}" } ?: "content:$sourceId:$sourceItemId")
        val episodes = parseEpisodes(source, sourceId, sourceItemId, contentId, sourceMetadata)
        val playbackMetadata = JSONObject()
        listOf(
            "sourceCapability", "adapterType", "tvboxAdapterKind", "tvboxRequiresJar", "tvboxJar",
            "tvboxType", "playerType", "headers", "userAgent", "referer", "cookies", "tvboxIJKProfiles", "tvboxParseConfig",
        ).forEach { key ->
            if (sourceMetadata.has(key) && !sourceMetadata.isNull(key)) playbackMetadata.put(key, sourceMetadata.opt(key))
        }

        val normalized = JSONObject(source.toString())
            .put("sourceId", sourceId)
            .put("sourceItemId", sourceItemId)
            .put("canonicalId", canonicalId)
            .put("contentId", contentId)
            .put("legacyContentId", "content:$sourceId:$sourceItemId")
            .put("title", title)
            .put("titleEn", firstNonBlank(source, "titleEn", "vod_en", "en"))
            .put("subtitle", firstNonBlank(source, "subtitle", "subTitle"))
            .put("type", firstNonBlank(source, "type", "contentType", "vod_type", "mediaType"))
            .put("sourceCategoryId", typeId)
            .put("sourceCategoryIds", JSONArray().apply { if (typeId.isNotBlank()) put(typeId) })
            .put("sourceCategoryName", category)
            .put("sourceCategoryNames", JSONArray().apply { if (category.isNotBlank()) put(category) })
            .put("category", category.ifBlank { "未分类" })
            .put("poster", firstNonBlank(source, "poster", "pic", "vod_pic", "image", "cover"))
            .put("backdrop", firstNonBlank(source, "backdrop", "vod_pic_slide", "pic_slide", "background"))
            .put("background", firstNonBlank(source, "background", "backdrop", "vod_pic_slide", "pic_slide"))
            .put("description", description)
            .put("year", firstNonBlank(source, "year", "vod_year", "releaseYear"))
            .put("region", firstNonBlank(source, "region", "area", "vod_area"))
            .put("director", readDirector(source))
            .put("actors", actors)
            .put("cast", actors)
            .put("popularity", readNumber(source, "popularity", "vod_hits"))
            .put("updateInfo", firstNonBlank(source, "updateInfo", "vod_remarks", "remarks", "vod_state", "note", "updateStatus", "status"))
            .put("rating", firstNonBlank(source, "rating", "vod_score", "vod_douban_score"))
            .put("episodes", episodes)
            .put("episodeCount", episodes.length())
            .put("totalEpisodes", episodes.length())
            .put("availableSourceCount", 1)
            .put("sourceRefs", JSONArray().put(JSONObject().put("sourceId", sourceId).put("sourceItemId", sourceItemId)))
            .put("sourceRelations", JSONArray().put(JSONObject().put("sourceId", sourceId).put("sourceItemId", sourceItemId)))
            .put("metadata", playbackMetadata)
            .put("syncAt", System.currentTimeMillis())

        return NativeMovieItem.fromJson(normalized)
    }

    private fun parseEpisodes(
        item: JSONObject,
        sourceId: String,
        sourceItemId: String,
        contentId: String,
        sourceMetadata: JSONObject,
    ): JSONArray {
        val rawEpisodes = item.opt("episodes")?.takeUnless { it === JSONObject.NULL }
            ?: item.opt("eps")?.takeUnless { it === JSONObject.NULL }
            ?: item.opt("playlists")?.takeUnless { it === JSONObject.NULL }
        val sourceValues = mutableListOf<JSONObject>()
        when (rawEpisodes) {
            is JSONArray -> for (index in 0 until rawEpisodes.length()) {
                val value = rawEpisodes.opt(index)
                when (value) {
                    is JSONObject -> sourceValues += JSONObject(value.toString())
                    is String -> sourceValues += JSONObject().put("title", value)
                }
            }
            is String -> if (rawEpisodes.isNotBlank()) {
                rawEpisodes.split(Regex("\\s*[|,]\\s*")).filter { it.isNotBlank() }.forEach { sourceValues += JSONObject().put("title", it) }
            }
        }

        if (sourceValues.isEmpty()) {
            val routeValues = splitRoutes(item.opt("vod_play_url"))
            if (routeValues.isNotEmpty()) {
                val routeLabels = splitRoutes(item.opt("vod_play_from"))
                routeValues.forEachIndexed { routeIndex, route ->
                    route.split('#').forEachIndexed { episodeIndex, rawEntry ->
                        val entry = rawEntry.trim()
                        if (entry.isEmpty()) return@forEachIndexed
                        val separator = entry.indexOf('$')
                        val title = if (separator < 0) entry else entry.substring(0, separator).trim().ifBlank { "第${episodeIndex + 1}集" }
                        val url = if (separator < 0) "" else entry.substring(separator + 1).trim()
                        val candidateArray = JSONArray()
                        if (url.isNotEmpty()) {
                            candidateArray.put(normalizeCandidate(
                                JSONObject().put("mediaUrl", url).put("label", routeLabels.getOrElse(routeIndex) { "线路${routeIndex + 1}" })
                                    .put("metadata", JSONObject().put("tvboxPlayFlag", routeLabels.getOrElse(routeIndex) { "线路${routeIndex + 1}" }).put("tvboxRouteIndex", routeIndex)),
                                sourceMetadata,
                                sourceId,
                            ))
                        }
                        sourceValues += JSONObject().put("title", title).put("episodeNumber", episodeIndex + 1).put("playbackCandidates", candidateArray)
                    }
                }
            }
        }

        if (sourceValues.isEmpty()) {
            val urls = item.optJSONArray("urls")
            if (urls != null) for (index in 0 until urls.length()) {
                val url = urls.optString(index).trim()
                if (url.isNotEmpty()) sourceValues += JSONObject().put("title", "第${index + 1}集")
                    .put("playbackCandidates", JSONArray().put(normalizeCandidate(JSONObject().put("mediaUrl", url), sourceMetadata, sourceId)))
            }
        }

        return JSONArray().apply {
            sourceValues.forEachIndexed { index, episode ->
                val episodeSourceItemId = firstNonBlank(episode, "sourceItemId", "id").ifBlank { "$sourceItemId:episode:${index + 1}" }
                val canonicalEpisodeId = firstNonBlank(episode, "canonicalEpisodeId", "globalId")
                val episodeId = if (canonicalEpisodeId.isNotBlank()) "episode:$contentId:canonical:${canonicalEpisodeId.trim().lowercase(Locale.ROOT)}"
                else "episode:$contentId:$sourceId:$episodeSourceItemId"
                val candidateSource = episode.optJSONArray("playbackCandidates") ?: JSONArray()
                val candidates = JSONArray()
                for (candidateIndex in 0 until candidateSource.length()) {
                    val candidate = candidateSource.optJSONObject(candidateIndex) ?: continue
                    candidates.put(normalizeCandidate(candidate, sourceMetadata, sourceId))
                }
                val directUrl = firstNonBlank(episode, "mediaUrl", "url")
                if (directUrl.isNotBlank() && (0 until candidates.length()).none { candidates.optJSONObject(it)?.optString("mediaUrl").orEmpty().isNotBlank() }) {
                    val directCandidate = JSONObject().put("mediaUrl", directUrl)
                    listOf("protocol", "label", "quality", "resolution", "headers", "cookies", "referer", "userAgent", "expiresAt")
                        .forEach { key -> if (episode.has(key)) directCandidate.put(key, episode.opt(key) ?: JSONObject.NULL) }
                    candidates.put(normalizeCandidate(directCandidate, sourceMetadata, sourceId))
                }
                val title = firstNonBlank(episode, "title", "name").ifBlank { "第${index + 1}集" }
                put(JSONObject(episode.toString())
                    .put("episodeId", episodeId)
                    .put("episodeIdentity", canonicalEpisodeId.takeIf { it.isNotBlank() }?.let { "canonical:${it.trim().lowercase(Locale.ROOT)}" } ?: "")
                    .put("contentId", contentId)
                    .put("sourceId", sourceId)
                    .put("sourceItemId", episodeSourceItemId)
                    .put("episodeNumber", episode.optInt("episodeNumber", episode.optInt("number", index + 1)))
                    .put("title", title)
                    .put("episodeTitle", title)
                    .put("description", firstNonBlank(episode, "description", "desc"))
                    .put("sourceRefs", JSONArray().put(JSONObject().put("sourceId", sourceId).put("sourceItemId", episodeSourceItemId)))
                    .put("sourceRelations", JSONArray().put(JSONObject().put("sourceId", sourceId).put("sourceItemId", episodeSourceItemId)))
                    .put("playbackCandidates", candidates))
            }
        }
    }

    private fun normalizeCandidate(input: JSONObject, sourceMetadata: JSONObject, sourceId: String): JSONObject {
        val candidate = JSONObject(input.toString())
        val mediaUrl = firstNonBlank(candidate, "mediaUrl", "url")
        if (mediaUrl.isNotBlank()) candidate.put("mediaUrl", mediaUrl)
        if (firstNonBlank(candidate, "sourceId").isBlank()) candidate.put("sourceId", sourceId)
        val mergedMetadata = JSONObject()
        listOf("sourceCapability", "adapterType", "tvboxAdapterKind", "tvboxRequiresJar", "tvboxJar", "tvboxType", "playerType", "headers", "userAgent", "referer", "cookies", "tvboxIJKProfiles", "tvboxParseConfig").forEach { key ->
            if (sourceMetadata.has(key) && !sourceMetadata.isNull(key)) mergedMetadata.put(key, sourceMetadata.opt(key))
        }
        candidate.optJSONObject("metadata")?.let { existing ->
            val keys = existing.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                mergedMetadata.put(key, existing.opt(key) ?: JSONObject.NULL)
            }
        }
        candidate.put("metadata", mergedMetadata)
        val headers = JSONObject()
        sourceMetadata.optJSONObject("headers")?.let { original ->
            val keys = original.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                headers.put(key, original.opt(key) ?: JSONObject.NULL)
            }
        }
        candidate.optJSONObject("headers")?.let { original ->
            val keys = original.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                headers.put(key, original.opt(key) ?: JSONObject.NULL)
            }
        }
        candidate.put("headers", headers)
        if (!candidate.has("userAgent") && sourceMetadata.has("userAgent")) candidate.put("userAgent", sourceMetadata.opt("userAgent"))
        if (!candidate.has("referer") && sourceMetadata.has("referer")) candidate.put("referer", sourceMetadata.opt("referer"))
        if (!candidate.has("cookies") && sourceMetadata.has("cookies")) candidate.put("cookies", sourceMetadata.opt("cookies"))
        return candidate
    }

    private fun readDirector(source: JSONObject): String {
        val direct = firstNonBlank(source, "director", "vod_director")
        if (direct.isNotBlank()) return direct
        val directors = source.optJSONArray("directors")
        return directors?.optString(0).orEmpty().trim()
    }

    private fun readActors(item: JSONObject): JSONArray {
        val actors = item.opt("actors")
        if (actors is JSONArray) return JSONArray(actors.toString())
        val raw = firstNonNullString(item, "vod_actor", "actors", "cast")
        return JSONArray().apply { raw.split(Regex("[,，/\\s]+")).map(String::trim).filter(String::isNotEmpty).forEach(::put) }
    }

    private fun splitRoutes(value: Any?): List<String> = when (value) {
        is JSONArray -> buildList(value.length()) { for (index in 0 until value.length()) value.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add) }
        is String -> value.split("\$\$\$").map(String::trim).filter(String::isNotEmpty)
        else -> emptyList()
    }

    private fun firstNonBlank(source: JSONObject, vararg keys: String): String = keys.asSequence()
        .map { key ->
            val value = source.opt(key)
            if (value == null || value === JSONObject.NULL) "" else value.toString().trim()
        }
        .firstOrNull(String::isNotEmpty).orEmpty()

    private fun firstNonNullString(source: JSONObject, vararg keys: String): String = keys.asSequence()
        .map { key -> source.opt(key) }
        .firstOrNull { it != null && it !== JSONObject.NULL }
        ?.toString().orEmpty()

    private fun readNumber(source: JSONObject, vararg keys: String): Double = keys.asSequence()
        .mapNotNull { key ->
            val value = source.opt(key)
            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }
        .firstOrNull { it.isFinite() } ?: 0.0
}

data class NativeMovieItem(
    val sourceId: String,
    val sourceItemId: String,
    val contentId: String,
    val title: String,
    val poster: String,
    val category: String,
    val description: String,
    val episodes: List<JSONObject>,
    val rawJson: String,
) {
    fun toJson(): JSONObject = JSONObject(rawJson)

    companion object {
        fun fromJson(json: JSONObject): NativeMovieItem {
            val rawEpisodes = json.optJSONArray("episodes") ?: JSONArray()
            val episodes = buildList(rawEpisodes.length()) {
                for (index in 0 until rawEpisodes.length()) {
                    rawEpisodes.optJSONObject(index)?.let { add(JSONObject(it.toString())) }
                }
            }
            return NativeMovieItem(
                sourceId = json.optString("sourceId"),
                sourceItemId = json.optString("sourceItemId"),
                contentId = json.optString("contentId"),
                title = json.optString("title"),
                poster = json.optString("poster"),
                category = json.optString("category"),
                description = json.optString("description"),
                episodes = episodes,
                rawJson = json.toString(),
            )
        }
    }
}

data class NativeMovieCategory(val id: String, val name: String, val rawJson: String)
