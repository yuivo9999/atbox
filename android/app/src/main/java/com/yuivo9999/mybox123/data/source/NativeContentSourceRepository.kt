package com.yuivo9999.mybox123.data.source

import com.yuivo9999.mybox123.data.storage.NativeUserDataRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Source CRUD preserving extension-specific fields that the native UI may not yet understand. */
class NativeContentSourceRepository(private val userData: NativeUserDataRepository) {
    @Synchronized
    fun getAll(): List<NativeContentSource> {
        val raw = userData.getSources()
        val seen = linkedSetOf<String>()
        return buildList(raw.length()) {
            for (index in 0 until raw.length()) {
                val objectValue = raw.optJSONObject(index)
                    ?: throw IllegalStateException("SOURCE_RECORD_INVALID:$index")
                val source = NativeContentSource.fromJson(objectValue, index)
                // Legacy sourceRepository.getAll() keeps the first record for each explicit ID.
                if (seen.add(source.sourceId)) add(source)
            }
        }
    }

    @Synchronized
    fun saveAll(sources: List<NativeContentSource>): List<NativeContentSource> {
        val seen = linkedSetOf<String>()
        val normalized = sources.mapIndexedNotNull { index, source ->
            val item = NativeContentSource.fromJson(source.toJson(), index)
            // Match legacy saveAll(): normalize records and retain the first duplicate ID.
            item.takeIf { seen.add(it.sourceId) }
        }
        val json = JSONArray().apply { normalized.forEach { put(it.toJson()) } }
        userData.saveSources(json)
        return normalized
    }

    @Synchronized
    fun upsert(sourceJson: JSONObject): NativeContentSource {
        val requestedId = jsonString(sourceJson, "sourceId").trim().ifEmpty { jsonString(sourceJson, "id").trim() }
        val stableJson = JSONObject(sourceJson.toString()).apply {
            if (requestedId.isEmpty()) put("sourceId", "local-${UUID.randomUUID()}") else put("sourceId", requestedId)
        }
        val candidate = NativeContentSource.fromJson(stableJson, getAll().size)
        val current = getAll().filterNot { it.sourceId == candidate.sourceId }
        return saveAll(current + candidate).first { it.sourceId == candidate.sourceId }
    }

    @Synchronized
    fun remove(sourceId: String): Boolean {
        val current = getAll()
        val retained = current.filterNot { it.sourceId == sourceId }
        if (retained.size == current.size) return false
        saveAll(retained)
        return true
    }

    private fun jsonString(value: JSONObject, key: String): String =
        if (!value.has(key) || value.isNull(key)) "" else value.optString(key, "")

    @Synchronized
    fun setEnabled(sourceId: String, enabled: Boolean): Boolean {
        val current = getAll()
        val target = current.firstOrNull { it.sourceId == sourceId } ?: return false
        val updated = target.toJson().put("enabled", enabled).apply {
            if (!enabled) put("isActive", false)
        }
        saveAll(current.map { if (it.sourceId == sourceId) NativeContentSource.fromJson(updated) else it })
        return true
    }
}
