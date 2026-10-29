package com.yuivo9999.mybox123.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class RepositoryConfigInput(val repositoryName: String, val sourceUrl: String, val config: JSONObject)

data class RepositorySectionSummary(
    val rawCount: Int,
    val uniqueCount: Int,
    val duplicateCount: Int,
    val unsupportedCount: Int,
    val potentialConflictCount: Int
)

data class RepositoryAggregationSummary(
    val successfulRepositories: Int,
    val failedRepositories: List<String>,
    val sites: RepositorySectionSummary,
    val lives: RepositorySectionSummary,
    val parses: RepositorySectionSummary,
    val potentialConflictGroups: Int,
    val unsupportedEntries: Int,
    val safeDuplicateGroups: Int,
    val reviewMetadataTruncated: Boolean,
    val exportJson: String,
)

internal object RepositoryConfigAggregator {
    private const val MAX_CONFIG_TEXT_CHARS = 2 * 1024 * 1024
    private const val MAX_ITEMS_PER_SECTION = 20_000
    private const val MAX_EXPORTED_REVIEW_GROUPS = 500
    private const val MAX_SOURCE_REFS_PER_GROUP = 30
    private const val MAX_CONFLICT_RECORD_PREVIEWS = 5
    private const val MAX_EXPORTED_UNSUPPORTED_ROWS = 500

    private val identityFields = mapOf(
        "sites" to listOf("key", "api", "ext", "name"),
        "lives" to listOf("url", "key", "name", "type"),
        "parses" to listOf("url", "parse", "name", "type"),
    )

    fun parseConfig(text: String): JSONObject {
        require(text.length <= MAX_CONFIG_TEXT_CHARS) { "REPOSITORY_CONFIG_TOO_LARGE" }
        val tokener = JSONTokener(text)
        val value = tokener.nextValue()
        require(tokener.nextClean() == '\u0000' && value is JSONObject) { "REPOSITORY_JSON_INVALID" }
        val config = value as JSONObject
        val sites = section(config, "sites")
        val lives = section(config, "lives")
        section(config, "parses")
        require(sites != null || lives != null) { "REPOSITORY_CONFIG_SHAPE_UNSUPPORTED" }
        return config
    }

    fun summarize(
        configs: List<RepositoryConfigInput>,
        successfulRepositories: Int,
        failedRepositories: List<String>,
        repositoryOutcomes: List<RepositoryFetchOutcome> = emptyList(),
    ): RepositoryAggregationSummary {
        val merged = JSONObject()
        val sections = linkedMapOf<String, RepositorySectionSummary>()
        val conflictRows = mutableListOf<JSONObject>()
        val duplicateRows = mutableListOf<JSONObject>()
        val unsupportedRows = mutableListOf<JSONObject>()
        var unsupportedTotal = 0
        var safeDuplicateGroupTotal = 0
        var potentialConflictGroupTotal = 0
        var reviewDetailsTruncated = false
        val conflictGroupCountBySection = mutableMapOf<String, Int>()

        identityFields.forEach { (name, fields) ->
            val rawItems = configs.flatMap { source ->
                val array = section(source.config, name) ?: return@flatMap emptyList()
                (0 until array.length()).map { index ->
                    AggregationRecord(source.repositoryName, source.sourceUrl, index, array.opt(index))
                }
            }
            val supported = rawItems.filter { it.value is JSONObject }
            val unsupported = rawItems.filterNot { it.value is JSONObject }
            unsupported.forEach { record ->
                unsupportedTotal++
                if (unsupportedRows.size < MAX_EXPORTED_UNSUPPORTED_ROWS) {
                    unsupportedRows += JSONObject()
                        .put("section", name)
                        .put("classification", "UNSUPPORTED_ENTRY_SHAPE")
                        .put("sourceRepository", record.repositoryName)
                        .put("sourceUrl", record.sourceUrl)
                        .put("sourceIndex", record.sourceIndex)
                        .put("valueType", jsonType(record.value))
                }
            }

            val unique = linkedMapOf<String, AggregationRecord>()
            val duplicateGroups = linkedMapOf<String, MutableList<AggregationRecord>>()
            supported.forEach { record ->
                val item = record.value as JSONObject
                val identity = identity(item, fields)
                val payload = canonical(item)
                val dedupeKey = "$identity|payload:${payload.length}:$payload"
                val prior = unique[dedupeKey]
                if (prior == null) {
                    unique[dedupeKey] = record
                    duplicateGroups[dedupeKey] = mutableListOf(record)
                } else {
                    duplicateGroups.getValue(dedupeKey).add(record)
                }
            }
            duplicateGroups.values.filter { it.size > 1 }.forEach { rows ->
                safeDuplicateGroupTotal++
                if (rows.size > MAX_SOURCE_REFS_PER_GROUP) reviewDetailsTruncated = true
                if (duplicateRows.size < MAX_EXPORTED_REVIEW_GROUPS) {
                    duplicateRows += JSONObject()
                        .put("section", name)
                        .put("classification", "SAFE_EXACT_DUPLICATE")
                        .put("identityHint", anchorIdentity(rows.first().value as JSONObject, fields).take(180))
                        .put("records", rows.size)
                        .put("sources", JSONArray().apply { rows.take(MAX_SOURCE_REFS_PER_GROUP).forEach { put(sourceRef(it)) } })
                        .put("sourcesTruncated", rows.size > MAX_SOURCE_REFS_PER_GROUP)
                }
            }

            val anchorGroups = supported.groupBy { record -> anchorIdentity(record.value as JSONObject, fields) }
                .filterKeys { it.isNotBlank() }
            anchorGroups.forEach { (anchor, rows) ->
                val distinctPayloads = rows.map { canonical(it.value) }.distinct()
                if (rows.size > 1 && distinctPayloads.size > 1) {
                    potentialConflictGroupTotal++
                    if (rows.size > MAX_SOURCE_REFS_PER_GROUP || rows.size > MAX_CONFLICT_RECORD_PREVIEWS) reviewDetailsTruncated = true
                    conflictGroupCountBySection[name] = (conflictGroupCountBySection[name] ?: 0) + 1
                    if (conflictRows.size < MAX_EXPORTED_REVIEW_GROUPS) {
                        conflictRows += JSONObject()
                            .put("section", name)
                            .put("identityHint", anchor.take(180))
                            .put("records", rows.size)
                            .put("distinctPayloads", distinctPayloads.size)
                            .put("classification", "POTENTIAL_CONFLICT_REVIEW_REQUIRED")
                            .put("sources", JSONArray().apply { rows.take(MAX_SOURCE_REFS_PER_GROUP).forEach { put(sourceRef(it)) } })
                            .put("sourcesTruncated", rows.size > MAX_SOURCE_REFS_PER_GROUP)
                            .put("recordPreviews", JSONArray().apply {
                                rows.take(MAX_CONFLICT_RECORD_PREVIEWS).forEach { row ->
                                    put(JSONObject()
                                        .put("sourceRepository", row.repositoryName)
                                        .put("sourceUrl", row.sourceUrl)
                                        .put("sourceIndex", row.sourceIndex)
                                        .put("record", row.value))
                                }
                            })
                            .put("recordPreviewsTruncated", rows.size > MAX_CONFLICT_RECORD_PREVIEWS)
                    }
                }
            }

            merged.put(name, JSONArray().apply { unique.values.forEach { put(it.value) } })
            sections[name] = RepositorySectionSummary(
                rawCount = rawItems.size,
                uniqueCount = unique.size,
                duplicateCount = (supported.size - unique.size).coerceAtLeast(0),
                unsupportedCount = unsupported.size,
                potentialConflictCount = conflictGroupCountBySection[name] ?: 0,
            )
        }

        val emptySummary = RepositorySectionSummary(0, 0, 0, 0, 0)
        val reviewMetadataTruncated = unsupportedTotal > unsupportedRows.size ||
            safeDuplicateGroupTotal > duplicateRows.size ||
            potentialConflictGroupTotal > conflictRows.size || reviewDetailsTruncated

        val importBlockers = buildList {
            add("READ_ONLY_PREVIEW_NOT_IMPORTABLE")
            if (failedRepositories.isNotEmpty()) add("SOME_REPOSITORIES_FAILED")
            if (potentialConflictGroupTotal > 0) add("POTENTIAL_CONFLICTS_REQUIRE_REVIEW")
            if (unsupportedTotal > 0) add("UNSUPPORTED_ENTRIES_REQUIRE_REVIEW")
            if (reviewMetadataTruncated) add("REVIEW_METADATA_INCOMPLETE")
        }

        val export = JSONObject()
            .put("format", "mybox-tvbox-aggregate-preview-v1")
            .put("readOnlyPreview", true)
            .put("applyAllowed", false)
            .put("validation", JSONObject()
                .put("validObjectEntries", sections.values.sumOf { it.rawCount - it.unsupportedCount })
                .put("unsupportedEntries", unsupportedTotal)
                .put("safeExactDuplicateGroups", safeDuplicateGroupTotal)
                .put("potentialConflictGroups", potentialConflictGroupTotal)
                .put("reviewMetadataTruncated", reviewMetadataTruncated))
            .put("config", merged)
            .toString(2)

        return RepositoryAggregationSummary(
            successfulRepositories, failedRepositories,
            sections["sites"] ?: emptySummary,
            sections["lives"] ?: emptySummary,
            sections["parses"] ?: emptySummary,
            potentialConflictGroupTotal, unsupportedTotal, safeDuplicateGroupTotal, reviewMetadataTruncated, export,
        )
    }

    private fun sourceRef(record: AggregationRecord) = JSONObject()
        .put("repository", record.repositoryName)
        .put("url", record.sourceUrl)
        .put("index", record.sourceIndex)

    private fun jsonType(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> "object"
        is JSONArray -> "array"
        is String -> "string"
        is Number -> "number"
        is Boolean -> "boolean"
        else -> value.javaClass.simpleName
    }

    private fun anchorIdentity(obj: JSONObject, fields: List<String>): String {
        val field = fields.firstOrNull { key ->
            val value = obj.opt(key)
            value != null && value !== JSONObject.NULL && value !is JSONObject && value !is JSONArray && value.toString().isNotBlank()
        } ?: return ""
        val value = obj.opt(field).toString().trim().lowercase()
        return "$field:${value.length}:$value"
    }

    private fun identity(item: JSONObject, fields: List<String>): String {
        val values = fields.mapNotNull { field ->
            val value = item.opt(field) ?: return@mapNotNull null
            if (value === JSONObject.NULL || value is JSONObject || value is JSONArray) return@mapNotNull null
            value.toString().trim().takeIf(String::isNotEmpty)?.let { field to it.lowercase() }
        }
        return if (values.isNotEmpty()) values.joinToString("|") { (field, value) -> "$field:${value.length}:$value" }
        else "raw:${canonical(item)}"
    }

    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            JSONObject.quote(key) + ":" + canonical(value.opt(key))
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.opt(it)) }
        JSONObject.NULL -> "null"
        null -> "null"
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    private fun section(config: JSONObject, name: String): JSONArray? {
        if (!config.has(name)) return null
        val array = config.optJSONArray(name) ?: throw IllegalArgumentException("REPOSITORY_SECTION_NOT_ARRAY:$name")
        require(array.length() <= MAX_ITEMS_PER_SECTION) { "REPOSITORY_SECTION_TOO_LARGE:$name" }
        return array
    }
}

private data class AggregationRecord(val repositoryName: String, val sourceUrl: String, val sourceIndex: Int, val value: Any?)
