package com.yuivo9999.mybox123.data.storage

import org.json.JSONArray
import org.json.JSONObject

/** Typed view of a favorite row; raw JSON is retained to preserve fields added by newer/older clients. */
data class FavoriteRecord(
    val favoriteId: String,
    val targetType: String,
    val targetId: Any?,
    val createdAt: Long,
    val lastAccessedAt: Long,
    private val rawJson: String,
) {
    fun toJson(): JSONObject = JSONObject(rawJson)
        .put("favoriteId", favoriteId)
        .put("targetType", targetType)
        .put("targetId", targetId ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("lastAccessedAt", lastAccessedAt)

    companion object {
        fun fromJson(json: JSONObject): FavoriteRecord {
            val targetId = json.optNullable("targetId") ?: json.optNullable("id")
            val targetType = json.stringOrEmpty("targetType").ifEmpty {
                if (json.stringOrEmpty("kind") == "live") "channel" else "content"
            }
            val createdAt = json.optLong("createdAt", json.optLong("at", 0L))
            val id = json.stringOrEmpty("favoriteId").ifEmpty { "favorite:$targetType:${targetId ?: "unknown"}" }
            return FavoriteRecord(id, targetType, targetId, createdAt, json.optLong("lastAccessedAt", createdAt), json.toString())
        }
    }
}

data class HistoryRecord(
    val historyId: String,
    val targetType: String,
    val targetId: Any?,
    val episodeId: String,
    val sourceId: Any?,
    val sourceItemId: Any?,
    val lastPlayedAt: Long,
    val completed: Boolean,
    private val rawJson: String,
) {
    fun toJson(): JSONObject = JSONObject(rawJson)
        .put("historyId", historyId)
        .put("targetType", targetType)
        .put("targetId", targetId ?: JSONObject.NULL)
        .put("episodeId", episodeId)
        .put("sourceId", sourceId ?: JSONObject.NULL)
        .put("sourceItemId", sourceItemId ?: JSONObject.NULL)
        .put("lastPlayedAt", lastPlayedAt)
        .put("completed", completed)

    companion object {
        fun fromJson(json: JSONObject): HistoryRecord {
            val targetId = json.optNullable("targetId") ?: json.optNullable("id")
            val targetType = json.stringOrEmpty("targetType").ifEmpty {
                if (json.stringOrEmpty("kind") == "live") "channel" else "content"
            }
            val episodeId = json.stringOrEmpty("episodeId").ifEmpty {
                val episode = json.opt("episode")
                if (episode is Number) "legacy-episode:$targetId:${episode.toInt()}" else ""
            }
            val playedAt = json.optLong("lastPlayedAt", json.optLong("at", 0L))
            val id = json.stringOrEmpty("historyId").ifEmpty { "history:$targetType:${targetId ?: "unknown"}:${episodeId.ifEmpty { "live" }}" }
            return HistoryRecord(
                id, targetType, targetId, episodeId, json.optNullable("sourceId"), json.optNullable("sourceItemId"),
                playedAt, json.optBoolean("completed", false), json.toString(),
            )
        }
    }
}

data class PlaybackProgressRecord(
    val progressId: String,
    val contentId: Any?,
    val episodeId: String,
    val positionSeconds: Double,
    val durationSeconds: Double?,
    val updatedAt: Long,
    val completed: Boolean,
    private val rawJson: String,
) {
    fun toJson(): JSONObject = JSONObject(rawJson)
        .put("progressId", progressId)
        .put("contentId", contentId ?: JSONObject.NULL)
        .put("episodeId", episodeId)
        .put("positionSeconds", positionSeconds)
        .put("durationSeconds", durationSeconds ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)
        .put("completed", completed)

    companion object {
        fun fromJson(json: JSONObject): PlaybackProgressRecord {
            val contentId = json.optNullable("contentId") ?: json.optNullable("id")
            val episodeId = json.stringOrEmpty("episodeId").ifEmpty {
                val episode = json.opt("episode")
                if (episode is Number) "legacy-episode:$contentId:${episode.toInt()}" else ""
            }
            val id = json.stringOrEmpty("progressId").ifEmpty { "progress:${contentId ?: "unknown"}:${episodeId.ifEmpty { "content" }}" }
            val durationValue = json.optNullable("durationSeconds")
            val duration = (durationValue as? Number)?.toDouble()
            return PlaybackProgressRecord(
                id, contentId, episodeId, json.optDouble("positionSeconds", json.optDouble("progress", 0.0)).coerceAtLeast(0.0),
                duration, json.optLong("updatedAt", json.optLong("at", 0L)), json.optBoolean("completed", false), json.toString(),
            )
        }
    }
}

data class SearchRecord(
    val searchId: String,
    val keyword: String,
    val searchedAt: Long,
    val count: Int,
    private val rawJson: String,
) {
    fun toJson(): JSONObject = JSONObject(rawJson)
        .put("searchId", searchId)
        .put("keyword", keyword)
        .put("searchedAt", searchedAt)
        .put("count", count)

    companion object {
        fun fromJson(json: JSONObject): SearchRecord {
            val keyword = json.stringOrEmpty("keyword").ifEmpty { json.stringOrEmpty("query") }
            val encodedKeyword = java.net.URLEncoder.encode(keyword.trim().lowercase(), "UTF-8")
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%7E", "~")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
            val id = json.stringOrEmpty("searchId").ifEmpty { "search:$encodedKeyword" }
            return SearchRecord(id, keyword, json.optLong("searchedAt", json.optLong("at", 0L)), json.optInt("count", 1), json.toString())
        }
    }
}

/** Defaults mirrored from the existing web application's user-data model. */
object NativeUserSettings {
    private val allowedFallbackEngines = listOf("hls", "native", "mpegts")

    fun defaults(): JSONObject = JSONObject()
        .put("initialized", false)
        .put("autoplayResume", true)
        .put("defaultMovieSource", JSONObject.NULL)
        .put("defaultLiveSource", JSONObject.NULL)
        .put("theme", "sangtian")
        .put("fontSize", "medium")
        .put("fontFamily", "system")
        .put("cardStyle", "poster")
        .put("density", "comfortable")
        .put("playback", defaultPlaybackSettings())

    fun normalize(input: JSONObject): JSONObject {
        val normalized = defaults()
        val keys = input.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            normalized.put(key, input.opt(key) ?: JSONObject.NULL)
        }
        if (normalized.stringOrEmpty("fontFamily").isBlank()) normalized.put("fontFamily", "system")

        val playbackInput = input.optJSONObject("playback") ?: JSONObject()
        val playback = defaultPlaybackSettings()
        val playbackKeys = playbackInput.keys()
        while (playbackKeys.hasNext()) {
            val key = playbackKeys.next()
            playback.put(key, playbackInput.opt(key) ?: JSONObject.NULL)
        }
        val requestedOrder = playbackInput.optJSONArray("fallbackOrder") ?: JSONArray()
        val mergedOrder = linkedSetOf<String>()
        for (index in 0 until requestedOrder.length()) {
            val value = requestedOrder.optString(index)
            if (value in allowedFallbackEngines) mergedOrder += value
        }
        mergedOrder.addAll(allowedFallbackEngines)
        playback.put("moviePlayer", "html5")
        playback.put("livePlayer", "html5")
        playback.put("moviePlaybackScheme", normalizeScheme(
            playbackInput.stringOrEmpty("moviePlaybackScheme"), playbackInput.stringOrEmpty("moviePlayer"),
            playbackInput.optJSONObject("decoder")?.stringOrEmpty(playbackInput.stringOrEmpty("moviePlayer")).orEmpty(),
            "movie",
        ))
        playback.put("livePlaybackScheme", normalizeScheme(
            playbackInput.stringOrEmpty("livePlaybackScheme"), playbackInput.stringOrEmpty("livePlayer"),
            playbackInput.optJSONObject("decoder")?.stringOrEmpty(playbackInput.stringOrEmpty("livePlayer")).orEmpty(),
            "live",
        ))
        playback.put("fallbackEnabled", playbackInput.optBoolean("fallbackEnabled", true))
        playback.put("fallbackOrder", JSONArray(mergedOrder.toList()))
        playback.put("decoder", JSONObject().put("html5", "browser_auto"))
        normalized.put("playback", playback)
        return normalized
    }

    private fun normalizeScheme(
        requestedValue: String,
        legacyPlayerValue: String,
        legacyDecoderValue: String,
        scope: String,
    ): String {
        val requested = requestedValue.trim().lowercase()
        val valid = setOf("html5_auto", "hls_lowlatency", "html5_hardware", "hls_worker")
        if (requested in valid) return requested
        if (requested.matches(Regex("^(ijk|exo)_(hardware|software)$"))) {
            return if (scope == "live") "hls_lowlatency" else "html5_auto"
        }
        val legacy = legacyPlayerValue.trim().lowercase()
        if (legacy == "ijk" || legacy == "exo") return if (scope == "live") "hls_lowlatency" else "html5_auto"
        if (requested.isEmpty() && legacy.isEmpty()) return if (scope == "live") "hls_lowlatency" else "html5_auto"
        return when (legacyDecoderValue.trim().lowercase()) {
            "hls_lowlatency" -> "hls_lowlatency"
            "hls_worker" -> "hls_worker"
            "native_hardware", "hardware" -> "html5_hardware"
            else -> "html5_auto"
        }
    }

    fun selectedSourcesDefaults(): JSONObject = JSONObject()
        .put("movie", JSONObject.NULL)
        .put("live", JSONObject.NULL)

    private fun defaultPlaybackSettings(): JSONObject = JSONObject()
        .put("moviePlayer", "html5")
        .put("livePlayer", "html5")
        .put("moviePlaybackScheme", "html5_auto")
        .put("livePlaybackScheme", "hls_lowlatency")
        .put("fallbackEnabled", true)
        .put("fallbackOrder", JSONArray(allowedFallbackEngines))
        .put("decoder", JSONObject().put("html5", "browser_auto"))
}

private fun JSONObject.stringOrEmpty(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key, "")

private fun JSONObject.optNullable(key: String): Any? =
    if (!has(key) || isNull(key)) null else opt(key)
