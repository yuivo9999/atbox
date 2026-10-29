package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.data.movie.StaticCatalogData
import com.yuivo9999.mybox123.data.source.NativeContentSource
import org.json.JSONObject

class NativeLiveDescriptorService {
    fun loadChannels(source: NativeContentSource, forceRefresh: Boolean = false): List<JSONObject> {
        return StaticCatalogData.liveChannels.map { JSONObject(it.toString()) }
    }

    fun loadStreams(source: NativeContentSource, channel: JSONObject, forceRefresh: Boolean = false): List<JSONObject> {
        val streams = channel.optJSONArray("streams")
        val list = mutableListOf<JSONObject>()
        if (streams != null) {
            for (i in 0 until streams.length()) {
                streams.optJSONObject(i)?.let { list.add(it) }
            }
        }
        if (list.isEmpty()) {
            val url = channel.optString("url")
            if (url.isNotBlank()) {
                list.add(JSONObject().put("name", "默认线路").put("url", url))
            }
        }
        return list
    }
}
