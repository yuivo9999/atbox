package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.core.network.NativeHttpClient
import com.yuivo9999.mybox123.core.network.NativeHttpUrlPolicy
import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.data.source.NativeContentSourceRepository
import com.yuivo9999.mybox123.data.storage.NativeUserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Native source CRUD and bounded URL health checks. Network work always runs on Dispatchers.IO. */
class NativeSourceManagementService(
    private val repository: NativeContentSourceRepository,
    private val httpClient: NativeHttpClient,
    private val userData: NativeUserDataRepository,
    private val registryService: NativeSourceRegistryService,
) {
    private val mutationLock = Any()
    @Volatile private var registrySyncRequired = false

    suspend fun getSources(): List<NativeContentSource> = withContext(Dispatchers.IO) {
        synchronized(mutationLock) {
            repairRegistryIfRequired()
            repository.getAll()
        }
    }

    suspend fun saveSource(source: JSONObject): NativeContentSource = withContext(Dispatchers.IO) {
        mutateAndSync {
            val requestedId = source.optString("sourceId").ifBlank { source.optString("id") }
            val previous = requestedId.takeIf { it.isNotBlank() }?.let { id -> repository.getAll().firstOrNull { it.sourceId == id } }
            val saved = repository.upsert(source)
            if (!saved.enabled) clearSelectionIfMatches(saved.sourceType, saved.sourceId)
            if (previous != null && previous.sourceType != saved.sourceType) {
                clearSelectionIfMatches(previous.sourceType, saved.sourceId)
                if (!saved.enabled) clearSelectionIfMatches(saved.sourceType, saved.sourceId)
            }
            saved
        }
    }

    suspend fun removeSource(sourceId: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(mutationLock) {
            repairRegistryIfRequired()
            val source = repository.getAll().firstOrNull { it.sourceId == sourceId }
            if (source == null) false else mutateAndSync {
                val removed = repository.remove(sourceId)
                if (removed) clearSelectionIfMatches(source.sourceType, sourceId)
                removed
            }
        }
    }

    suspend fun setEnabled(sourceId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        synchronized(mutationLock) {
            repairRegistryIfRequired()
            val source = repository.getAll().firstOrNull { it.sourceId == sourceId }
            if (source == null) false else mutateAndSync {
                val updated = repository.setEnabled(sourceId, enabled)
                if (updated && !enabled) clearSelectionIfMatches(source.sourceType, sourceId)
                updated
            }
        }
    }

    /**
     * Parse legacy TXT/M3U/relaxed JSON/TVBox source files and merge their definitions while
     * preserving user/runtime state for sources whose IDs remain stable.
     */
    suspend fun importText(text: String, fileName: String = "source"): List<NativeContentSource> = withContext(Dispatchers.IO) {
        val parsed = NativeSourceImportParser.parseText(text, fileName)
        mergeImportedSourcesOnIo(parsed)
    }

    suspend fun mergeImportedSources(imported: List<org.json.JSONObject>): List<NativeContentSource> = withContext(Dispatchers.IO) {
        mergeImportedSourcesOnIo(imported)
    }

    /** Export current source records as the same JSON-array shape accepted by the legacy source importer. */
    suspend fun exportSourcesJson(): String = withContext(Dispatchers.IO) {
        val array = org.json.JSONArray()
        repository.getAll().forEach { array.put(it.toJson()) }
        array.toString(2)
    }

    /** Restore missing project presets only; never replace or remove user-defined/current source records. */
    suspend fun restoreDefaultSources(): SourceRestoreResult = withContext(Dispatchers.IO) {
        mutateAndSync {
            val current = repository.getAll()
            val identities = current.map { identity(it) }.toMutableSet()
            val additions = defaultPresetSources().mapIndexedNotNull { index, json ->
                val preset = NativeContentSource.fromJson(json, current.size + index)
                if (identities.add(identity(preset))) preset else null
            }
            if (additions.isNotEmpty()) repository.saveAll(current + additions)
            SourceRestoreResult(added = additions.size, total = current.size + additions.size)
        }
    }

    private fun mergeImportedSourcesOnIo(imported: List<org.json.JSONObject>): List<NativeContentSource> {
        require(imported.isNotEmpty()) { "SOURCE_IMPORT_EMPTY" }
        return mutateAndSync {
            val current = repository.getAll()
            val importedSources = imported.mapIndexed { index, json -> NativeContentSource.fromJson(json, index) }
            val importedBundleIds = importedSources.map { it.toJson().optString("bundleId") }.filter(String::isNotBlank).toSet()
            val replaced = current.filter { it.toJson().optString("bundleId") in importedBundleIds }
            val previousById = replaced.associateBy { it.sourceId }
            val normalizedImported = importedSources.map { source ->
                val previous = previousById[source.sourceId] ?: return@map source
                val next = source.toJson()
                val old = previous.toJson()
                // Refresh definition but preserve user/runtime state, matching sourceManagement.importFile().
                listOf("enabled", "isActive", "liveMode", "status", "lastCheckedAt", "lastUsedAt", "capabilities", "createdAt").forEach { key ->
                    if (old.has(key) && !old.isNull(key)) next.put(key, old.opt(key))
                }
                NativeContentSource.fromJson(next)
            }
            val importedIdentities = normalizedImported.map { identity(it) }.toSet()
            val retained = current.filter { source ->
                source.toJson().optString("bundleId") !in importedBundleIds && identity(source) !in importedIdentities
            }
            val merged = repository.saveAll(retained + normalizedImported)
            // Identity-based de-duplication can remove a source even when it belongs to a
            // different bundle. Also treat an ID reused for the other source type as removed for
            // its former type, otherwise selected/default settings can point at the wrong source.
            NativeSourceImportReconciliationPolicy.removedSources(current, merged)
                .forEach { clearSelectionIfMatches(it.sourceType, it.sourceId) }
            merged
        }
    }

    /** Serialize mutation + registry reconciliation; a failed reconciliation remains dirty and is repaired before the next read/mutation. */
    private fun <T> mutateAndSync(block: () -> T): T = synchronized(mutationLock) {
        repairRegistryIfRequired()
        // Cache invalidation is intentionally performed before persistence. If a later registry
        // write fails, stale live descriptors are unavailable rather than silently reusable.
        registryService.invalidateLiveCachesBeforeSourceMutation()
        registrySyncRequired = true
        val result = block()
        try {
            syncRegistry()
            registrySyncRequired = false
        } catch (error: Exception) {
            throw IllegalStateException("SOURCE_REGISTRY_SYNC_REQUIRED", error)
        }
        result
    }

    private fun repairRegistryIfRequired() {
        if (!registrySyncRequired) return
        try {
            syncRegistry()
            registrySyncRequired = false
        } catch (error: Exception) {
            throw IllegalStateException("SOURCE_REGISTRY_SYNC_REQUIRED", error)
        }
    }

    private fun syncRegistry() {
        registryService.sync(repository.getAll())
    }

    private fun identity(source: NativeContentSource): String = "${source.sourceType}|${source.sourceRef.ifBlank { source.toJson().optString("url") }}"

    private fun clearSelectionIfMatches(sourceType: String, sourceId: String) {
        val selected = userData.getSelectedSources()
        if (selected.optString(sourceType).takeIf { it.isNotBlank() } == sourceId) {
            selected.put(sourceType, org.json.JSONObject.NULL)
            userData.saveSelectedSources(selected)
        }
        val settings = userData.getSettings()
        val defaultKey = if (sourceType == "live") "defaultLiveSource" else "defaultMovieSource"
        if (settings.optString(defaultKey).takeIf { it.isNotBlank() } == sourceId) {
            settings.put(defaultKey, org.json.JSONObject.NULL)
            userData.saveSettings(settings)
        }
    }

    suspend fun testSource(sourceId: String): SourceHealthCheckResult = withContext(Dispatchers.IO) {
        val source = repository.getAll().firstOrNull { it.sourceId == sourceId }
            ?: return@withContext SourceHealthCheckResult(sourceId, false, null, "SOURCE_NOT_FOUND", 0L)
        val ref = resolveProbeUrl(source.sourceRef)
            ?: return@withContext SourceHealthCheckResult(sourceId, false, null, "SOURCE_TEST_SCHEME_UNSUPPORTED", 0L)
        val startedAt = System.currentTimeMillis()
        val response = httpClient.execute(
            NativeHttpClient.NativeHttpRequest(
                url = ref,
                timeoutMs = NativeHttpClient.DEFAULT_TIMEOUT_MS,
                maxResponseBytes = SOURCE_TEST_MAX_BYTES,
            ),
        )
        SourceHealthCheckResult(
            sourceId = sourceId,
            ok = response.ok,
            httpStatus = response.status.takeIf { it > 0 },
            errorCode = response.errorCode ?: if (response.ok) null else "HTTP_${response.status}",
            durationMs = System.currentTimeMillis() - startedAt,
            contentType = response.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value,
            finalUrl = response.url,
        )
    }

    private fun resolveProbeUrl(rawValue: String): String? {
        val raw = rawValue.trim()
        if (NativeHttpUrlPolicy.normalize(raw) != null) return raw
        if (!raw.startsWith("proxy://", ignoreCase = true)) return null
        val encoded = Regex("(?:[?&]|^)ext=([^&]*)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1) ?: return null
        val decoded = try {
            URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            return null
        }
        return decoded.takeIf { NativeHttpUrlPolicy.normalize(it) != null }
    }

    companion object {
        private const val SOURCE_TEST_MAX_BYTES = 1024 * 1024

        /** Mirrors src/me/sourceManagement.js DEFAULT_PRESET_SOURCES; endpoints are not probed here. */
        private fun defaultPresetSources(): List<JSONObject> {
            val presets = org.json.JSONArray("""
                [
                  {"sourceId":"source_hhzy_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|豪华资源|4k","name":"🎥┃豪华┃资源","sourceType":"movie","sourceRef":"https://hhzyapi.com/api.php/provide/vod/?ac=list","url":"https://hhzyapi.com/api.php/provide/vod/?ac=list","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","categories":["内地剧","香港剧","欧美剧","韩剧","日剧","马泰剧","台湾剧","动画片","中国动漫","日本动漫","欧美动漫","剧情片","战争片","动作片","科幻片","记录片","爱情片","喜剧片","灾难片","悬疑片","犯罪片","大陆综艺","日韩综艺","港台综艺","欧美综艺"],"createdAt":1710000000000},
                  {"sourceId":"source_lzzy_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|量子资源|4k","name":"🎥┃量子┃资源","sourceType":"movie","sourceRef":"https://cj.lziapi.com/api.php/provide/vod/","url":"https://cj.lziapi.com/api.php/provide/vod/","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","categories":["国产动漫","日韩动漫","大陆剧","欧美剧","韩剧","日剧","动作片","喜剧片","爱情片","科幻片","恐怖片","剧情片","战争片"],"createdAt":1710000000001},
                  {"sourceId":"source_gszy_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|光速资源|4k","name":"🎥┃光速┃资源","sourceType":"movie","sourceRef":"https://api.guangsuapi.com/api.php/provide/vod/","url":"https://api.guangsuapi.com/api.php/provide/vod/","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","categories":["欧美剧","韩剧","日剧","动作片","喜剧片","爱情片","科幻片","恐怖片","剧情片","战争片"],"createdAt":1710000000002},
                  {"sourceId":"source_snzy_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|索尼资源|4k","name":"🎥┃索尼┃资源","sourceType":"movie","sourceRef":"https://suoniapi.com/api.php/provide/vod/","url":"https://suoniapi.com/api.php/provide/vod/","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","categories":["欧美剧","韩剧","日剧","动作片","喜剧片","爱情片","科幻片","恐怖片","剧情片","战争片","现代都市","古装仙侠"],"createdAt":1710000000003},
                  {"sourceId":"source_ikun_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|爱坤资源|4k","name":"🐓┃爱坤┃资源","sourceType":"movie","sourceRef":"https://ikunzyapi.com/api.php/provide/vod","url":"https://ikunzyapi.com/api.php/provide/vod","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","categories":["欧美剧","韩剧","日剧","动作片","喜剧片","爱情片","科幻片","恐怖片","剧情片","战争片"],"createdAt":1710000000004},
                  {"sourceId":"source_xiongzhang_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|熊掌高清|4k","name":"🐻┃熊掌┃高清","sourceType":"movie","sourceRef":"https://xzcjz.com/api.php/provide/vod","url":"https://xzcjz.com/api.php/provide/vod","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","createdAt":1710000000005},
                  {"sourceId":"source_hongniu_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|红牛资源|4k","name":"🎥┃红牛┃资源","sourceType":"movie","sourceRef":"https://www.hongniuzy2.com/api.php/provide/vod/","url":"https://www.hongniuzy2.com/api.php/provide/vod/","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","categories":["欧美剧","韩剧","日剧","动作片","喜剧片","爱情片","科幻片","恐怖片","剧情片","战争片"],"createdAt":1710000000006},
                  {"sourceId":"source_chulian_4k","bundleId":"bundle_preset_4k","sourceKey":"movie|初恋资源|4k","name":"🧡┃初恋┃官源","sourceType":"movie","sourceRef":"https://video.adminqt.cn/api.php/provide/vod/","url":"https://video.adminqt.cn/api.php/provide/vod/","sourceCapability":"direct-http-vod","adapterType":"http-vod","tvboxAdapterKind":"http-vod","enabled":true,"status":"未测试","createdAt":1710000000007},
                  {"sourceId":"source_live_zh_4k","bundleId":"bundle_preset_4k","sourceKey":"live|综合直播|4k","name":"📺┃央视卫视┃综合直播","sourceType":"live","sourceRef":"http://xhztv.top/njyy.txt","url":"http://xhztv.top/njyy.txt","enabled":true,"status":"未测试","liveMode":"tv1","createdAt":1710000000008}
                ]
            """.trimIndent())
            return buildList(presets.length()) {
                for (index in 0 until presets.length()) presets.optJSONObject(index)?.let(::add)
            }
        }
    }
}

data class SourceRestoreResult(val added: Int, val total: Int)

data class SourceHealthCheckResult(
    val sourceId: String,
    val ok: Boolean,
    val httpStatus: Int?,
    val errorCode: String?,
    val durationMs: Long,
    val contentType: String? = null,
    val finalUrl: String? = null,
)
