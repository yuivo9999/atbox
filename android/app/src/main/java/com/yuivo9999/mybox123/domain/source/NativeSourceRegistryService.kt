package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.data.storage.NativeUserDataRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rebuilds the native source registry snapshot after source mutations and reconciles source-owned
 * descriptor caches. This remains registry/cache infrastructure, not a live playback adapter.
 */
class NativeSourceRegistryService(
    private val userData: NativeUserDataRepository,
    private val liveCacheStore: NativeLiveCacheStore = NativeLiveCacheStore(userData),
) {
    fun readSnapshot(): JSONObject = runCatching {
        JSONObject(userData.readRaw(KEY_SNAPSHOT) ?: "{}")
    }.getOrDefault(JSONObject())

    fun liveGeneration(): Long = runCatching {
        JSONObject(userData.readRaw(KEY_LIVE_GENERATION) ?: "{}").optLong("value", 0L)
    }.getOrDefault(0L)

    /** Validates a caller-held descriptor against the authoritative current source record. */
    fun validateCurrentLiveSource(
        requested: NativeContentSource,
        current: NativeContentSource?,
    ): NativeContentSource = NativeLiveSourceDefinitionPolicy.requireCurrent(requested, current)

    /** Fail-safe before a source mutation: stale descriptor caches must not survive a failed registry sync. */
    fun invalidateLiveCachesBeforeSourceMutation(): Int = liveCacheStore.invalidateAllForSourceMutation()

    fun sync(sources: List<NativeContentSource>): NativeSourceRegistrySnapshot {
        val movie = JSONArray()
        val live = JSONArray()
        val liveFingerprintParts = mutableListOf<String>()
        val all = JSONArray()

        sources.sortedWith(compareBy<NativeContentSource> { it.order }.thenBy { it.sourceId }).forEach { source ->
            val raw = source.toJson()
            val descriptorFingerprint = stableHash(raw.toString())
            val entry = JSONObject()
                .put("sourceId", source.sourceId)
                .put("sourceType", source.sourceType)
                .put("sourceRef", source.sourceRef)
                .put("enabled", source.enabled)
                .put("order", source.order)
                .put("adapterType", source.adapterType ?: JSONObject.NULL)
                .put("tvboxAdapterKind", source.tvboxAdapterKind ?: JSONObject.NULL)
                .put("liveMode", raw.optString("liveMode").ifBlank { "unknown" })
                .put("descriptorFingerprint", if (source.sourceType == "live") descriptorFingerprint else JSONObject.NULL)
                .put("runtimeStatus", liveRuntimeStatus(source))
            all.put(entry)
            if (source.sourceType == "live") {
                live.put(entry)
                liveFingerprintParts += listOf(
                    source.sourceId, source.sourceRef, source.enabled.toString(), source.order.toString(),
                    source.adapterType.orEmpty(), source.tvboxAdapterKind.orEmpty(), raw.optString("liveMode"),
                    raw.optString("localFormat"), stableHash(raw.optString("localContent")), raw.optString("tvboxLiveProvider"),
                    raw.optString("sourceCapability"), raw.optString("tvboxApi"), raw.optString("tvboxExtFormat"),
                    descriptorFingerprint,
                ).joinToString("\u001f")
            } else {
                movie.put(entry)
            }
        }

        val previousSnapshot = runCatching { JSONObject(userData.readRaw(KEY_SNAPSHOT) ?: "{}") }.getOrDefault(JSONObject())
        val previousLiveFingerprint = previousSnapshot.optString("liveFingerprint")
        val invalidatedLiveCacheSources = liveCacheStore.reconcile(previousSnapshot.optJSONArray("live"), live)
        val nextLiveFingerprint = liveFingerprintParts.joinToString("\u001e")
        val oldGeneration = runCatching { JSONObject(userData.readRaw(KEY_LIVE_GENERATION) ?: "0").optLong("value", 0L) }
            .getOrDefault(0L)
        val generation = if (previousSnapshot.has("liveFingerprint") && previousLiveFingerprint != nextLiveFingerprint) oldGeneration + 1L else oldGeneration
        val snapshot = JSONObject()
            .put("schemaVersion", 1)
            .put("syncedAt", System.currentTimeMillis())
            .put("all", all)
            .put("movie", movie)
            .put("live", live)
            .put("liveCacheInvalidatedSources", JSONArray(invalidatedLiveCacheSources))
            .put("liveFingerprint", nextLiveFingerprint)
            .put("liveRuntimeImplemented", false)
            .put("liveRuntimeNote", "Descriptor parsing/cache service exists; native live adapter/playback consumer is not implemented")
        userData.writeManyRaw(
            mapOf(
                KEY_SNAPSHOT to snapshot.toString(),
                KEY_LIVE_GENERATION to JSONObject().put("value", generation).toString(),
            ),
        )
        return NativeSourceRegistrySnapshot(
            totalCount = sources.size,
            movieCount = movie.length(),
            liveCount = live.length(),
            enabledLiveCount = sources.count { it.sourceType == "live" && it.enabled },
            liveGeneration = generation,
            liveRuntimeImplemented = false,
        )
    }

    private fun stableHash(value: String): String {
        var hash = 2166136261L
        value.forEach { char -> hash = ((hash xor char.code.toLong()) * 16777619L) and 0xffffffffL }
        return hash.toString(36)
    }

    private fun liveRuntimeStatus(source: NativeContentSource): String = NativeSourceRuntimeStatusPolicy.liveStatus(source)

    companion object {
        const val KEY_SNAPSHOT = "native:source_registry:v1"
        const val KEY_LIVE_GENERATION = "native:live_registry_generation"
    }
}

data class NativeSourceRegistrySnapshot(
    val totalCount: Int,
    val movieCount: Int,
    val liveCount: Int,
    val enabledLiveCount: Int,
    val liveGeneration: Long,
    val liveRuntimeImplemented: Boolean,
)
