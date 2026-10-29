package com.yuivo9999.mybox123.ui.screens

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.domain.source.SourceHealthCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Native UI adapter for the Stage 2 source-management service; it never blocks the UI thread. */
class SourceManagementViewModel(
    private val application: MyBoxApplication,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SourceManagementUiState())
    val uiState: StateFlow<SourceManagementUiState> = _uiState.asStateFlow()

    private var loadGeneration = 0L

    fun load(force: Boolean = false) {
        if (_uiState.value.loading || (!force && _uiState.value.loaded)) return
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val services = application.awaitServices()
                val sources = services.sourceManagementService.getSources()
                val settings = withContext(Dispatchers.IO) { services.userDataRepository.getSettings() }
                if (generation == loadGeneration) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loaded = true,
                            sources = sources,
                            defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                            defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                            error = null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(loading = false, loaded = false, error = error.message ?: "NATIVE_SERVICES_UNAVAILABLE") }
                }
            }
        }
    }

    fun setEnabled(source: NativeContentSource, enabled: Boolean) {
        if (_uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = source.sourceId, operationMessage = null, error = null) }
            try {
                val service = application.awaitServices().sourceManagementService
                val updated = service.setEnabled(source.sourceId, enabled)
                if (!updated) throw IllegalStateException("SOURCE_NOT_FOUND")
                val sources = service.getSources()
                val settings = withContext(Dispatchers.IO) { application.awaitServices().userDataRepository.getSettings() }
                _uiState.update {
                    it.copy(
                        sources = sources,
                        defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                        defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                        mutatingSourceId = null,
                        operationMessage = if (enabled) "已启用：${source.name}" else "已停用：${source.name}",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingSourceId = null, error = error.message ?: "SOURCE_UPDATE_FAILED") }
                load(force = true)
            }
        }
    }


    fun exportSources(contentResolver: ContentResolver, uri: Uri) {
        if (_uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, operationMessage = null, error = null) }
            try {
                val payload = application.awaitServices().sourceManagementService.exportSourcesJson()
                withContext(Dispatchers.IO) {
                    val output = contentResolver.openOutputStream(uri, "w") ?: throw IOException("SOURCE_EXPORT_DESTINATION_UNAVAILABLE")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                }
                _uiState.update { it.copy(exporting = false, operationMessage = "已导出 ${_uiState.value.sources.size} 个内容源配置。") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(exporting = false, error = error.message ?: "SOURCE_EXPORT_FAILED") }
            }
        }
    }

    /** Imports one user-selected document through the same bounded parser/merge path as pasted text. */
    fun importDocument(contentResolver: ContentResolver, uri: Uri) {
        if (_uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = "__file_import__", operationMessage = null, error = null) }
            try {
                val (fileName, text) = withContext(Dispatchers.IO) { readSourceDocument(contentResolver, uri) }
                val service = application.awaitServices().sourceManagementService
                val mergedSources = service.importText(text, fileName)
                _uiState.update {
                    it.copy(
                        sources = mergedSources,
                        healthResults = emptyMap(),
                        mutatingSourceId = null,
                        operationMessage = "文件“$fileName”已解析并按现有合并规则处理；当前共有 ${mergedSources.size} 个内容源。重复身份源按合并规则更新，已有用户状态会尽量保留。",
                        error = null,
                    )
                }
                load(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val errorCode = when {
                    error is IOException -> "SOURCE_FILE_UNREADABLE"
                    else -> error.message ?: "SOURCE_FILE_IMPORT_FAILED"
                }
                // A registry-sync failure can happen after persistence. Try a direct repository
                // refresh, but retain the original error so the UI never reports false success.
                val refreshedSources = runCatching {
                    application.awaitServices().sourceManagementService.getSources()
                }.getOrNull()
                _uiState.update {
                    it.copy(
                        mutatingSourceId = null,
                        sources = refreshedSources ?: it.sources,
                        healthResults = if (refreshedSources != null) emptyMap() else it.healthResults,
                        error = errorCode,
                    )
                }
            }
        }
    }

    fun reportFileImportCancelled() {
        if (_uiState.value.mutatingSourceId == null) {
            _uiState.update { it.copy(operationMessage = "已取消文件选择，未导入内容源。", error = null) }
        }
    }

    private fun readSourceDocument(contentResolver: ContentResolver, uri: Uri): Pair<String, String> {
        val fileName = (runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 } ?: return@use null)
                else null
            }
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "source.json").take(120)

        val input = contentResolver.openInputStream(uri) ?: throw IOException("SOURCE_FILE_UNREADABLE")
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_SOURCE_FILE_BYTES) throw IllegalArgumentException("SOURCE_FILE_TOO_LARGE")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        if (bytes.isEmpty()) throw IllegalArgumentException("SOURCE_IMPORT_EMPTY")
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw IllegalArgumentException("SOURCE_FILE_UTF8_INVALID")
        }
        return fileName to text
    }

    fun restoreDefaultSources() {
        if (_uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = "__restore_defaults__", operationMessage = null, error = null) }
            try {
                val service = application.awaitServices().sourceManagementService
                val result = service.restoreDefaultSources()
                val sources = service.getSources()
                val settings = withContext(Dispatchers.IO) { application.awaitServices().userDataRepository.getSettings() }
                _uiState.update {
                    it.copy(
                        sources = sources,
                        defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                        defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                        mutatingSourceId = null,
                        operationMessage = if (result.added == 0) "项目内置源均已存在，没有覆盖现有配置。" else "已补回 ${result.added} 个缺失的内置源；现有源和用户自定义源均已保留。",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingSourceId = null, error = error.message ?: "SOURCE_RESTORE_DEFAULTS_FAILED") }
            }
        }
    }

    fun importText(text: String, fileName: String = "source.txt") {
        if (_uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = "__import__", operationMessage = null, error = null) }
            try {
                val service = application.awaitServices().sourceManagementService
                service.importText(text, fileName)
                val sources = service.getSources()
                _uiState.update {
                    it.copy(
                        sources = sources,
                        mutatingSourceId = null,
                        operationMessage = "导入处理完成；合并后的源列表共 ${sources.size} 个源。",
                    )
                }
                load(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingSourceId = null, error = error.message ?: "SOURCE_IMPORT_FAILED") }
            }
        }
    }

    fun saveSource(source: NativeContentSource?, name: String, sourceType: String, sourceRef: String) {
        if (_uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null) return
        val normalizedName = name.trim()
        val normalizedRef = sourceRef.trim()
        if (normalizedName.isEmpty() || normalizedRef.isEmpty()) {
            _uiState.update { it.copy(error = "SOURCE_NAME_AND_URL_REQUIRED") }
            return
        }
        val sourceId = source?.sourceId ?: "__new_source__"
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = sourceId, operationMessage = null, error = null) }
            try {
                val service = application.awaitServices().sourceManagementService
                val json = (source?.toJson() ?: JSONObject()).apply {
                    put("name", normalizedName)
                    put("sourceType", if (sourceType == "live") "live" else "movie")
                    put("sourceRef", normalizedRef)
                    put("url", normalizedRef)
                    put("enabled", source?.enabled ?: true)
                    if (source == null) put("sourceId", "local-${java.util.UUID.randomUUID()}")
                }
                val saved = service.saveSource(json)
                val sources = service.getSources()
                val settings = withContext(Dispatchers.IO) { application.awaitServices().userDataRepository.getSettings() }
                _uiState.update {
                    it.copy(
                        sources = sources,
                        healthResults = if (source == null) it.healthResults else it.healthResults - saved.sourceId,
                        defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                        defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                        mutatingSourceId = null,
                        operationMessage = "已保存内容源：${saved.name}",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingSourceId = null, error = error.message ?: "SOURCE_SAVE_FAILED") }
            }
        }
    }

    fun removeSource(source: NativeContentSource) {
        if (_uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = source.sourceId, operationMessage = null, error = null) }
            try {
                val services = application.awaitServices()
                if (!services.sourceManagementService.removeSource(source.sourceId)) throw IllegalStateException("SOURCE_NOT_FOUND")
                val sources = services.sourceManagementService.getSources()
                val settings = withContext(Dispatchers.IO) { services.userDataRepository.getSettings() }
                _uiState.update {
                    it.copy(
                        sources = sources,
                        healthResults = it.healthResults - source.sourceId,
                        defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                        defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                        mutatingSourceId = null,
                        operationMessage = "已删除内容源：${source.name}",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingSourceId = null, error = error.message ?: "SOURCE_REMOVE_FAILED") }
            }
        }
    }

    /** Deletes selected source definitions sequentially; every target is reconciled against the final repository state. */
    fun removeSources(sourceIds: List<String>) {
        val current = _uiState.value
        val targets = sourceIds.distinct().mapNotNull { id -> current.sources.firstOrNull { it.sourceId == id } }
        if (targets.isEmpty() || current.mutatingSourceId != null || current.testingSourceId != null || current.exporting || current.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = "__bulk_delete__", operationMessage = null, error = null) }
            val removalErrors = linkedMapOf<String, String>()
            try {
                val service = application.awaitServices().sourceManagementService
                targets.forEach { source ->
                    try {
                        if (!service.removeSource(source.sourceId)) removalErrors[source.sourceId] = "SOURCE_NOT_FOUND"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        removalErrors[source.sourceId] = error.message ?: "SOURCE_REMOVE_FAILED"
                    }
                }

                // A registry-sync exception may happen after the source row was removed. Read the
                // reconciled repository before deciding whether each item truly remains.
                val services = application.awaitServices()
                val remainingSources = try {
                    services.sourceManagementService.getSources()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(
                            mutatingSourceId = null,
                            error = error.message ?: "SOURCE_REGISTRY_SYNC_REQUIRED",
                            operationMessage = buildBatchRemovalMessage(targets, emptySet(), removalErrors, unresolved = true),
                        )
                    }
                    load(force = true)
                    return@launch
                }
                val remainingIds = remainingSources.map { it.sourceId }.toSet()
                val deletedIds = targets.map { it.sourceId }.filterNot { it in remainingIds }.toSet()
                val failedSources = targets.filter { it.sourceId in remainingIds }
                val settings = withContext(Dispatchers.IO) { services.userDataRepository.getSettings() }
                _uiState.update {
                    it.copy(
                        sources = remainingSources,
                        healthResults = it.healthResults - targets.map { target -> target.sourceId }.toSet(),
                        defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                        defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                        mutatingSourceId = null,
                        operationMessage = buildBatchRemovalMessage(targets, deletedIds, removalErrors, failedSources.map { source -> source.sourceId }.toSet()),
                        error = if (failedSources.isEmpty()) null else "SOURCE_BULK_DELETE_PARTIAL_FAILURE",
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(mutatingSourceId = null) }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        mutatingSourceId = null,
                        error = error.message ?: "SOURCE_BULK_DELETE_FAILED",
                        operationMessage = buildBatchRemovalMessage(targets, emptySet(), removalErrors, unresolved = true),
                    )
                }
                load(force = true)
            }
        }
    }

    private fun buildBatchRemovalMessage(
        targets: List<NativeContentSource>,
        deletedIds: Set<String>,
        errors: Map<String, String>,
        failedIds: Set<String> = emptySet(),
        unresolved: Boolean = false,
    ): String {
        val results = targets.map { source ->
            when {
                source.sourceId in deletedIds -> "成功：${source.name}"
                source.sourceId in failedIds -> "失败：${source.name}（${errors[source.sourceId] ?: "SOURCE_REMOVE_FAILED"}）"
                unresolved -> "待核验：${source.name}（${errors[source.sourceId] ?: "SOURCE_RESULT_UNAVAILABLE"}）"
                else -> "失败：${source.name}（${errors[source.sourceId] ?: "SOURCE_REMOVE_FAILED"}）"
            }
        }
        val successCount = deletedIds.size
        val failureCount = failedIds.size.takeIf { !unresolved } ?: errors.keys.count { it !in deletedIds }
        val heading = if (unresolved) "批量删除已发起，但最终状态未能全部核验" else "批量删除处理完成：成功 $successCount 个，失败 $failureCount 个"
        return (listOf(heading) + results).joinToString("\n")
    }

    /** Applies a bounded bulk enable/disable operation; individual failures are reported, never hidden. */
    fun setSourcesEnabled(sourceIds: List<String>, enabled: Boolean) {
        val targets = sourceIds.distinct().filter(String::isNotBlank)
        if (targets.isEmpty() || _uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = "__bulk__", operationMessage = null, error = null) }
            var succeeded = 0
            var failed = 0
            try {
                val service = application.awaitServices().sourceManagementService
                targets.forEach { sourceId ->
                    try {
                        if (service.setEnabled(sourceId, enabled)) succeeded++ else failed++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed++
                    }
                }
                val services = application.awaitServices()
                val sources = services.sourceManagementService.getSources()
                val settings = withContext(Dispatchers.IO) { services.userDataRepository.getSettings() }
                _uiState.update {
                    it.copy(
                        sources = sources,
                        defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                        defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                        mutatingSourceId = null,
                        operationMessage = "批量${if (enabled) "启用" else "停用"}完成：成功 $succeeded 个，失败 $failed 个。${if (failed > 0) "请检查失败项并重试。" else ""}",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingSourceId = null, error = error.message ?: "SOURCE_BULK_UPDATE_FAILED") }
                load(force = true)
            }
        }
    }

    fun setDefaultSource(source: NativeContentSource) {
        if (!source.enabled || _uiState.value.mutatingSourceId != null || _uiState.value.testingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingSourceId = source.sourceId, operationMessage = null, error = null) }
            try {
                val services = application.awaitServices()
                val settings = withContext(Dispatchers.IO) { services.userDataRepository.getSettings() }
                val key = if (source.sourceType == "live") "defaultLiveSource" else "defaultMovieSource"
                settings.put(key, source.sourceId)
                withContext(Dispatchers.IO) { services.userDataRepository.saveSettings(settings) }
                _uiState.update {
                    it.copy(
                        defaultMovieSourceId = settings.nonBlankSetting("defaultMovieSource"),
                        defaultLiveSourceId = settings.nonBlankSetting("defaultLiveSource"),
                        mutatingSourceId = null,
                        operationMessage = "已将“${source.name}”设为${if (source.sourceType == "live") "默认直播源" else "默认影视源"}。",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingSourceId = null, error = error.message ?: "SOURCE_DEFAULT_FAILED") }
            }
        }
    }

    /** Tests at most ten selected sources sequentially, preserving a per-source result even on failure. */
    fun testSources(sourceIds: List<String>) {
        val availableIds = _uiState.value.sources.map { it.sourceId }.toSet()
        val targets = sourceIds.distinct().filter { it.isNotBlank() && it in availableIds }.take(MAX_BATCH_SOURCE_TESTS)
        if (targets.isEmpty() || _uiState.value.testingSourceId != null || _uiState.value.mutatingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    testingSourceId = "__batch__",
                    batchCurrentSourceId = null,
                    batchTestCompleted = 0,
                    batchTestTotal = targets.size,
                    operationMessage = null,
                    error = null,
                    healthResults = it.healthResults - targets.toSet(),
                )
            }
            var succeeded = 0
            var failed = 0
            val service = try {
                application.awaitServices().sourceManagementService
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(testingSourceId = null, batchCurrentSourceId = null, batchTestTotal = 0) }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        testingSourceId = null,
                        batchCurrentSourceId = null,
                        batchTestTotal = 0,
                        error = error.message ?: "NATIVE_SERVICES_UNAVAILABLE",
                    )
                }
                return@launch
            }
            targets.forEachIndexed { index, sourceId ->
                _uiState.update { it.copy(batchCurrentSourceId = sourceId, batchTestCompleted = index) }
                val result = try {
                    service.testSource(sourceId)
                } catch (cancelled: CancellationException) {
                    _uiState.update { it.copy(testingSourceId = null, batchCurrentSourceId = null, batchTestTotal = 0) }
                    throw cancelled
                } catch (error: Exception) {
                    SourceHealthCheckResult(
                        sourceId = sourceId,
                        ok = false,
                        httpStatus = null,
                        errorCode = error.message ?: "SOURCE_TEST_FAILED",
                        durationMs = 0L,
                    )
                }
                if (result.ok) succeeded++ else failed++
                _uiState.update {
                    it.copy(
                        healthResults = it.healthResults + (sourceId to result),
                        batchTestCompleted = index + 1,
                    )
                }
            }
            _uiState.update {
                it.copy(
                    testingSourceId = null,
                    batchCurrentSourceId = null,
                    batchTestTotal = 0,
                    operationMessage = "批量连通性检测完成：成功 $succeeded 个，失败 $failed 个。结果已逐项显示；失败仅表示本次有限 HTTP 探测未通过，不代表源的所有功能均不可用。",
                )
            }
        }
    }

    fun testSource(source: NativeContentSource) {
        if (_uiState.value.testingSourceId != null || _uiState.value.mutatingSourceId != null || _uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(testingSourceId = source.sourceId, operationMessage = null, error = null) }
            try {
                val result: SourceHealthCheckResult = application.awaitServices().sourceManagementService.testSource(source.sourceId)
                val message = if (result.ok) {
                    "${source.name}：连接成功${result.httpStatus?.let { "（HTTP $it）" } ?: ""}，${result.durationMs} ms"
                } else {
                    "${source.name}：检测失败（${result.errorCode ?: "SOURCE_TEST_FAILED"}）"
                }
                _uiState.update {
                    it.copy(testingSourceId = null, operationMessage = message, healthResults = it.healthResults + (source.sourceId to result))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val result = SourceHealthCheckResult(source.sourceId, false, null, error.message ?: "SOURCE_TEST_FAILED", 0L)
                _uiState.update { it.copy(testingSourceId = null, error = error.message ?: "SOURCE_TEST_FAILED", healthResults = it.healthResults + (source.sourceId to result)) }
            }
        }
    }
}

private const val MAX_BATCH_SOURCE_TESTS = 10
private const val MAX_SOURCE_FILE_BYTES = 5 * 1024 * 1024

data class SourceManagementUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val sources: List<NativeContentSource> = emptyList(),
    val error: String? = null,
    val operationMessage: String? = null,
    val mutatingSourceId: String? = null,
    val testingSourceId: String? = null,
    val batchCurrentSourceId: String? = null,
    val batchTestCompleted: Int = 0,
    val batchTestTotal: Int = 0,
    val healthResults: Map<String, SourceHealthCheckResult> = emptyMap(),
    val exporting: Boolean = false,
    val defaultMovieSourceId: String? = null,
    val defaultLiveSourceId: String? = null,
)


private fun JSONObject.nonBlankSetting(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
