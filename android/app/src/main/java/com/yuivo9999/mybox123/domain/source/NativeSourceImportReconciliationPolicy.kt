package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.data.source.NativeContentSource

/**
 * Identifies prior source references that no longer point to a persisted source after an import.
 * Source IDs are globally unique in the repository, but the type is included so a reused ID that
 * changes from movie to live (or vice versa) also invalidates the former type's selection.
 */
object NativeSourceImportReconciliationPolicy {
    fun removedSources(
        previous: List<NativeContentSource>,
        persisted: List<NativeContentSource>,
    ): List<NativeContentSource> {
        val persistedKeys = persisted.map { it.sourceId to it.sourceType }.toSet()
        return previous.filter { (it.sourceId to it.sourceType) !in persistedKeys }
    }
}
