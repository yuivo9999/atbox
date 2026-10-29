package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.core.network.NativeHttpUrlPolicy
import com.yuivo9999.mybox123.core.network.NativeHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/** Local TVBox repository definitions with bounded single-repository and read-only batch aggregation previews. */
class RepositoryManagementViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(RepositoryManagementUiState())
    private var syncingRepositoryId: String? = null
    val uiState: StateFlow<RepositoryManagementUiState> = _uiState.asStateFlow()

    fun load(force: Boolean = false) {
        if (_uiState.value.loading || (_uiState.value.loaded && !force)) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val repositories = withContext(Dispatchers.IO) {
                    val repo = application.awaitServices().userDataRepository
                    if (repo.readRaw(KEY_MULTI_REPOS) == null) {
                        defaultRepositories().also { repo.writeJsonArray(KEY_MULTI_REPOS, it) }
                    } else {
                        repo.readJsonArray(KEY_MULTI_REPOS)
                    }
                }
                _uiState.update { it.copy(loading = false, loaded = true, repositories = repositories.toEntryList(), error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.message ?: "REPOSITORIES_LOAD_FAILED") }
            }
        }
    }

    fun save(name: String, primaryUrl: String, backupUrlsText: String, editingId: String? = null) {
        val cleanName = name.trim()
        val cleanUrl = primaryUrl.trim()
        if (cleanName.isBlank()) return fail("请输入仓库名称。")
        if (NativeHttpUrlPolicy.normalize(cleanUrl) == null) return fail("主地址必须是有效的 HTTP(S) URL。")
        val backups = backupUrlsText.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()
        if (backups.any { NativeHttpUrlPolicy.normalize(it) == null }) return fail("备用地址包含无效的 HTTP(S) URL。")
        val current = _uiState.value.repositories
        val id = editingId ?: "repo_${UUID.randomUUID()}"
        val previous = current.firstOrNull { it.id == id }
        val json = (previous?.toJson() ?: JSONObject())
            .put("id", id)
            .put("name", cleanName)
            .put("primaryUrl", cleanUrl)
            .put("backupUrls", JSONArray(backups))
            .put("enabled", previous?.enabled ?: true)
            .put("status", "idle")
        val next = if (previous == null) listOf(RepositoryEntry.fromJson(json)) + current else current.map { if (it.id == id) RepositoryEntry.fromJson(json) else it }
        persist(next, if (previous == null) "已添加仓库。" else "仓库已更新。")
    }

    fun toggle(entry: RepositoryEntry, enabled: Boolean) {
        persist(_uiState.value.repositories.map { if (it.id == entry.id) RepositoryEntry.fromJson(it.toJson().put("enabled", enabled)) else it }, if (enabled) "仓库已启用。" else "仓库已停用。")
    }

    fun remove(entry: RepositoryEntry) {
        persist(_uiState.value.repositories.filterNot { it.id == entry.id }, "已删除仓库：${entry.name}")
    }

    fun importText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return fail("请粘贴仓库 URL、逐行列表或 TVBox urls JSON。")
        if (trimmed.length > MAX_IMPORT_BYTES || trimmed.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) {
            return fail("导入文本超过 1 MiB 限制。")
        }
        val imported = try {
            parseImport(trimmed)
        } catch (error: IllegalArgumentException) {
            return fail(error.message ?: "导入文本格式无效。")
        } catch (_: Exception) {
            return fail("导入文本格式无效。")
        }
        if (imported.isEmpty()) return fail("没有找到有效的仓库 URL。")
        val invalid = imported.firstOrNull { NativeHttpUrlPolicy.normalize(it.second) == null || it.third.any { url -> NativeHttpUrlPolicy.normalize(url) == null } }
        if (invalid != null) return fail("导入内容包含无效的 HTTP(S) URL：${invalid.first}")

        val existingUrls = _uiState.value.repositories.mapNotNull { canonicalUrl(it.primaryUrl) }.toMutableSet()
        var duplicateCount = 0
        val additions = imported.mapNotNull { (name, url, backups) ->
            val primaryKey = canonicalUrl(url) ?: return@mapNotNull null
            if (!existingUrls.add(primaryKey)) {
                duplicateCount++
                return@mapNotNull null
            }
            val backupKeys = mutableSetOf(primaryKey)
            val normalizedBackups = backups.filter { backup ->
                val key = canonicalUrl(backup) ?: return@filter false
                backupKeys.add(key)
            }
            RepositoryEntry.fromJson(JSONObject()
                .put("id", "repo_${UUID.randomUUID()}")
                .put("name", name.ifBlank { "导入仓库" })
                .put("primaryUrl", url)
                .put("backupUrls", JSONArray(normalizedBackups))
                .put("enabled", true)
                .put("status", "idle")
                .put("createdAt", System.currentTimeMillis()))
        }
        if (additions.isEmpty()) return fail("导入的仓库已全部存在或重复；跳过 $duplicateCount 个重复主地址。")
        persist(additions + _uiState.value.repositories, "成功导入 ${additions.size} 个仓库；跳过 $duplicateCount 个重复主地址。")
    }

    /** Fetches a bounded subset of enabled repositories and produces an in-memory aggregation summary only. */
    fun aggregatePreview() {
        if (_uiState.value.saving || _uiState.value.syncingRepositoryId != null || _uiState.value.aggregationRunning) return
        val enabled = _uiState.value.repositories.filter { it.enabled }.take(MAX_AGGREGATION_REPOSITORIES)
        if (enabled.isEmpty()) return fail("没有已启用的仓库可供聚合预览。")
        _uiState.update { it.copy(aggregationRunning = true, aggregation = null, error = null, message = "正在顺序读取最多 $MAX_AGGREGATION_REPOSITORIES 个已启用仓库；结果仅供预览。") }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = application.awaitServices().httpClient
                    val configs = mutableListOf<RepositoryConfigInput>()
                    val outcomes = mutableListOf<RepositoryFetchOutcome>()
                    enabled.forEach { entry ->
                        try {
                            val fetched = fetchRepositoryConfig(client, entry)
                            configs += RepositoryConfigInput(entry.name, fetched.second, fetched.first)
                            outcomes += RepositoryFetchOutcome(entry.name, true, fetched.second, "读取成功")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            val reason = classifyAggregationFailure(error.message ?: "SYNC_FAILED")
                            outcomes += RepositoryFetchOutcome(entry.name, false, "", reason)
                        }
                    }
                    if (configs.isEmpty()) throw IllegalStateException("所选仓库均同步失败：${outcomes.take(3).joinToString("；") { "${it.name}（${it.detail}）" }}")
                    RepositoryConfigAggregator.summarize(
                        configs,
                        successfulRepositories = configs.size,
                        failedRepositories = outcomes.filterNot { it.success }.map { "${it.name}（${it.detail}）" },
                        repositoryOutcomes = outcomes,
                    ).let { summary -> summary to outcomes }
                }
                val summary = result.first.toUiSummary(result.second)
                _uiState.update { it.copy(aggregationRunning = false, aggregation = summary, error = null, message = "批量聚合预览完成：成功 ${summary.successfulRepositories} 个，失败 ${summary.failedRepositories.size} 个。结构化 JSON 仅供复制导出，未保存或应用。") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(aggregationRunning = false, error = error.message ?: "REPOSITORY_AGGREGATION_PREVIEW_FAILED") }
            } finally {
                _uiState.update { if (it.aggregationRunning) it.copy(aggregationRunning = false) else it }
            }
        }
    }

    private fun fetchRepositoryConfig(client: NativeHttpClient, entry: RepositoryEntry): Pair<JSONObject, String> {
        val candidates = (listOf(entry.primaryUrl) + entry.backupUrls)
            .mapNotNull { NativeHttpUrlPolicy.normalize(it) }
            .distinct()
            .take(MAX_AGGREGATION_ADDRESSES_PER_REPOSITORY)
        var lastFailure = "REPOSITORY_ALL_ADDRESSES_FAILED"
        for (url in candidates) {
            val response = client.execute(NativeHttpClient.NativeHttpRequest(
                url = url,
                timeoutMs = AGGREGATION_REQUEST_TIMEOUT_MS,
                maxResponseBytes = MAX_REPOSITORY_RESPONSE_BYTES,
                followRedirects = true,
            ))
            if (!response.ok) {
                lastFailure = response.errorCode ?: "HTTP_${response.status}"
                continue
            }
            try {
                return RepositoryConfigAggregator.parseConfig(response.body) to (response.url.ifBlank { url })
            } catch (error: Exception) {
                lastFailure = error.message?.takeIf { it.startsWith("REPOSITORY_") } ?: "REPOSITORY_CONFIG_SHAPE_UNSUPPORTED"
            }
        }
        throw IllegalStateException(lastFailure)
    }

    /** Fetches one repository with ordered primary/backup fallback and previews only recognized TVBox counts. */
    fun syncPreview(entry: RepositoryEntry) {
        if (_uiState.value.saving || _uiState.value.syncingRepositoryId != null || _uiState.value.aggregationRunning) return
        if (!entry.enabled) return fail("请先启用该仓库，再执行同步预览。")
        syncingRepositoryId = entry.id
        _uiState.update { it.copy(syncingRepositoryId = entry.id, error = null, message = "正在按主地址、备用地址顺序尝试：${entry.name}") }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = application.awaitServices().httpClient
                    val candidates = (listOf(entry.primaryUrl) + entry.backupUrls).mapNotNull { NativeHttpUrlPolicy.normalize(it) }.distinct()
                    var lastFailure = "REPOSITORY_ALL_ADDRESSES_FAILED"
                    for ((index, url) in candidates.withIndex()) {
                        val response = client.execute(NativeHttpClient.NativeHttpRequest(
                            url = url,
                            timeoutMs = 10_000L,
                            maxResponseBytes = 2 * 1024 * 1024,
                            followRedirects = true,
                        ))
                        if (!response.ok) {
                            lastFailure = response.errorCode ?: "HTTP_${response.status}"
                            continue
                        }
                        val parsed = try {
                            val tokener = JSONTokener(response.body)
                            val value = tokener.nextValue()
                            if (tokener.nextClean() != '\u0000' || value !is JSONObject) null else value
                        } catch (_: Exception) { null }
                        if (parsed == null) {
                            lastFailure = "REPOSITORY_JSON_INVALID"
                            continue
                        }
                        val sites = parsed.optJSONArray("sites")
                        val lives = parsed.optJSONArray("lives")
                        val parses = parsed.optJSONArray("parses")
                        if ((parsed.has("sites") && sites == null) ||
                            (parsed.has("lives") && lives == null) ||
                            (parsed.has("parses") && parses == null) ||
                            (sites == null && lives == null)) {
                            lastFailure = "REPOSITORY_CONFIG_SHAPE_UNSUPPORTED"
                            continue
                        }
                        return@withContext RepositorySyncPreview(
                            usedUrl = response.url.ifBlank { url },
                            usedBackupIndex = index,
                            siteCount = sites?.length() ?: 0,
                            liveCount = lives?.length() ?: 0,
                            parseCount = parses?.length() ?: 0,
                        )
                    }
                    throw IllegalStateException(lastFailure)
                }
                val route = if (result.usedBackupIndex == 0) "主地址" else "备用地址 ${result.usedBackupIndex}"
                val updated = _uiState.value.repositories.map { current ->
                    if (current.id == entry.id) RepositoryEntry.fromJson(current.toJson()
                        .put("status", "preview_ok")
                        .put("lastSyncAt", System.currentTimeMillis())
                        .put("usedUrl", result.usedUrl)
                        .put("siteCount", result.siteCount)
                        .put("liveCount", result.liveCount)
                        .put("parseCount", result.parseCount)
                        .put("errorMessage", "")) else current
                }
                persistResult(updated, "仓库预览成功（$route）：影视站点 ${result.siteCount}，直播分组 ${result.liveCount}，解析规则 ${result.parseCount}。未导入或聚合数据。")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: "REPOSITORY_SYNC_FAILED"
                val updated = _uiState.value.repositories.map { current ->
                    if (current.id == entry.id) RepositoryEntry.fromJson(current.toJson()
                        .put("status", "preview_failed")
                        .put("lastSyncAt", System.currentTimeMillis())
                        .put("errorMessage", message)) else current
                }
                persistResult(updated, "仓库预览失败：$message；已按顺序尝试所有有效地址。")
            } finally {
                syncingRepositoryId = null
                _uiState.update { it.copy(syncingRepositoryId = null) }
            }
        }
    }

    fun restoreDefaults() = persist(defaultRepositories().toEntryList(), "已恢复内置仓库列表；未执行联网同步。")

    private suspend fun persistResult(next: List<RepositoryEntry>, message: String) {
        _uiState.update { it.copy(saving = true, error = null, message = null) }
        try {
            withContext(Dispatchers.IO) {
                application.awaitServices().userDataRepository.writeJsonArray(KEY_MULTI_REPOS, JSONArray().apply { next.forEach { put(it.toJson()) } })
            }
            _uiState.update { it.copy(saving = false, loaded = true, repositories = next, message = message, error = null) }
        } catch (cancelled: CancellationException) {
            _uiState.update { it.copy(saving = false) }
            throw cancelled
        } catch (error: Exception) {
            _uiState.update { it.copy(saving = false, error = error.message ?: "REPOSITORIES_SAVE_FAILED") }
        }
    }

    private fun persist(next: List<RepositoryEntry>, message: String) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, message = null) }
            try {
                withContext(Dispatchers.IO) {
                    application.awaitServices().userDataRepository.writeJsonArray(KEY_MULTI_REPOS, JSONArray().apply { next.forEach { put(it.toJson()) } })
                }
                _uiState.update { it.copy(saving = false, loaded = true, repositories = next, message = message, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(saving = false, error = error.message ?: "REPOSITORIES_SAVE_FAILED") }
            }
        }
    }

    private fun fail(message: String) = _uiState.update { it.copy(error = message, message = null) }

    private fun parseImport(text: String): List<Triple<String, String, List<String>>> {
        if (text.startsWith("{") || text.startsWith("[")) {
            val root = if (text.startsWith("[")) {
                JSONArray(text)
            } else {
                val objectRoot = JSONObject(text)
                objectRoot.optJSONArray("urls") ?: throw IllegalArgumentException("JSON 仓库配置必须包含 urls 数组。")
            }
            if (root.length() > MAX_IMPORTED_REPOSITORIES) {
                throw IllegalArgumentException("单次最多导入 $MAX_IMPORTED_REPOSITORIES 个仓库。")
            }
            return buildList {
                for (index in 0 until root.length()) {
                    val item = root.opt(index) as? JSONObject
                        ?: throw IllegalArgumentException("第 ${index + 1} 条仓库记录必须是 JSON 对象。")
                    val url = item.opt("url") as? String
                    if (url.isNullOrBlank()) throw IllegalArgumentException("第 ${index + 1} 条仓库记录缺少字符串 url。")
                    val nameValue = if (item.has("name")) item.opt("name") else "导入仓库 ${index + 1}"
                    val name = nameValue as? String
                        ?: throw IllegalArgumentException("第 ${index + 1} 条仓库记录的 name 必须是字符串。")
                    val backupsJson = if (item.has("backupUrls")) item.opt("backupUrls") as? JSONArray
                        ?: throw IllegalArgumentException("第 ${index + 1} 条仓库记录的 backupUrls 必须是数组。") else JSONArray()
                    if (backupsJson.length() > MAX_BACKUP_URLS_PER_REPOSITORY) {
                        throw IllegalArgumentException("第 ${index + 1} 条仓库记录的备用地址不能超过 $MAX_BACKUP_URLS_PER_REPOSITORY 个。")
                    }
                    val backups = buildList {
                        for (backupIndex in 0 until backupsJson.length()) {
                            val backup = backupsJson.opt(backupIndex) as? String
                                ?: throw IllegalArgumentException("第 ${index + 1} 条仓库记录包含非字符串备用地址。")
                            if (backup.isNotBlank()) add(backup.trim())
                        }
                    }
                    add(Triple(name.trim(), url.trim(), backups))
                }
            }
        }
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).take(MAX_IMPORTED_REPOSITORIES + 1).toList()
        if (lines.size > MAX_IMPORTED_REPOSITORIES) {
            throw IllegalArgumentException("逐行导入最多支持 $MAX_IMPORTED_REPOSITORIES 个仓库。")
        }
        return lines.mapIndexed { index, line ->
            if (line.length > MAX_IMPORT_LINE_LENGTH) throw IllegalArgumentException("第 ${index + 1} 行超过 $MAX_IMPORT_LINE_LENGTH 个字符。")
            val separator = when { line.contains(',') -> ','; line.contains('#') -> '#'; else -> null }
            val name = separator?.let { line.substringBefore(it).trim() }.orEmpty()
            val url = (separator?.let { line.substringAfter(it).trim() } ?: line).trim()
            if (NativeHttpUrlPolicy.normalize(url) == null) {
                throw IllegalArgumentException("第 ${index + 1} 行不是有效的 HTTP(S) URL。")
            }
            Triple(name.ifBlank { "导入仓库 ${index + 1}" }, url, emptyList())
        }
    }

    private fun canonicalUrl(raw: String): String? {
        val normalized = NativeHttpUrlPolicy.normalize(raw) ?: return null
        return normalized.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()?.toString()
    }

    private fun defaultRepositories() = JSONArray().apply {
        put(defaultRepo("repo_fty", "饭太硬", "http://xn--sss604efuw.com/tv", listOf("https://www.ftytv.com/tv", "https://ghfast.top/https://raw.githubusercontent.com/fantaiying/ext/master/tvbox.json")))
        put(defaultRepo("repo_feimao", "肥猫", "http://xn--z7x900a.com/tv", listOf("http://feedcat.top/tv")))
        put(defaultRepo("repo_qiaoji", "巧技", "http://cdn.qiaoji8.com/tvbox.json", listOf("https://ghfast.top/https://raw.githubusercontent.com/qiaoji8/tvbox/master/tvbox.json")))
        put(defaultRepo("repo_moyu", "摸鱼儿", "http://xn--654a.xn--2qux23c9zi.top", listOf("https://ghfast.top/https://raw.githubusercontent.com/moyu/tvbox/master/tv.json")))
        put(defaultRepo("repo_xiaopingguo", "小苹果", "https://agit.ai/pingguo/rec/raw/branch/master/tvbox.json", listOf("https://ghproxy.net/https://raw.githubusercontent.com/xiaopingguo/tvbox/master/apple.json")))
    }

    private fun defaultRepo(id: String, name: String, url: String, backups: List<String>) = JSONObject()
        .put("id", id).put("name", name).put("primaryUrl", url).put("backupUrls", JSONArray(backups))
        .put("enabled", true).put("status", "idle").put("siteCount", 0).put("liveCount", 0)

    private fun JSONArray.toEntryList(): List<RepositoryEntry> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let { add(RepositoryEntry.fromJson(it)) }
    }

    companion object {
        private const val KEY_MULTI_REPOS = "multi_repos"
        private const val MAX_IMPORT_BYTES = 1024 * 1024
        private const val MAX_IMPORTED_REPOSITORIES = 500
        private const val MAX_BACKUP_URLS_PER_REPOSITORY = 20
        private const val MAX_IMPORT_LINE_LENGTH = 8 * 1024
        private const val MAX_AGGREGATION_REPOSITORIES = 5
        private const val MAX_AGGREGATION_ADDRESSES_PER_REPOSITORY = 3
        private const val AGGREGATION_REQUEST_TIMEOUT_MS = 8_000L
        private const val MAX_REPOSITORY_RESPONSE_BYTES = 2 * 1024 * 1024

        private fun classifyAggregationFailure(code: String): String = when {
            code.contains("TOO_LARGE", ignoreCase = true) -> "响应超过大小限制（$code）"
            code.startsWith("HTTP_") || code.contains("NETWORK", ignoreCase = true) || code.contains("TIMEOUT", ignoreCase = true) -> "网络/HTTP 失败（$code）"
            code.contains("JSON", ignoreCase = true) || code.contains("CONFIG", ignoreCase = true) || code.contains("SECTION", ignoreCase = true) -> "JSON 配置格式不兼容（$code）"
            else -> "地址不可用或请求失败（$code）"
        }
    }
}

data class RepositoryEntry(
    val id: String,
    val name: String,
    val primaryUrl: String,
    val backupUrls: List<String>,
    val enabled: Boolean,
    val status: String,
    val usedUrl: String,
    val siteCount: Int,
    val liveCount: Int,
    val parseCount: Int,
    val lastSyncAt: Long,
    val errorMessage: String,
    private val rawJson: String,
) {
    fun toJson(): JSONObject = JSONObject(rawJson)
        .put("id", id).put("name", name).put("primaryUrl", primaryUrl)
        .put("backupUrls", JSONArray(backupUrls)).put("enabled", enabled).put("status", status)

    companion object {
        fun fromJson(json: JSONObject) = RepositoryEntry(
            id = json.optString("id").ifBlank { "repo_${UUID.randomUUID()}" },
            name = json.optString("name", "未命名仓库").ifBlank { "未命名仓库" },
            primaryUrl = json.optString("primaryUrl"),
            backupUrls = json.optJSONArray("backupUrls")?.let { array -> buildList { for (i in 0 until array.length()) array.optString(i).takeIf(String::isNotBlank)?.let(::add) } }.orEmpty(),
            enabled = json.optBoolean("enabled", true),
            status = json.optString("status", "idle"),
            usedUrl = json.optString("usedUrl"),
            siteCount = json.optInt("siteCount", 0),
            liveCount = json.optInt("liveCount", 0),
            parseCount = json.optInt("parseCount", 0),
            lastSyncAt = json.optLong("lastSyncAt", 0L),
            errorMessage = json.optString("errorMessage"),
            rawJson = json.toString(),
        )
    }
}

data class RepositorySyncPreview(val usedUrl: String, val usedBackupIndex: Int, val siteCount: Int, val liveCount: Int, val parseCount: Int)

data class RepositoryFetchOutcome(val name: String, val success: Boolean, val usedUrl: String, val detail: String)

data class RepositoryAggregationUiSummary(
    val successfulRepositories: Int,
    val failedRepositories: List<String>,
    val outcomes: List<RepositoryFetchOutcome>,
    val sitesRaw: Int,
    val sitesUnique: Int,
    val livesRaw: Int,
    val livesUnique: Int,
    val parsesRaw: Int,
    val parsesUnique: Int,
    val potentialConflictGroups: Int,
    val unsupportedEntries: Int,
    val safeDuplicateGroups: Int,
    val reviewMetadataTruncated: Boolean,
    val exportJson: String,
)

data class RepositoryManagementUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val repositories: List<RepositoryEntry> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val syncingRepositoryId: String? = null,
    val aggregationRunning: Boolean = false,
    val aggregation: RepositoryAggregationUiSummary? = null,
)

private fun RepositoryAggregationSummary.toUiSummary(outcomes: List<RepositoryFetchOutcome>) = RepositoryAggregationUiSummary(
    successfulRepositories = successfulRepositories,
    failedRepositories = failedRepositories,
    outcomes = outcomes,
    sitesRaw = sites.rawCount,
    sitesUnique = sites.uniqueCount,
    livesRaw = lives.rawCount,
    livesUnique = lives.uniqueCount,
    parsesRaw = parses.rawCount,
    parsesUnique = parses.uniqueCount,
    potentialConflictGroups = potentialConflictGroups,
    unsupportedEntries = unsupportedEntries,
    safeDuplicateGroups = safeDuplicateGroups,
    reviewMetadataTruncated = reviewMetadataTruncated,
    exportJson = exportJson,
)
