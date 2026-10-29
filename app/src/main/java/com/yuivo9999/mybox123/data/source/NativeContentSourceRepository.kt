package com.yuivo9999.mybox123.data.source

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class NativeContentSourceRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mybox_content_sources", Context.MODE_PRIVATE)

    fun getAll(): List<NativeContentSource> {
        val str = prefs.getString("sources", null) ?: return emptyList()
        return try {
            val arr = JSONArray(str)
            val list = mutableListOf<NativeContentSource>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(NativeContentSource.fromJson(it, i)) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(sources: List<NativeContentSource>) {
        val arr = JSONArray()
        sources.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("sources", arr.toString()).apply()
    }
}
