package com.yuivo9999.mybox123.data.storage

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Native repository for the legacy user's JSON-shaped data. Unknown fields are preserved.
 * Methods are synchronous because each mutation is transactional; callers must invoke them from
 * Dispatchers.IO (or another worker thread), never directly from the Android main thread.
 */
class NativeUserDataRepository(private val database: NativeUserDataDatabase) {
    fun readRaw(key: String): String? = database.readRaw(requireKey(key))

    /** Raw values captured from the legacy SharedPreferences migration; export-only snapshot. */
    fun exportLegacyMigrationBackupsRaw(): Map<String, String> = database.readAllLegacyBackups()

    fun writeRaw(key: String, valueJson: String) {
        val normalizedKey = requireKey(key)
        validateJson(valueJson, normalizedKey)
        database.writeRaw(normalizedKey, valueJson)
    }

    fun writeManyRaw(values: Map<String, String>) {
        val normalized = linkedMapOf<String, String>()
        values.forEach { (key, raw) ->
            val normalizedKey = requireKey(key)
            validateJson(raw, normalizedKey)
            normalized[normalizedKey] = raw
        }
        database.writeManyRaw(normalized)
    }

    /**
     * Apply an explicitly selected set of supported import keys with an atomic pre-change snapshot.
     * expectedCurrentRaw must contain every changed key; null means the key was absent at preview.
     * Call from Dispatchers.IO. No import UI calls this API yet.
     */
    fun applyRecoverableImport(
        values: Map<String, String>,
        expectedCurrentRaw: Map<String, String?>,
        formatVersion: Int = SUPPORTED_IMPORT_FORMAT_VERSION,
    ): String {
        require(formatVersion == SUPPORTED_IMPORT_FORMAT_VERSION) { "RESTORE_FORMAT_VERSION_UNSUPPORTED" }
        require(values.isNotEmpty()) { "RESTORE_NO_CHANGES" }
        val allowedKeys = importArrayKeys + importObjectKeys
        val normalizedValues = linkedMapOf<String, String>()
        var totalArrayRecords = 0
        values.forEach { (key, raw) ->
            val normalizedKey = requireKey(key)
            require(normalizedKey in allowedKeys) { "RESTORE_KEY_UNSUPPORTED:$normalizedKey" }
            validateImportRootType(normalizedKey, raw)
            if (normalizedKey in importArrayKeys) {
                val array = JSONArray(raw)
                totalArrayRecords += array.length()
                require(totalArrayRecords <= NativeDataImportPreview.MAX_RECORDS) { "RESTORE_TOO_MANY_RECORDS" }
                for (index in 0 until array.length()) {
                    require(array.optJSONObject(index) != null) { "RESTORE_RECORD_MUST_BE_OBJECT:$normalizedKey:$index" }
                }
            }
            normalizedValues[normalizedKey] = raw
        }
        require(normalizedValues.size == values.size) { "RESTORE_DUPLICATE_KEY" }
        val normalizedExpected = linkedMapOf<String, String?>()
        expectedCurrentRaw.forEach { (key, raw) -> normalizedExpected[requireKey(key)] = raw }
        require(normalizedExpected.keys == normalizedValues.keys) { "RESTORE_EXPECTED_STATE_KEYS_MISMATCH" }
        normalizedExpected.values.filterNotNull().forEach { raw ->
            require(raw.toByteArray(Charsets.UTF_8).size <= NativeDataImportPreview.MAX_FILE_BYTES) { "RESTORE_EXPECTED_VALUE_TOO_LARGE" }
        }
        val totalBytes = normalizedValues.values.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
        require(totalBytes <= NativeDataImportPreview.MAX_FILE_BYTES) { "RESTORE_PAYLOAD_TOO_LARGE" }
        val operationId = java.util.UUID.randomUUID().toString()
        database.applyRecoverableImport(operationId, formatVersion, normalizedExpected, normalizedValues)
        return operationId
    }

    /** Returns false when this operation was already rolled back; missing IDs throw. */
    fun rollbackRecoverableImport(operationId: String): Boolean {
        require(operationId.isNotBlank() && operationId.length <= 64) { "RESTORE_OPERATION_ID_INVALID" }
        return database.rollbackRecoverableImport(operationId)
    }

    fun getRecoverableImportStatus(operationId: String): String? = database.readRestoreOperationStatus(operationId)

    /** Returns the newest bounded list of persistent restore operations for the recovery UI. */
    fun getRecoverableImportHistory(limit: Int = 20): List<RecoverableImportOperation> =
        database.readRecoverableImportHistory(limit)

    private fun validateImportRootType(key: String, raw: String) {
        require(NativeDataImportPreview.maxJsonDepthForStorage(raw) <= NativeDataImportPreview.MAX_JSON_DEPTH_FOR_STORAGE) {
            "RESTORE_JSON_TOO_DEEP:$key"
        }
        validateJson(raw, key)
        val parser = JSONTokener(raw)
        val root = try { parser.nextValue() } catch (error: JSONException) {
            throw IllegalArgumentException("USER_DATA_JSON_INVALID:$key", error)
        }
        val expectedArray = key in importArrayKeys
        require(if (expectedArray) root is JSONArray else root is JSONObject) { "RESTORE_ROOT_TYPE_INVALID:$key" }
        if (parser.nextClean() != '\u0000') throw IllegalArgumentException("USER_DATA_JSON_TRAILING_DATA:$key")
    }

    fun readJsonArray(key: String): JSONArray {
        val raw = readRaw(key) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (error: JSONException) {
            throw IllegalStateException("USER_DATA_JSON_ARRAY_INVALID:$key", error)
        }
    }

    fun writeJsonArray(key: String, value: JSONArray) = writeRaw(key, value.toString())

    fun readJsonObject(key: String): JSONObject {
        val raw = readRaw(key) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (error: JSONException) {
            throw IllegalStateException("USER_DATA_JSON_OBJECT_INVALID:$key", error)
        }
    }

    fun writeJsonObject(key: String, value: JSONObject) = writeRaw(key, value.toString())

    fun getFavoriteRecords(): List<FavoriteRecord> = readJsonArray(KEY_FAVORITES).objects().map(FavoriteRecord::fromJson)
    fun saveFavoriteRecords(records: List<FavoriteRecord>) = saveTypedRecords(KEY_FAVORITES, records.map(FavoriteRecord::toJson))
    fun getHistoryRecords(): List<HistoryRecord> = readJsonArray(KEY_HISTORY).objects().map(HistoryRecord::fromJson)
    fun saveHistoryRecords(records: List<HistoryRecord>) = saveTypedRecords(KEY_HISTORY, records.map(HistoryRecord::toJson))
    fun getProgressRecords(): List<PlaybackProgressRecord> = readJsonArray(KEY_PROGRESS).objects().map(PlaybackProgressRecord::fromJson)
    fun saveProgressRecords(records: List<PlaybackProgressRecord>) = saveTypedRecords(KEY_PROGRESS, records.map(PlaybackProgressRecord::toJson))
    fun getSearchRecords(): List<SearchRecord> = readJsonArray(KEY_SEARCHES).objects().map(SearchRecord::fromJson)
    fun saveSearchRecords(records: List<SearchRecord>) = saveTypedRecords(KEY_SEARCHES, records.map(SearchRecord::toJson))

    fun getFavorites(): JSONArray = readJsonArray(KEY_FAVORITES)
    fun saveFavorites(items: JSONArray) = writeJsonArray(KEY_FAVORITES, items)
    fun getHistory(): JSONArray = readJsonArray(KEY_HISTORY)
    fun saveHistory(items: JSONArray) = writeJsonArray(KEY_HISTORY, items)
    fun getProgress(): JSONArray = readJsonArray(KEY_PROGRESS)
    fun saveProgress(items: JSONArray) = writeJsonArray(KEY_PROGRESS, items)
    fun getSearches(): JSONArray = readJsonArray(KEY_SEARCHES)
    fun saveSearches(items: JSONArray) = writeJsonArray(KEY_SEARCHES, items)
    fun getSources(): JSONArray = readJsonArray(KEY_SOURCES)
    fun saveSources(items: JSONArray) = writeJsonArray(KEY_SOURCES, items)
    fun getSettings(): JSONObject = NativeUserSettings.normalize(readJsonObject(KEY_SETTINGS))
    fun saveSettings(settings: JSONObject) = writeJsonObject(KEY_SETTINGS, NativeUserSettings.normalize(settings))
    fun getSelectedSources(): JSONObject {
        val defaults = NativeUserSettings.selectedSourcesDefaults()
        val stored = readJsonObject(KEY_SELECTED_SOURCES)
        val keys = stored.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            defaults.put(key, stored.opt(key) ?: JSONObject.NULL)
        }
        return defaults
    }
    fun saveSelectedSources(selected: JSONObject) {
        val normalized = NativeUserSettings.selectedSourcesDefaults()
        val keys = selected.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            normalized.put(key, selected.opt(key) ?: JSONObject.NULL)
        }
        writeJsonObject(KEY_SELECTED_SOURCES, normalized)
    }

    /** Clear user-visible records atomically; settings, sources and migration backups are preserved. */
    fun clearHistoryAndProgress() {
        writeManyRaw(mapOf(KEY_HISTORY to "[]", KEY_PROGRESS to "[]"))
    }

    fun clearUserRecords() {
        writeManyRaw(
            mapOf(
                KEY_FAVORITES to "[]",
                KEY_HISTORY to "[]",
                KEY_PROGRESS to "[]",
                KEY_SEARCHES to "[]",
            ),
        )
    }

    fun exportAllRaw(): Map<String, String> = database.readAllRaw()

    private fun saveTypedRecords(key: String, records: List<JSONObject>) {
        val array = JSONArray()
        records.forEach { array.put(it) }
        writeJsonArray(key, array)
    }

    private fun JSONArray.objects(): List<JSONObject> = buildList(length()) {
        for (index in 0 until length()) {
            val item = optJSONObject(index)
                ?: throw IllegalStateException("USER_DATA_RECORD_INVALID:$index")
            add(item)
        }
    }

    private fun validateJson(raw: String, key: String) {
        try {
            val parser = JSONTokener(raw)
            parser.nextValue()
            if (parser.nextClean() != '\u0000') throw JSONException("Trailing data")
        } catch (error: JSONException) {
            throw IllegalArgumentException("USER_DATA_JSON_INVALID:$key", error)
        }
    }

    private fun requireKey(key: String): String {
        val normalized = key.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_KEY_LENGTH) { "USER_DATA_KEY_INVALID" }
        return normalized
    }

    companion object {
        const val SUPPORTED_IMPORT_FORMAT_VERSION = 1
        private val importArrayKeys = setOf(KEY_FAVORITES, KEY_HISTORY, KEY_PROGRESS, KEY_SEARCHES, KEY_SOURCES, "multi_repos")
        private val importObjectKeys = setOf(KEY_SETTINGS, KEY_SELECTED_SOURCES)

        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val KEY_PROGRESS = "progress"
        const val KEY_SEARCHES = "searches"
        const val KEY_SETTINGS = "settings"
        const val KEY_SELECTED_SOURCES = "selectedSources"
        const val KEY_SOURCES = "sources"
        private const val MAX_KEY_LENGTH = 256
    }
}
