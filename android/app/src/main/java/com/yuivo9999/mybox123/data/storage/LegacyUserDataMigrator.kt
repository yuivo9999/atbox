package com.yuivo9999.mybox123.data.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Imports the old Capacitor/WebView bridge's native dual-write store.
 * The old SharedPreferences file is intentionally never cleared or modified.
 */
class LegacyUserDataMigrator(
    context: Context,
    private val database: NativeUserDataDatabase,
) {
    private val appContext = context.applicationContext

    fun migrate(): LegacyMigrationResult = try {
        migrateInternal()
    } catch (error: Exception) {
        Log.e(TAG, "Legacy user data migration could not start; source preferences were left intact", error)
        LegacyMigrationResult(
            completed = false,
            importedCount = 0,
            backupCount = 0,
            invalidKnownKeys = emptyList(),
            encodedNonStringKeys = emptyList(),
            sourceFound = false,
            alreadyRan = false,
            completedAt = System.currentTimeMillis(),
            errorCode = error.message ?: "LEGACY_MIGRATION_START_FAILED",
        )
    }

    private fun migrateInternal(): LegacyMigrationResult {
        val alreadyMigrated = parseCompleted(database.readRaw(MIGRATION_MARKER_KEY))
        if (alreadyMigrated != null) return alreadyMigrated.copy(alreadyRan = true)

        val oldPreferences = appContext.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val snapshot = linkedMapOf<String, String>()
        val encodedNonStringKeys = mutableListOf<String>()
        oldPreferences.all.forEach { (key, value) ->
            if (value is String) {
                snapshot[key] = value
            } else {
                encodedNonStringKeys += key
                snapshot[key] = encodeNonStringPreference(value)
            }
        }
        val invalidKnownKeys = findInvalidKnownValues(snapshot)
        val report = LegacyMigrationResult(
            completed = true,
            importedCount = snapshot.size,
            backupCount = snapshot.size,
            invalidKnownKeys = invalidKnownKeys,
            encodedNonStringKeys = encodedNonStringKeys,
            sourceFound = snapshot.isNotEmpty(),
            alreadyRan = false,
            completedAt = System.currentTimeMillis(),
        )
        val markerJson = report.toJson().toString()

        return try {
            val imported = database.importLegacySnapshot(snapshot, MIGRATION_MARKER_KEY, markerJson)
            if (imported) report else (parseCompleted(database.readRaw(MIGRATION_MARKER_KEY))
                ?: report.copy(completed = false, errorCode = "MIGRATION_MARKER_UNEXPECTED")).copy(alreadyRan = true)
        } catch (error: Exception) {
            Log.e(TAG, "Legacy user data migration failed; source preferences were left intact", error)
            LegacyMigrationResult(
                completed = false,
                importedCount = 0,
                backupCount = database.legacyBackupCount(),
                invalidKnownKeys = invalidKnownKeys,
                encodedNonStringKeys = encodedNonStringKeys,
                sourceFound = snapshot.isNotEmpty(),
                alreadyRan = false,
                completedAt = System.currentTimeMillis(),
                errorCode = error.message ?: "LEGACY_MIGRATION_FAILED",
            )
        }
    }

    private fun encodeNonStringPreference(value: Any?): String {
        val encoded = JSONObject().put("__legacySharedPreferencesType", value?.javaClass?.simpleName ?: "null")
        when (value) {
            is Set<*> -> encoded.put("value", JSONArray().apply { value.forEach { put(it?.toString() ?: JSONObject.NULL) } })
            null -> encoded.put("value", JSONObject.NULL)
            else -> encoded.put("value", value)
        }
        return encoded.toString()
    }

    private fun findInvalidKnownValues(values: Map<String, String>): List<String> {
        val expectedArrays = setOf("favorites", "history", "progress", "searches", "sources")
        val expectedObjects = setOf("settings", "selectedSources", "migration:legacy-v2")
        return values.keys.filter { key ->
            val raw = values[key] ?: return@filter false
            try {
                val tokener = JSONTokener(raw)
                val value = tokener.nextValue()
                if (tokener.nextClean() != '\u0000') return@filter true
                when {
                    key in expectedArrays -> value !is JSONArray
                    key in expectedObjects -> value !is JSONObject
                    else -> false
                }
            } catch (_: JSONException) {
                key in expectedArrays || key in expectedObjects
            }
        }
    }

    private fun parseCompleted(raw: String?): LegacyMigrationResult? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            if (!json.optBoolean("completed", false)) return null
            LegacyMigrationResult(
                completed = true,
                importedCount = json.optInt("importedCount", 0),
                backupCount = json.optInt("backupCount", 0),
                invalidKnownKeys = json.optJSONArray("invalidKnownKeys").toStringList(),
                encodedNonStringKeys = json.optJSONArray("encodedNonStringKeys").toStringList(),
                sourceFound = json.optBoolean("sourceFound", false),
                alreadyRan = true,
                completedAt = json.optLong("completedAt", 0L),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) { for (index in 0 until length()) add(optString(index)) }
    }

    companion object {
        const val LEGACY_PREFERENCES_NAME = "tvbox_user_data"
        const val MIGRATION_MARKER_KEY = "system:migration:legacy-sharedpreferences-v1"
        private const val TAG = "MyBoxDataMigration"
    }
}

data class LegacyMigrationResult(
    val completed: Boolean,
    val importedCount: Int,
    val backupCount: Int,
    val invalidKnownKeys: List<String>,
    val encodedNonStringKeys: List<String>,
    val sourceFound: Boolean,
    val alreadyRan: Boolean,
    val completedAt: Long,
    val errorCode: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("completed", completed)
        .put("version", 1)
        .put("importedCount", importedCount)
        .put("backupCount", backupCount)
        .put("invalidKnownKeys", JSONArray(invalidKnownKeys))
        .put("encodedNonStringKeys", JSONArray(encodedNonStringKeys))
        .put("sourceFound", sourceFound)
        .put("completedAt", completedAt)
        .put("errorCode", errorCode ?: JSONObject.NULL)
}
