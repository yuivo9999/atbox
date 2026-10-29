package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.data.source.NativeContentSource

/**
 * Validates that a live descriptor request still refers to the current persisted source definition.
 * Cache generation is a separate in-flight invalidation fence; this policy validates source identity
 * and lifecycle state at operation boundaries.
 */
internal object NativeLiveSourceDefinitionPolicy {
    fun requireCurrent(
        requested: NativeContentSource,
        current: NativeContentSource?,
    ): NativeContentSource {
        require(current != null) { "SOURCE_NOT_FOUND" }
        require(current.sourceType == "live") { "SOURCE_TYPE_NOT_LIVE" }
        require(current.enabled) { "SOURCE_DISABLED" }
        require(requested.sourceId == current.sourceId) { "LIVE_SOURCE_ID_MISMATCH" }
        require(fingerprint(requested) == fingerprint(current)) { "LIVE_SOURCE_DEFINITION_STALE" }
        return current
    }

    fun fingerprint(source: NativeContentSource): String {
        var hash = 2166136261L
        source.toJson().toString().forEach { char ->
            hash = ((hash xor char.code.toLong()) * 16777619L) and 0xffffffffL
        }
        return hash.toString(36)
    }
}
