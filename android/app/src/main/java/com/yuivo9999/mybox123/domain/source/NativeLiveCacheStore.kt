package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.data.storage.NativeUserDataRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent cache ownership boundary for native live-source data. Every cache entry is owned by
 * one sourceId and grouped by kind (channels, streams, epg). Source lifecycle reconciliation drops
 * all entries for removed, disabled, or descriptor-changed sources. This store does not fetch or play.
 */
class NativeLiveCacheStore private constructor(private val persistence: Persistence, private val nowMillis: () -> Long) {
    private val readStore: () -> JSONObject get() = persistence.read
    private val writeStore: (JSONObject) -> Unit get() = persistence.write
    /** Process-local fence used to prevent an in-flight descriptor load from repopulating invalidated data. */
    private var cacheGeneration: Long = 0L

    @Synchronized
    fun generation(): Long = cacheGeneration

    constructor(userData: NativeUserDataRepository) : this(Persistence(
        read = { userData.readJsonObject(KEY_STORE) },
        write = { userData.writeJsonObject(KEY_STORE, it) },
    ), System::currentTimeMillis)

    /** Test seam for deterministic cache lifecycle tests without Android storage. */
    internal constructor(read: () -> JSONObject, write: (JSONObject) -> Unit, testSeam: Unit, now: () -> Long = System::currentTimeMillis) : this(Persistence(read, write), now)
    @Synchronized
    fun get(sourceId: String, kind: String, cacheKey: String): JSONObject? {
        requireValid(sourceId, kind, cacheKey)
        purgeExpired()
        return readEntry(sourceId, kind, cacheKey)
    }

    /** Reads a cache entry only if the caller's generation is still current at the read's linearization point. */
    @Synchronized
    fun getIfGeneration(sourceId: String, kind: String, cacheKey: String, expectedGeneration: Long): JSONObject? {
        if (expectedGeneration != cacheGeneration) return null
        requireValid(sourceId, kind, cacheKey)
        purgeExpired()
        if (expectedGeneration != cacheGeneration) return null
        return readEntry(sourceId, kind, cacheKey)
    }

    private fun readEntry(sourceId: String, kind: String, cacheKey: String): JSONObject? = runCatching {
        val entry = readRoot().optJSONObject(sourceId)?.optJSONObject(kind)?.optJSONObject(cacheKey)
            ?: return@runCatching null
        val wrapped = entry.optJSONObject("value")
        JSONObject((wrapped ?: entry).toString())
    }.getOrNull()

    /** Writes only if no source-registry invalidation occurred since the caller began loading. */
    @Synchronized
    fun putIfGeneration(sourceId: String, kind: String, cacheKey: String, value: JSONObject, expectedGeneration: Long): Boolean {
        if (expectedGeneration != cacheGeneration) return false
        put(sourceId, kind, cacheKey, value)
        return true
    }

    @Synchronized
    fun put(sourceId: String, kind: String, cacheKey: String, value: JSONObject) {
        requireValid(sourceId, kind, cacheKey)
        val serializedValue = value.toString()
        require(serializedValue.length <= MAX_CACHE_VALUE_CHARS) { "LIVE_CACHE_VALUE_TOO_LARGE" }
        purgeExpired()
        val root = readRoot()
        val now = nowMillis()
        val source = root.optJSONObject(sourceId) ?: JSONObject()
        val bucket = source.optJSONObject(kind) ?: JSONObject()
        bucket.put(cacheKey, JSONObject().put("storedAt", now).put("expiresAt", now + TTL_BY_KIND.getValue(kind)).put("value", JSONObject(serializedValue)))
        source.put(kind, bucket).put("updatedAt", now)
        root.put(sourceId, source)
        enforceBounds(root, sourceId, kind)
        writeStore(root)
    }

    /** Removes expired entries across all owners. Also upgrades legacy entries with TTL on read. */
    @Synchronized
    fun purgeExpired(): Int {
        val root = readRoot()
        val now = nowMillis()
        var removed = 0
        val sourceKeys = root.keys().asSequence().toList()
        sourceKeys.forEach sourceLoop@{ sourceId ->
            val source = root.optJSONObject(sourceId) ?: return@sourceLoop
            CACHE_KINDS.forEach kindLoop@{ kind ->
                val bucket = source.optJSONObject(kind) ?: return@kindLoop
                val entryKeys = bucket.keys().asSequence().toList()
                entryKeys.forEach entryLoop@{ key ->
                    val entry = bucket.optJSONObject(key)
                    if (entry == null || isExpired(entry, kind, now)) {
                        bucket.remove(key)
                        removed++
                    }
                }
                if (bucket.length() == 0) source.remove(kind)
            }
            pruneEmptySource(root, sourceId)
        }
        if (removed > 0) writeStore(root)
        return removed
    }

    /** Clears all source-owned live data and advances the fence even when the store is already empty. */
    @Synchronized
    fun invalidateAllForSourceMutation(): Int = clearAllEntries()

    /** User-requested cache cleanup; advances the generation so in-flight loads cannot repopulate it. */
    @Synchronized
    fun clearAllEntries(): Int {
        val root = readRoot()
        var removed = 0
        root.keys().asSequence().toList().forEach { sourceId ->
            val source = root.optJSONObject(sourceId) ?: return@forEach
            CACHE_KINDS.forEach { kind -> removed += source.optJSONObject(kind)?.length() ?: 0 }
        }
        cacheGeneration++
        writeStore(JSONObject())
        return removed
    }

    @Synchronized
    fun clearSource(sourceId: String): Boolean {
        if (sourceId.isBlank()) return false
        val root = readRoot()
        val existed = root.has(sourceId)
        if (existed) {
            root.remove(sourceId)
            cacheGeneration++
            writeStore(root)
        }
        return existed
    }

    @Synchronized
    fun clearKind(kind: String): Int {
        require(kind in CACHE_KINDS) { "LIVE_CACHE_KIND_UNSUPPORTED" }
        val root = readRoot()
        var removed = 0
        val keys = root.keys()
        val updated = JSONObject()
        while (keys.hasNext()) {
            val sourceId = keys.next()
            val source = root.optJSONObject(sourceId) ?: continue
            if (source.has(kind)) {
                source.remove(kind)
                removed++
            }
            if (source.length() > 1 || (source.length() == 1 && !source.has("updatedAt"))) updated.put(sourceId, source)
        }
        if (removed > 0) cacheGeneration++
        writeStore(updated)
        return removed
    }

    /** Invalidates only changed/disabled/removed source owners; unrelated source caches survive. */
    @Synchronized
    fun reconcile(previousLive: JSONArray?, nextLive: JSONArray): List<String> {
        purgeExpired()
        val old = entriesBySource(previousLive)
        val next = entriesBySource(nextLive)
        val cacheOwners = readRoot().keys().asSequence().toSet()
        val candidates = (old.keys + next.keys + cacheOwners).toSortedSet()
        val changedOwners = candidates.filter { sourceId ->
            val before = old[sourceId]
            val after = next[sourceId]
            when {
                after == null -> before != null || sourceId in cacheOwners
                before == null -> true
                !after.optBoolean("enabled", false) -> before.optBoolean("enabled", false) || sourceId in cacheOwners
                else -> before.optString("descriptorFingerprint") != after.optString("descriptorFingerprint") || !before.optBoolean("enabled", false)
            }
        }
        val invalidated = changedOwners.filter { it in cacheOwners }
        if (changedOwners.isNotEmpty()) cacheGeneration++
        if (invalidated.isNotEmpty()) {
            val root = readRoot()
            invalidated.forEach(root::remove)
            writeStore(root)
        }
        return invalidated
    }

    fun summary(): JSONObject {
        purgeExpired()
        val root = readRoot()
        val sources = JSONArray()
        val keys = root.keys()
        while (keys.hasNext()) {
            val sourceId = keys.next()
            val entry = root.optJSONObject(sourceId) ?: continue
            sources.put(JSONObject()
                .put("sourceId", sourceId)
                .put("channelEntries", entry.optJSONObject("channels")?.length() ?: 0)
                .put("streamEntries", entry.optJSONObject("streams")?.length() ?: 0)
                .put("epgEntries", entry.optJSONObject("epg")?.length() ?: 0))
        }
        return JSONObject().put("sourceCount", sources.length()).put("sources", sources)
    }

    private fun enforceBounds(root: JSONObject, activeSourceId: String, activeKind: String) {
        val activeSource = root.optJSONObject(activeSourceId) ?: return
        val activeBucket = activeSource.optJSONObject(activeKind) ?: return
        while (activeBucket.length() > MAX_ENTRIES_PER_KIND) {
            removeOldestEntry(activeBucket)
        }
        while (root.toString().length > MAX_STORE_CHARS) {
            var oldestSource = ""
            var oldestKind = ""
            var oldestKey = ""
            var oldestAt = Long.MAX_VALUE
            val sourceKeys = root.keys()
            while (sourceKeys.hasNext()) {
                val sourceId = sourceKeys.next()
                val source = root.optJSONObject(sourceId) ?: continue
                for (kind in CACHE_KINDS) {
                    val bucket = source.optJSONObject(kind) ?: continue
                    val entryKeys = bucket.keys()
                    while (entryKeys.hasNext()) {
                        val key = entryKeys.next()
                        val entry = bucket.optJSONObject(key) ?: continue
                        val storedAt = entry.optLong("storedAt", source.optLong("updatedAt", 0L))
                        if (storedAt < oldestAt) {
                            oldestAt = storedAt
                            oldestSource = sourceId
                            oldestKind = kind
                            oldestKey = key
                        }
                    }
                }
            }
            if (oldestSource.isEmpty()) throw IllegalStateException("LIVE_CACHE_STORE_LIMIT_EXCEEDED")
            root.optJSONObject(oldestSource)?.optJSONObject(oldestKind)?.remove(oldestKey)
            pruneEmptySource(root, oldestSource)
        }
        pruneEmptySource(root, activeSourceId)
    }

    private fun isExpired(entry: JSONObject, kind: String, now: Long): Boolean {
        val storedAt = entry.optLong("storedAt", 0L)
        // Old entries predate expiresAt; apply the current kind-specific TTL to them too.
        val expiresAt = entry.optLong("expiresAt", storedAt + TTL_BY_KIND.getValue(kind))
        return storedAt <= 0L || expiresAt <= now
    }

    private fun removeOldestEntry(bucket: JSONObject) {
        var oldestKey: String? = null
        var oldestAt = Long.MAX_VALUE
        val keys = bucket.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = bucket.optJSONObject(key) ?: continue
            val storedAt = entry.optLong("storedAt", 0L)
            if (storedAt < oldestAt) { oldestAt = storedAt; oldestKey = key }
        }
        oldestKey?.let(bucket::remove)
    }

    private fun pruneEmptySource(root: JSONObject, sourceId: String) {
        val source = root.optJSONObject(sourceId) ?: return
        val hasEntries = CACHE_KINDS.any { (source.optJSONObject(it)?.length() ?: 0) > 0 }
        if (!hasEntries) root.remove(sourceId)
    }

    private fun entriesBySource(array: JSONArray?): Map<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        if (array == null) return result
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("sourceId").trim()
            if (id.isNotEmpty()) result[id] = item
        }
        return result
    }

    private fun readRoot(): JSONObject = runCatching { readStore() }.getOrDefault(JSONObject())

    private fun requireValid(sourceId: String, kind: String, cacheKey: String) {
        require(sourceId.isNotBlank() && sourceId.length <= 256) { "LIVE_CACHE_SOURCE_ID_INVALID" }
        require(kind in CACHE_KINDS) { "LIVE_CACHE_KIND_UNSUPPORTED" }
        require(cacheKey.isNotBlank() && cacheKey.length <= 512) { "LIVE_CACHE_KEY_INVALID" }
    }

    private data class Persistence(val read: () -> JSONObject, val write: (JSONObject) -> Unit)

    companion object {
        const val KEY_STORE = "native:live_cache_store:v1"
        private const val MAX_CACHE_VALUE_CHARS = 5 * 1024 * 1024
        private const val MAX_STORE_CHARS = 12 * 1024 * 1024
        private const val MAX_ENTRIES_PER_KIND = 128
        const val CHANNELS_TTL_MS = 6 * 60 * 60 * 1000L
        const val STREAMS_TTL_MS = 30 * 60 * 1000L
        const val EPG_TTL_MS = 12 * 60 * 60 * 1000L
        private val TTL_BY_KIND = mapOf(
            "channels" to CHANNELS_TTL_MS,
            "streams" to STREAMS_TTL_MS,
            "epg" to EPG_TTL_MS,
        )
        val CACHE_KINDS = setOf("channels", "streams", "epg")
    }
}
