package com.yuivo9999.mybox123.data.movie

import org.json.JSONObject

data class NativeMovieCategory(
    val id: String,
    val name: String,
    val rawJson: String = "{}"
) {
    fun toJson(): JSONObject = try {
        JSONObject(rawJson).put("id", id).put("name", name)
    } catch (_: Exception) {
        JSONObject().put("id", id).put("name", name)
    }

    companion object {
        fun fromJson(json: JSONObject): NativeMovieCategory {
            return NativeMovieCategory(
                id = json.optString("id").ifBlank { json.optString("type_id") },
                name = json.optString("name").ifBlank { json.optString("type_name") },
                rawJson = json.toString()
            )
        }
    }
}

data class NativeMovieItem(
    val sourceId: String = "",
    val sourceItemId: String = "",
    val contentId: String = "",
    val title: String = "",
    val poster: String = "",
    val category: String = "",
    val sourceCategoryName: String = "",
    val year: String = "",
    val region: String = "",
    val rating: String = "",
    val updateInfo: String = "",
    val backdrop: String = "",
    val description: String = "",
    val director: String = "",
    val actors: List<String> = emptyList(),
    val episodes: List<JSONObject> = emptyList(),
    val rawJson: String = "{}"
) {
    fun toJson(): JSONObject {
        return try {
            val json = JSONObject(rawJson)
            json.put("sourceId", sourceId)
            json.put("sourceItemId", sourceItemId)
            json.put("contentId", contentId)
            json.put("title", title)
            json.put("poster", poster)
            json.put("category", category)
            json.put("sourceCategoryName", sourceCategoryName)
            json.put("year", year)
            json.put("region", region)
            json.put("rating", rating)
            json.put("updateInfo", updateInfo)
            json.put("backdrop", backdrop)
            json.put("description", description)
            json.put("director", director)
            json
        } catch (_: Exception) {
            JSONObject().put("title", title)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): NativeMovieItem {
            val epArray = json.optJSONArray("episodes")
            val epList = mutableListOf<JSONObject>()
            if (epArray != null) {
                for (i in 0 until epArray.length()) {
                    epArray.optJSONObject(i)?.let { epList.add(it) }
                }
            }
            val actArray = json.optJSONArray("actors")
            val actList = mutableListOf<String>()
            if (actArray != null) {
                for (i in 0 until actArray.length()) {
                    actList.add(actArray.optString(i))
                }
            }
            return NativeMovieItem(
                sourceId = json.optString("sourceId"),
                sourceItemId = json.optString("sourceItemId").ifBlank { json.optString("vod_id") },
                contentId = json.optString("contentId").ifBlank { json.optString("vod_id") },
                title = json.optString("title").ifBlank { json.optString("vod_name") },
                poster = json.optString("poster").ifBlank { json.optString("vod_pic") },
                category = json.optString("category").ifBlank { json.optString("type_name") },
                sourceCategoryName = json.optString("sourceCategoryName"),
                year = json.optString("year").ifBlank { json.optString("vod_year") },
                region = json.optString("region").ifBlank { json.optString("vod_area") },
                rating = json.optString("rating").ifBlank { json.optString("vod_score") },
                updateInfo = json.optString("updateInfo").ifBlank { json.optString("vod_remarks") },
                backdrop = json.optString("backdrop").ifBlank { json.optString("vod_pic") },
                description = json.optString("description").ifBlank { json.optString("vod_content") },
                director = json.optString("director").ifBlank { json.optString("vod_director") },
                actors = actList,
                episodes = epList,
                rawJson = json.toString()
            )
        }
    }
}
