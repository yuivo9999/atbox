package com.yuivo9999.mybox123.data.source

import org.json.JSONObject

/** Typed view over a source record, retaining the original JSON for forward compatibility. */
data class NativeContentSource(
    val sourceId: String,
    val name: String,
    val sourceType: String,
    val sourceRef: String,
    val enabled: Boolean,
    val status: String,
    val order: Int,
    val adapterType: String?,
    val tvboxAdapterKind: String?,
    val rawJson: String,
) {
    fun toJson(): JSONObject = JSONObject(rawJson).apply {
        put("sourceId", sourceId)
        put("name", name)
        put("sourceType", sourceType)
        put("sourceRef", sourceRef)
        if (sourceRef.isNotBlank()) put("url", sourceRef)
        put("enabled", enabled)
        put("status", status)
        put("order", order)
        adapterType?.let { put("adapterType", it) }
        tvboxAdapterKind?.let { put("tvboxAdapterKind", it) }
    }

    companion object {
        fun fromJson(json: JSONObject, index: Int = 0): NativeContentSource {
            val fallbackId = "legacy-source:${index + 1}"
            val sourceId = readString(json, "sourceId").trim().ifEmpty {
                readString(json, "id").trim().ifEmpty { fallbackId }
            }
            val sourceType = if (readString(json, "sourceType").equals("live", ignoreCase = true)) "live" else "movie"
            val sourceRef = readString(json, "sourceRef").trim().ifEmpty { readString(json, "url").trim() }
            val status = readString(json, "status").ifEmpty { "未测试" }
            val adapterType = readString(json, "adapterType").takeIf { it.isNotBlank() }
            val adapterKind = readString(json, "tvboxAdapterKind").takeIf { it.isNotBlank() }
            val normalized = JSONObject(json.toString()).apply {
                put("sourceId", sourceId)
                put("name", readString(json, "name").trim().ifEmpty { sourceId })
                put("sourceType", sourceType)
                put("sourceRef", sourceRef)
                put("url", readString(json, "url").trim().ifEmpty { sourceRef })
                put("bundleId", readString(json, "bundleId").trim().ifEmpty { "bundle_$sourceId" })
                put("sourceKey", readString(json, "sourceKey").trim().ifEmpty { sourceId })
                put("enabled", !json.has("enabled") || json.optBoolean("enabled", true))
                put("status", status)
                put("order", json.optInt("order", index + 1))
                val rawCapabilities = json.optJSONArray("capabilities")
                val capabilities = org.json.JSONArray()
                val seenCapabilities = linkedSetOf<String>()
                if (rawCapabilities != null) {
                    for (capabilityIndex in 0 until rawCapabilities.length()) {
                        val capability = rawCapabilities.optString(capabilityIndex).trim()
                        if (capability.isNotEmpty() && seenCapabilities.add(capability)) capabilities.put(capability)
                    }
                }
                put("capabilities", capabilities)
            }
            return NativeContentSource(
                sourceId = sourceId,
                name = normalized.optString("name"),
                sourceType = sourceType,
                sourceRef = sourceRef,
                enabled = normalized.optBoolean("enabled", true),
                status = status,
                order = normalized.optInt("order", index + 1),
                adapterType = adapterType,
                tvboxAdapterKind = adapterKind,
                rawJson = normalized.toString(),
            )
        }

        private fun readString(json: JSONObject, key: String): String =
            if (!json.has(key) || json.isNull(key)) "" else json.optString(key, "")
    }
}
