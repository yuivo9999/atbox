package com.yuivo9999.mybox123.data.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class NativeUserDataRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mybox_user_data", Context.MODE_PRIVATE)

    fun readRaw(key: String): String? = prefs.getString(key, null)

    fun writeRaw(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun readJsonArray(key: String): JSONArray {
        val str = readRaw(key) ?: return JSONArray()
        return try {
            JSONArray(str)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    fun writeJsonArray(key: String, array: JSONArray) {
        writeRaw(key, array.toString())
    }

    fun getFavoriteRecords(): List<FavoriteRecord> {
        val arr = readJsonArray("favorites")
        val list = mutableListOf<FavoriteRecord>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(FavoriteRecord.fromJson(it)) }
        }
        return list
    }

    fun saveFavoriteRecords(records: List<FavoriteRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        writeJsonArray("favorites", arr)
    }

    fun getHistoryRecords(): List<PlaybackHistoryRecord> {
        val arr = readJsonArray("history")
        val list = mutableListOf<PlaybackHistoryRecord>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(PlaybackHistoryRecord.fromJson(it)) }
        }
        return list
    }

    fun saveHistoryRecords(records: List<PlaybackHistoryRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        writeJsonArray("history", arr)
    }

    fun getProgressRecords(): List<ProgressRecord> {
        val arr = readJsonArray("progress")
        val list = mutableListOf<ProgressRecord>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(ProgressRecord.fromJson(it)) }
        }
        return list
    }

    fun saveProgressRecords(records: List<ProgressRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        writeJsonArray("progress", arr)
    }

    fun getSearchRecords(): List<SearchRecord> {
        val arr = readJsonArray("searches")
        val list = mutableListOf<SearchRecord>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(SearchRecord.fromJson(it)) }
        }
        return list
    }

    fun saveSearchRecords(records: List<SearchRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        writeJsonArray("searches", arr)
    }

    fun getSettings(): JSONObject {
        val str = readRaw("settings") ?: return JSONObject()
        return try {
            JSONObject(str)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun saveSettings(settings: JSONObject) {
        writeRaw("settings", settings.toString())
    }
}
