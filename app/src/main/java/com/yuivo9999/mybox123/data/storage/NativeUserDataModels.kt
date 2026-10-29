package com.yuivo9999.mybox123.data.storage

import org.json.JSONObject
import java.util.UUID

typealias HistoryRecord = PlaybackHistoryRecord

data class FavoritePresentation(
    val record: FavoriteRecord,
    val title: String,
    val subtitle: String,
    val isLive: Boolean
)

data class FavoriteRecord(
    val favoriteId: String = "",
    val targetType: String = "",
    val targetId: Any? = null,
    val kind: String = "",
    val title: String = "",
    val subtitle: String = "",
    val name: String = "",
    val sourceId: String = "",
    val sourceItemId: String = "",
    val sourceName: String = "",
    val createdAt: Long = 0L,
    val lastAccessedAt: Long = 0L,
    val rawJson: String = "{}"
) {
    fun toPresentation(): FavoritePresentation {
        val isLive = targetType.equals("channel", true) || targetType.equals("live", true) || toJson().optString("kind").equals("live", true)
        val displayTitle = title.ifBlank { name }.ifBlank { "未命名" }
        val displaySubtitle = subtitle.ifBlank { sourceName }.ifBlank { if (isLive) "直播" else "影视" }
        return FavoritePresentation(
            record = this,
            title = displayTitle,
            subtitle = displaySubtitle,
            isLive = isLive
        )
    }

    fun toJson(): JSONObject = try {
        val j = JSONObject(rawJson)
        j.put("favoriteId", favoriteId)
        j.put("targetType", targetType)
        j.put("targetId", targetId)
        j.put("title", title)
        j.put("subtitle", subtitle)
        j.put("name", name)
        j.put("sourceId", sourceId)
        j.put("sourceItemId", sourceItemId)
        j.put("sourceName", sourceName)
        j.put("createdAt", createdAt)
        j.put("lastAccessedAt", lastAccessedAt)
        j
    } catch (_: Exception) {
        JSONObject().put("title", title).put("targetId", targetId)
    }

    companion object {
        fun fromJson(json: JSONObject): FavoriteRecord {
            return FavoriteRecord(
                favoriteId = json.optString("favoriteId"),
                targetType = json.optString("targetType"),
                targetId = json.opt("targetId"),
                kind = json.optString("kind"),
                title = json.optString("title").ifBlank { json.optString("name") },
                subtitle = json.optString("subtitle").ifBlank { json.optString("sourceName") },
                name = json.optString("name"),
                sourceId = json.optString("sourceId"),
                sourceItemId = json.optString("sourceItemId"),
                sourceName = json.optString("sourceName"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                lastAccessedAt = json.optLong("lastAccessedAt", System.currentTimeMillis()),
                rawJson = json.toString()
            )
        }
    }
}

data class PlaybackHistoryRecord(
    val historyId: String = "",
    val targetType: String = "content",
    val targetId: Any? = null,
    val contentId: Any? = null,
    val title: String = "",
    val poster: String = "",
    val episodeIndex: Int = 0,
    val episodeTitle: String = "",
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val lastPlayedAt: Long = 0L,
    val sourceId: String = "",
    val sourceItemId: String = "",
    val rawJson: String = "{}"
) {
    fun toJson(): JSONObject = try {
        val j = JSONObject(rawJson)
        j.put("historyId", historyId)
        j.put("targetType", targetType)
        j.put("targetId", targetId)
        j.put("contentId", contentId)
        j.put("title", title)
        j.put("poster", poster)
        j.put("episodeIndex", episodeIndex)
        j.put("episodeTitle", episodeTitle)
        j.put("positionSeconds", positionSeconds)
        j.put("durationSeconds", durationSeconds)
        j.put("lastPlayedAt", lastPlayedAt)
        j.put("sourceId", sourceId)
        j.put("sourceItemId", sourceItemId)
        j
    } catch (_: Exception) {
        JSONObject().put("title", title)
    }

    companion object {
        fun fromJson(json: JSONObject): PlaybackHistoryRecord {
            return PlaybackHistoryRecord(
                historyId = json.optString("historyId"),
                targetType = json.optString("targetType", "content"),
                targetId = json.opt("targetId"),
                contentId = json.opt("contentId"),
                title = json.optString("title"),
                poster = json.optString("poster"),
                episodeIndex = json.optInt("episodeIndex", 0),
                episodeTitle = json.optString("episodeTitle"),
                positionSeconds = json.optDouble("positionSeconds", 0.0),
                durationSeconds = json.optDouble("durationSeconds", 0.0),
                lastPlayedAt = json.optLong("lastPlayedAt", System.currentTimeMillis()),
                sourceId = json.optString("sourceId"),
                sourceItemId = json.optString("sourceItemId"),
                rawJson = json.toString()
            )
        }
    }
}

data class SearchRecord(
    val searchId: String = UUID.randomUUID().toString(),
    val keyword: String,
    val count: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val searchedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("searchId", searchId)
        .put("keyword", keyword)
        .put("count", count)
        .put("createdAt", createdAt)
        .put("lastUsedAt", lastUsedAt)
        .put("searchedAt", searchedAt)

    companion object {
        fun fromJson(json: JSONObject): SearchRecord {
            val sId = json.optString("searchId").ifBlank { UUID.randomUUID().toString() }
            val now = System.currentTimeMillis()
            return SearchRecord(
                searchId = sId,
                keyword = json.optString("keyword"),
                count = json.optInt("count", 1),
                createdAt = json.optLong("createdAt", now),
                lastUsedAt = json.optLong("lastUsedAt", now),
                searchedAt = json.optLong("searchedAt", now)
            )
        }
    }
}

data class ProgressRecord(
    val contentId: Any? = null,
    val episodeIndex: Int = 0,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("contentId", contentId)
        .put("episodeIndex", episodeIndex)
        .put("positionSeconds", positionSeconds)
        .put("durationSeconds", durationSeconds)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): ProgressRecord {
            return ProgressRecord(
                contentId = json.opt("contentId"),
                episodeIndex = json.optInt("episodeIndex", 0),
                positionSeconds = json.optDouble("positionSeconds", 0.0),
                durationSeconds = json.optDouble("durationSeconds", 0.0),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}
