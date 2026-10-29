package com.yuivo9999.mybox123.data.storage

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/** Read-only, deterministic validation for native user-data export files. Never writes to storage. */
object NativeDataImportPreview {
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    private const val FORMAT = "mybox-native-user-data"
    private const val VERSION = 1
    private const val MAX_JSON_DEPTH = 64
    const val MAX_RECORDS = 50_000
    const val MAX_JSON_DEPTH_FOR_STORAGE = MAX_JSON_DEPTH
    fun maxJsonDepthForStorage(raw: String): Int = maxJsonDepth(raw)

    private val arrayKeys = setOf(
        NativeUserDataRepository.KEY_FAVORITES,
        NativeUserDataRepository.KEY_HISTORY,
        NativeUserDataRepository.KEY_PROGRESS,
        NativeUserDataRepository.KEY_SEARCHES,
        NativeUserDataRepository.KEY_SOURCES,
        "multi_repos",
    )
    private val objectKeys = setOf(
        NativeUserDataRepository.KEY_SETTINGS,
        NativeUserDataRepository.KEY_SELECTED_SOURCES,
    )
    private val supportedKeys = arrayKeys + objectKeys

    data class KeyPreview(
        val key: String,
        val incomingRecords: Int,
        val currentRecords: Int,
        val duplicateIncoming: Int,
        val identityConflicts: Int,
        val recordsWithoutStableIdentity: Int,
        val wholeKeyConflict: Boolean,
    )

    data class Result(
        val formatVersion: Int,
        val exportedAt: Long?,
        val checksumStatus: String,
        val declaredDataSha256: String?,
        val envelopeUnknownKeys: List<String>,
        val supportedKeys: List<KeyPreview>,
        val unknownKeys: List<String>,
        val skippedUnsafeKeys: List<String>,
        val totalIncomingRecords: Int,
        val totalDuplicateIncoming: Int,
        val totalIdentityConflicts: Int,
        val totalRecordsWithoutStableIdentity: Int,
        val totalWholeKeyConflicts: Int,
        /** Exact incoming JSON for explicit whole-key replacement; unknown keys are excluded. */
        val incomingValuesByKey: Map<String, String>,
        /** Exact current raw value at preview time; null means the key was absent. */
        val currentRawSnapshot: Map<String, String?>,
    )

    fun preview(payload: String, currentRaw: Map<String, String>): Result {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "IMPORT_FILE_TOO_LARGE" }
        require(maxJsonDepth(payload) <= MAX_JSON_DEPTH) { "IMPORT_JSON_TOO_DEEP" }
        val root = parseCompleteObject(payload, "IMPORT_JSON_INVALID")
        require(root.optString("format") == FORMAT) { "IMPORT_FORMAT_UNSUPPORTED" }
        val versionValue = root.opt("formatVersion")
        require(versionValue is Number && versionValue.toDouble() == VERSION.toDouble()) { "IMPORT_VERSION_UNSUPPORTED" }
        val data = root.optJSONObject("data") ?: throw IllegalArgumentException("IMPORT_DATA_OBJECT_REQUIRED")
        val requiredEnvelopeKeys = setOf("format", "formatVersion", "exportedAt", "scope", "data")
        require(root.keys().asSequence().toSet().containsAll(requiredEnvelopeKeys)) { "IMPORT_ENVELOPE_FIELDS_MISSING" }
        val exportedAtValue = root.opt("exportedAt")
        require(exportedAtValue is Number && exportedAtValue.toDouble().isFinite() && exportedAtValue.toDouble() == exportedAtValue.toLong().toDouble() && exportedAtValue.toLong() >= 0L) { "IMPORT_EXPORTED_AT_INVALID" }
        require(root.opt("scope") is String && root.optString("scope").isNotBlank()) { "IMPORT_SCOPE_INVALID" }
        val envelopeUnknownKeys = root.keys().asSequence().toSet() - (requiredEnvelopeKeys + "dataSha256")
        val declaredDigestValue = root.opt("dataSha256")
        val checksumStatus = if (declaredDigestValue == null) {
            "LEGACY_NO_CHECKSUM"
        } else {
            require(declaredDigestValue is String && declaredDigestValue.matches(Regex("[0-9a-f]{64}"))) { "IMPORT_CHECKSUM_FORMAT_INVALID" }
            require(declaredDigestValue == canonicalDataSha256(data)) { "IMPORT_CHECKSUM_MISMATCH" }
            "VERIFIED"
        }

        val incomingKeys = data.keys().asSequence().toList().sorted()
        val unknownKeys = incomingKeys.filterNot { it in supportedKeys }
        val recordTotal = incomingKeys.filter { it in arrayKeys }.sumOf { key ->
            (data.opt(key) as? JSONArray)?.length() ?: throw IllegalArgumentException("IMPORT_EXPECTED_ARRAY:$key")
        }
        require(recordTotal <= MAX_RECORDS) { "IMPORT_TOO_MANY_RECORDS" }
        val previews = mutableListOf<KeyPreview>()
        val skippedUnsafe = mutableListOf<String>()

        incomingKeys.filter { it in supportedKeys }.forEach { key ->
            val incoming = data.opt(key)
            val current = parseCurrentValue(currentRaw[key], key)
            if (key in arrayKeys) {
                val incomingArray = incoming as? JSONArray
                    ?: throw IllegalArgumentException("IMPORT_EXPECTED_ARRAY:$key")
                val currentArray = when (current) {
                    is JSONArray -> current
                    else -> if (currentRaw.containsKey(key)) throw IllegalStateException("CURRENT_DATA_TYPE_INVALID:$key") else JSONArray()
                }
                if (key == "multi_repos") {
                    previews += KeyPreview(
                        key, incomingArray.length(), currentArray.length(), 0, 0,
                        incomingArray.length(), currentRaw.containsKey(key) && canonicalJson(incoming) != canonicalJson(current),
                    )
                    skippedUnsafe += "$key:记录身份规则尚未统一，当前仅作整键冲突预览"
                } else {
                    val incomingIdentities = mutableListOf<String?>()
                    var missingIdentity = 0
                    for (index in 0 until incomingArray.length()) {
                        val record = incomingArray.optJSONObject(index)
                            ?: throw IllegalArgumentException("IMPORT_RECORD_MUST_BE_OBJECT:$key:$index")
                        val identity = stableIdentity(key, record)
                        if (identity == null) missingIdentity++
                        incomingIdentities += identity
                    }
                    val seen = mutableSetOf<String>()
                    var duplicates = 0
                    incomingIdentities.filterNotNull().forEach { if (!seen.add(it)) duplicates++ }
                    val currentIdentities = currentArrayIdentities(key, currentArray)
                    val conflicts = incomingIdentities.filterNotNull().count { it in currentIdentities }
                    previews += KeyPreview(
                        key, incomingArray.length(), currentArray.length(), duplicates, conflicts,
                        missingIdentity, false,
                    )
                    if (missingIdentity > 0) skippedUnsafe += "$key:$missingIdentity 条记录缺少稳定身份，将不能自动合并"
                }
            } else {
                val incomingObject = incoming as? JSONObject
                    ?: throw IllegalArgumentException("IMPORT_EXPECTED_OBJECT:$key")
                val currentObject = when (current) {
                    is JSONObject -> current
                    else -> if (currentRaw.containsKey(key)) throw IllegalStateException("CURRENT_DATA_TYPE_INVALID:$key") else JSONObject()
                }
                previews += KeyPreview(
                    key, incomingObject.length(), currentObject.length(), 0, 0, 0,
                    currentRaw.containsKey(key) && canonicalJson(incomingObject) != canonicalJson(currentObject),
                )
            }
        }

        val safePreviews = previews.toList()
        val incomingValues = incomingKeys.filter { it in supportedKeys }.associateWith { key ->
            data.get(key).toString()
        }.toSortedMap()
        val currentSnapshot = incomingValues.keys.associateWith { key -> currentRaw[key] }.toSortedMap()
        return Result(
            formatVersion = VERSION,
            exportedAt = exportedAtValue.toLong(),
            checksumStatus = checksumStatus,
            declaredDataSha256 = declaredDigestValue as? String,
            envelopeUnknownKeys = envelopeUnknownKeys.sorted(),
            supportedKeys = safePreviews,
            unknownKeys = unknownKeys,
            skippedUnsafeKeys = skippedUnsafe,
            totalIncomingRecords = safePreviews.sumOf { it.incomingRecords },
            totalDuplicateIncoming = safePreviews.sumOf { it.duplicateIncoming },
            totalIdentityConflicts = safePreviews.sumOf { it.identityConflicts },
            totalRecordsWithoutStableIdentity = safePreviews.sumOf { it.recordsWithoutStableIdentity },
            totalWholeKeyConflicts = safePreviews.count { it.wholeKeyConflict },
            incomingValuesByKey = incomingValues,
            currentRawSnapshot = currentSnapshot,
        )
    }

    /** SHA-256 of canonical JSON data; object keys are sorted while array order is preserved. */
    fun canonicalDataSha256(data: JSONObject): String {
        val bytes = canonicalJson(data).toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun maxJsonDepth(raw: String): Int {
        var depth = 0
        var maximum = 0
        var inString = false
        var escaped = false
        raw.forEach { char ->
            if (inString) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
            } else {
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        if (depth > maximum) maximum = depth
                    }
                    '}', ']' -> depth--
                }
            }
        }
        return maximum
    }

    private fun parseCurrentValue(raw: String?, key: String): Any? {
        if (raw == null) return if (key in arrayKeys) JSONArray() else JSONObject()
        return try {
            val parser = JSONTokener(raw)
            val value = parser.nextValue()
            if (parser.nextClean() != '\u0000') throw JSONException("Trailing data")
            value
        } catch (error: JSONException) {
            throw IllegalStateException("CURRENT_DATA_INVALID:$key", error)
        }
    }

    private fun parseCompleteObject(raw: String, errorCode: String): JSONObject = try {
        val parser = JSONTokener(raw)
        val value = parser.nextValue()
        if (parser.nextClean() != '\u0000' || value !is JSONObject) throw JSONException("Expected complete object")
        value
    } catch (error: JSONException) {
        throw IllegalArgumentException(errorCode, error)
    }

    private fun stableIdentity(key: String, record: JSONObject): String? {
        fun text(vararg names: String): String? = names.asSequence()
            .map { name -> if (record.isNull(name)) "" else record.optString(name, "").trim() }
            .firstOrNull { it.isNotEmpty() }
        return when (key) {
            NativeUserDataRepository.KEY_FAVORITES -> text("favoriteId") ?: run {
                val target = text("targetId", "id") ?: return null
                val type = normalizedTargetType(text("targetType", "kind") ?: return null)
                "$type:$target"
            }
            NativeUserDataRepository.KEY_HISTORY -> text("historyId") ?: run {
                val target = text("targetId", "id") ?: return null
                val type = normalizedTargetType(text("targetType", "kind") ?: return null)
                val episode = text("episodeId") ?: (record.opt("episode") as? Number)?.toInt()?.let { "legacy-episode:$target:$it" } ?: ""
                if (episode.isBlank()) return null
                "$type:$target:$episode"
            }
            NativeUserDataRepository.KEY_PROGRESS -> text("progressId") ?: run {
                val target = text("contentId", "id") ?: return null
                val episode = text("episodeId") ?: (record.opt("episode") as? Number)?.toInt()?.let { "legacy-episode:$target:$it" } ?: ""
                if (episode.isBlank()) return null
                "$target:$episode"
            }
            NativeUserDataRepository.KEY_SEARCHES -> text("searchId") ?: text("keyword", "query")?.lowercase(java.util.Locale.ROOT)
            NativeUserDataRepository.KEY_SOURCES -> text("sourceId", "id")
            else -> null
        }?.let { "$key:$it" }
    }

    private fun normalizedTargetType(value: String): String = when (value.trim().lowercase(java.util.Locale.ROOT)) {
        "live" -> "channel"
        "channel" -> "channel"
        "movie", "content", "vod" -> "content"
        else -> value.trim().lowercase(java.util.Locale.ROOT)
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        is JSONObject -> {
            val keys = value.keys().asSequence().toList().sorted()
            "{" + keys.joinToString(",") { key -> JSONObject.quote(key) + ":" + canonicalJson(value.opt(key)) } + "}"
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]", separator = ",") { index -> canonicalJson(value.opt(index)) }
        JSONObject.NULL -> "null"
        null -> "null"
        is String -> JSONObject.quote(value)
        is Number -> {
            val decimal = try { java.math.BigDecimal(value.toString()).stripTrailingZeros() } catch (_: NumberFormatException) { throw IllegalArgumentException("IMPORT_JSON_NUMBER_INVALID") }
            if (decimal.compareTo(java.math.BigDecimal.ZERO) == 0) "0" else decimal.toPlainString()
        }
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun currentArrayIdentities(key: String, array: JSONArray): Set<String> = buildSet {
        for (index in 0 until array.length()) {
            val record = array.optJSONObject(index) ?: continue
            stableIdentity(key, record)?.let(::add)
        }
    }
}
