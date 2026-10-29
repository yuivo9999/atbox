package com.yuivo9999.mybox123.ui.screens

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.LegacyMigrationResult
import com.yuivo9999.mybox123.data.storage.NativeUserDataRepository
import com.yuivo9999.mybox123.data.storage.RecoverableImportOperation
import com.yuivo9999.mybox123.data.storage.NativeDataImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

private const val MAX_LEGACY_BACKUP_EXPORT_BYTES = 10 * 1024 * 1024
private const val MAX_LEGACY_BACKUP_KEY_CHARS = 512
private const val MAX_LEGACY_BACKUP_JSON_DEPTH = 4

/** Bounded native data export/record cleanup and legacy SharedPreferences migration status. */
class DataManagementViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(DataManagementUiState())
    private val mutationGate = Any()
    private var mutationInFlight = false
    private var reloadAfterLoad = false

    /** Serialize import apply, rollback, and record cleanup across their full database operation. */
    private fun tryAcquireMutation(): Boolean = synchronized(mutationGate) {
        if (mutationInFlight) return@synchronized false
        mutationInFlight = true
        true
    }

    private fun releaseMutation() {
        synchronized(mutationGate) { mutationInFlight = false }
    }

    private fun isMutationInFlight(): Boolean = synchronized(mutationGate) { mutationInFlight }
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    fun load(force: Boolean = false) {
        if (_uiState.value.loading) {
            if (force) reloadAfterLoad = true
            return
        }
        if (_uiState.value.loaded && !force) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    val services = application.awaitServices()
                    val repository = services.userDataRepository
                    val all = repository.exportAllRaw()
                    val counts = DataRecordCounts(
                        favorites = arrayCount(all[NativeUserDataRepository.KEY_FAVORITES]),
                        history = arrayCount(all[NativeUserDataRepository.KEY_HISTORY]),
                        progress = arrayCount(all[NativeUserDataRepository.KEY_PROGRESS]),
                        searches = arrayCount(all[NativeUserDataRepository.KEY_SEARCHES]),
                        sources = arrayCount(all[NativeUserDataRepository.KEY_SOURCES]),
                        repositories = arrayCount(all["multi_repos"]),
                    )
                    val cache = services.liveCacheStore.summary()
                    val cacheCounts = LiveCacheCounts(
                        sources = cache.optInt("sourceCount", 0),
                        channels = cache.optJSONArray("sources").sumOfObjects("channelEntries"),
                        streams = cache.optJSONArray("sources").sumOfObjects("streamEntries"),
                        epg = cache.optJSONArray("sources").sumOfObjects("epgEntries"),
                    )
                    DataManagementLoadResult(counts, services.legacyMigrationResult, repository.getRecoverableImportHistory(), cacheCounts)
                }
                _uiState.update { it.copy(loading = false, loaded = true, counts = result.counts, migration = result.migration, recoveryOperations = result.recoveryOperations, liveCacheCounts = result.liveCacheCounts, error = null) }
                runQueuedReloadIfNeeded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.message ?: "DATA_MANAGEMENT_LOAD_FAILED") }
                runQueuedReloadIfNeeded()
            }
        }
    }

    private fun runQueuedReloadIfNeeded() {
        if (!reloadAfterLoad) return
        reloadAfterLoad = false
        load(force = true)
    }

    fun prepareExport(onReady: (String) -> Unit) {
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, error = null, message = null) }
            try {
                val payload = withContext(Dispatchers.IO) {
                    val services = application.awaitServices()
                    val values = services.userDataRepository.exportAllRaw()
                    val data = JSONObject()
                    values.forEach { (key, raw) ->
                        val value: Any = try {
                            val tokener = JSONTokener(raw)
                            val parsed = tokener.nextValue()
                            if (tokener.nextClean() == '\u0000') parsed else raw
                        } catch (_: JSONException) {
                            raw
                        }
                        data.put(key, value)
                    }
                    JSONObject()
                        .put("format", "mybox-native-user-data")
                        .put("formatVersion", 1)
                        .put("exportedAt", System.currentTimeMillis())
                        .put("scope", "active_native_user_data_only; legacy_backup_table_not_included")
                        .put("dataSha256", NativeDataImportPreview.canonicalDataSha256(data))
                        .put("data", data)
                        .toString(2)
                }
                _uiState.update { it.copy(exporting = false, message = "导出内容已准备，请选择保存位置。") }
                onReady(payload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(exporting = false, error = error.message ?: "DATA_EXPORT_PREPARE_FAILED") }
            }
        }
    }

    fun prepareLegacyBackupExport(onReady: (String) -> Unit) {
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, error = null, message = null) }
            try {
                val payload = withContext(Dispatchers.IO) {
                    val backups = application.awaitServices().userDataRepository.exportLegacyMigrationBackupsRaw()
                    val data = JSONObject()
                    // Keep every backed-up preference value as its original raw string; do not
                    // parse/re-serialize it, because malformed legacy values are still valuable.
                    backups.forEach { (key, raw) -> data.put(key, raw) }
                    val result = JSONObject()
                        .put("format", "mybox-legacy-migration-backup")
                        .put("formatVersion", 1)
                        .put("exportedAt", System.currentTimeMillis())
                        .put("scope", "legacy_backup_table_raw_values_only; export_only_not_accepted_by_standard_import")
                        .put("backupCount", backups.size)
                        .put("dataSha256", legacyBackupEntriesSha256(backups))
                        .put("data", data)
                        .toString(2)
                    require(result.toByteArray(Charsets.UTF_8).size <= MAX_LEGACY_BACKUP_EXPORT_BYTES) {
                        "LEGACY_BACKUP_EXPORT_TOO_LARGE"
                    }
                    result
                }
                _uiState.update { it.copy(exporting = false, message = "旧数据迁移备份已准备，请选择保存位置。") }
                onReady(payload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(exporting = false, error = error.message ?: "LEGACY_BACKUP_EXPORT_PREPARE_FAILED") }
            }
        }
    }

    fun previewLegacyBackupFile(contentResolver: ContentResolver, uri: Uri) {
        if (_uiState.value.previewingImport || _uiState.value.applyingImport || _uiState.value.exporting || _uiState.value.clearing || _uiState.value.rollingBackImport || isMutationInFlight()) return
        viewModelScope.launch {
            _uiState.update { it.copy(previewingImport = true, legacyBackupPreview = null, importPreview = null, importActions = emptyMap(), error = null, message = null) }
            try {
                val preview = withContext(Dispatchers.IO) {
                    val payload = contentResolver.openInputStream(uri)?.use { readUtf8Bounded(it, MAX_LEGACY_BACKUP_EXPORT_BYTES) }
                        ?: throw IOException("LEGACY_BACKUP_FILE_UNAVAILABLE")
                    require(NativeDataImportPreview.maxJsonDepthForStorage(payload) <= MAX_LEGACY_BACKUP_JSON_DEPTH) {
                        "LEGACY_BACKUP_JSON_TOO_DEEP"
                    }
                    val root = try {
                        val tokener = JSONTokener(payload)
                        val parsed = tokener.nextValue()
                        if (tokener.nextClean() != '\u0000' || parsed !is JSONObject) {
                            throw JSONException("Expected one complete JSON object")
                        }
                        parsed
                    } catch (error: JSONException) {
                        throw IllegalArgumentException("LEGACY_BACKUP_JSON_INVALID", error)
                    }
                    val requiredEnvelopeKeys = setOf("format", "formatVersion", "exportedAt", "scope", "backupCount", "data")
                    val checksumEnvelopeKeys = requiredEnvelopeKeys + "dataSha256"
                    val envelopeKeys = root.keys().asSequence().toSet()
                    require(envelopeKeys == requiredEnvelopeKeys || envelopeKeys == checksumEnvelopeKeys) { "LEGACY_BACKUP_ENVELOPE_KEYS_INVALID" }
                    require(root.opt("format") is String && root.optString("format") == "mybox-legacy-migration-backup") { "LEGACY_BACKUP_FORMAT_INVALID" }
                    val versionValue = root.opt("formatVersion")
                    require(versionValue is Number && versionValue.toDouble() == 1.0) { "LEGACY_BACKUP_VERSION_UNSUPPORTED" }
                    require(root.opt("scope") is String && root.optString("scope") == "legacy_backup_table_raw_values_only; export_only_not_accepted_by_standard_import") { "LEGACY_BACKUP_SCOPE_INVALID" }
                    val timestampValue = root.opt("exportedAt")
                    require(timestampValue is Number && timestampValue.toDouble().isFinite() && timestampValue.toDouble() == timestampValue.toLong().toDouble() && timestampValue.toLong() >= 0L) { "LEGACY_BACKUP_TIMESTAMP_INVALID" }
                    val data = root.optJSONObject("data") ?: throw IllegalArgumentException("LEGACY_BACKUP_DATA_INVALID")
                    val keys = data.keys().asSequence().toList().sorted()
                    require(keys.size <= 5000) { "LEGACY_BACKUP_TOO_MANY_KEYS" }
                    var rawValueByteCount = 0L
                    keys.forEach { key ->
                        require(key.isNotBlank() && key.length <= MAX_LEGACY_BACKUP_KEY_CHARS) { "LEGACY_BACKUP_KEY_INVALID" }
                        val value = data.opt(key)
                        require(value is String) { "LEGACY_BACKUP_VALUE_NOT_RAW_STRING:$key" }
                        rawValueByteCount += value.toByteArray(Charsets.UTF_8).size
                        require(rawValueByteCount <= MAX_LEGACY_BACKUP_EXPORT_BYTES.toLong()) { "LEGACY_BACKUP_VALUES_TOO_LARGE" }
                    }
                    val countValue = root.opt("backupCount")
                    require(countValue is Number && countValue.toDouble().isFinite() && countValue.toDouble() == countValue.toInt().toDouble()) { "LEGACY_BACKUP_COUNT_INVALID" }
                    val declaredCount = countValue.toInt()
                    require(declaredCount == keys.size) { "LEGACY_BACKUP_COUNT_MISMATCH" }
                    val entries = keys.associateWith { data.getString(it) }
                    val declaredDigest = root.opt("dataSha256")
                    val integrityVerified = if (declaredDigest == null) {
                        false // Older v1 backups remain inspectable, but their payload integrity is not verifiable.
                    } else {
                        require(declaredDigest is String && declaredDigest.matches(Regex("[0-9a-f]{64}"))) { "LEGACY_BACKUP_CHECKSUM_INVALID" }
                        require(declaredDigest == legacyBackupEntriesSha256(entries)) { "LEGACY_BACKUP_CHECKSUM_MISMATCH" }
                        true
                    }
                    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                    val sha256 = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
                        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                    LegacyBackupFilePreview(
                        exportedAt = timestampValue.toLong(),
                        backupCount = declaredCount,
                        keys = keys,
                        byteCount = payloadBytes.size,
                        rawValueByteCount = rawValueByteCount,
                        sha256 = sha256,
                        integrityVerified = integrityVerified,
                        integrityStatus = if (integrityVerified) "VERIFIED" else "LEGACY_NO_CHECKSUM",
                    )
                }
                _uiState.update {
                    val integrityMessage = if (preview.integrityVerified) {
                        "数据 SHA-256 校验通过。"
                    } else {
                        "这是无数据校验和的旧版备份；结构可读取，但内容完整性无法验证。"
                    }
                    it.copy(previewingImport = false, legacyBackupPreview = preview, message = "旧数据迁移备份结构校验完成。$integrityMessage 此操作只读，尚未写入或恢复任何数据。")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val errorCode = error.message ?: "LEGACY_BACKUP_PREVIEW_FAILED"
                val displayError = when (errorCode) {
                    "LEGACY_BACKUP_CHECKSUM_INVALID", "LEGACY_BACKUP_CHECKSUM_MISMATCH" -> "CHECKSUM_REJECTED:$errorCode"
                    else -> errorCode
                }
                _uiState.update { it.copy(previewingImport = false, legacyBackupPreview = null, error = displayError) }
            }
        }
    }

    fun previewImport(contentResolver: ContentResolver, uri: Uri) {
        if (_uiState.value.previewingImport || _uiState.value.applyingImport || _uiState.value.exporting || _uiState.value.clearing || _uiState.value.rollingBackImport || isMutationInFlight()) return
        viewModelScope.launch {
            _uiState.update { it.copy(previewingImport = true, importPreview = null, legacyBackupPreview = null, error = null, message = null) }
            try {
                val preview = withContext(Dispatchers.IO) {
                    val payload = contentResolver.openInputStream(uri)?.use { readUtf8Bounded(it) }
                        ?: throw IOException("IMPORT_FILE_UNAVAILABLE")
                    val repository = application.awaitServices().userDataRepository
                    NativeDataImportPreview.preview(payload, repository.exportAllRaw())
                }
                _uiState.update {
                    it.copy(
                        previewingImport = false,
                        importPreview = preview,
                        importActions = preview.incomingValuesByKey.keys.associateWith { ImportKeyAction.SKIP },
                        message = "只读校验完成；所有数据键默认跳过，尚未写入或修改任何数据。",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val errorCode = error.message ?: "IMPORT_PREVIEW_FAILED"
                val displayError = when (errorCode) {
                    "IMPORT_CHECKSUM_FORMAT_INVALID", "IMPORT_CHECKSUM_MISMATCH" -> "CHECKSUM_REJECTED:$errorCode"
                    else -> errorCode
                }
                _uiState.update { it.copy(previewingImport = false, importPreview = null, error = displayError) }
            }
        }
    }

    private fun readUtf8Bounded(input: InputStream, maxBytes: Int = NativeDataImportPreview.MAX_FILE_BYTES): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { if (maxBytes == MAX_LEGACY_BACKUP_EXPORT_BYTES) "LEGACY_BACKUP_FILE_TOO_LARGE" else "IMPORT_FILE_TOO_LARGE" }
            output.write(buffer, 0, read)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        } catch (error: CharacterCodingException) {
            throw IOException("IMPORT_UTF8_INVALID", error)
        }
    }

    fun setImportAction(key: String, action: ImportKeyAction) {
        val preview = _uiState.value.importPreview ?: return
        if (key !in preview.incomingValuesByKey) return
        // Keys with unresolved cross-record/reference contracts remain excluded from this first apply path.
        if (!canReplaceImportKey(key) && action != ImportKeyAction.SKIP) return
        if (_uiState.value.applyingImport) return
        _uiState.update { current -> current.copy(importActions = current.importActions + (key to action), error = null, message = null) }
    }

    fun applyImportPlan() {
        val current = _uiState.value
        val preview = current.importPreview ?: return
        if (current.applyingImport || current.rollingBackImport || current.previewingImport || current.exporting || current.clearing) return
        if (!tryAcquireMutation()) {
            _uiState.update { it.copy(error = "DATA_MUTATION_IN_PROGRESS") }
            return
        }
        val selectedKeys = preview.incomingValuesByKey.keys.filter { current.importActions[it] == ImportKeyAction.REPLACE_KEY && canReplaceImportKey(it) }
        if (selectedKeys.isEmpty()) {
            releaseMutation()
            _uiState.update { it.copy(error = "IMPORT_NO_KEYS_SELECTED") }
            return
        }
        val values = selectedKeys.associateWith { preview.incomingValuesByKey.getValue(it) }
        val expected = selectedKeys.associateWith { preview.currentRawSnapshot[it] }
        viewModelScope.launch {
            _uiState.update { it.copy(applyingImport = true, error = null, message = null) }
            try {
                val operationId = withContext(Dispatchers.IO) {
                    application.awaitServices().userDataRepository.applyRecoverableImport(
                        values = values,
                        expectedCurrentRaw = expected,
                        formatVersion = preview.formatVersion,
                    )
                }
                _uiState.update {
                    it.copy(
                        applyingImport = false,
                        importPreview = null,
                        importActions = emptyMap(),
                        message = "已应用 ${selectedKeys.size} 个数据键。恢复操作编号：$operationId。回滚快照已保存，可在下方恢复操作历史中查看并撤销。",
                    )
                }
                load(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        applyingImport = false,
                        error = error.message ?: "IMPORT_APPLY_FAILED",
                        message = "未确认应用成功。若数据在预览后发生变化，系统会拒绝覆盖；请刷新并重新预览。",
                    )
                }
            } finally {
                _uiState.update { it.copy(applyingImport = false) }
                releaseMutation()
            }
        }
    }

    fun rollbackImport(operationId: String) {
        if (operationId.isBlank() || operationId.length > 64) return
        if (_uiState.value.rollingBackImport || _uiState.value.applyingImport || _uiState.value.clearing) return
        if (!tryAcquireMutation()) {
            _uiState.update { it.copy(error = "DATA_MUTATION_IN_PROGRESS") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(rollingBackImport = true, error = null, message = null) }
            try {
                val rolledBack = withContext(Dispatchers.IO) {
                    application.awaitServices().userDataRepository.rollbackRecoverableImport(operationId)
                }
                _uiState.update {
                    it.copy(
                        rollingBackImport = false,
                        message = if (rolledBack) "已撤销该导入操作并恢复原数据。" else "该恢复操作已被撤销，无需重复操作。",
                    )
                }
                load(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(rollingBackImport = false, error = error.message ?: "IMPORT_ROLLBACK_FAILED", message = "撤销失败。若数据已被后续操作修改，系统会拒绝覆盖；请保留错误代码并重新检查数据。") }
            } finally {
                _uiState.update { it.copy(rollingBackImport = false) }
                releaseMutation()
            }
        }
    }

    fun writeExport(contentResolver: ContentResolver, uri: Uri, payload: String, label: String = "用户数据") {
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, error = null, message = null) }
            try {
                withContext(Dispatchers.IO) {
                    val output = contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("EXPORT_DESTINATION_UNAVAILABLE")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                }
                _uiState.update { it.copy(exporting = false, message = "${label}已导出。") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(exporting = false, error = error.message ?: "DATA_EXPORT_WRITE_FAILED") }
            }
        }
    }

    fun clearHistoryAndProgress() = clearRecords("history_progress")
    fun clearUserRecords() = clearRecords("all_records")

    fun clearLiveCache() {
        if (_uiState.value.clearing || _uiState.value.exporting || _uiState.value.previewingImport || _uiState.value.applyingImport || _uiState.value.rollingBackImport) return
        if (!tryAcquireMutation()) {
            _uiState.update { it.copy(error = "DATA_MUTATION_IN_PROGRESS") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(clearing = true, error = null, message = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    val cacheStore = application.awaitServices().liveCacheStore
                    val removed = cacheStore.clearAllEntries()
                    val summary = cacheStore.summary()
                    LiveCacheCounts(
                        sources = summary.optInt("sourceCount", 0),
                        channels = summary.optJSONArray("sources").sumOfObjects("channelEntries"),
                        streams = summary.optJSONArray("sources").sumOfObjects("streamEntries"),
                        epg = summary.optJSONArray("sources").sumOfObjects("epgEntries"),
                    ) to removed
                }
                _uiState.update { it.copy(clearing = false, liveCacheCounts = result.first, message = "已清理直播缓存条目 ${result.second} 项；收藏、历史、设置、内容源及多仓配置未修改。") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(clearing = false, error = error.message ?: "LIVE_CACHE_CLEAR_FAILED") }
            } finally {
                _uiState.update { it.copy(clearing = false) }
                releaseMutation()
            }
        }
    }

    private fun clearRecords(scope: String) {
        if (_uiState.value.clearing || _uiState.value.applyingImport || _uiState.value.rollingBackImport) return
        if (!tryAcquireMutation()) {
            _uiState.update { it.copy(error = "DATA_MUTATION_IN_PROGRESS") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(clearing = true, error = null, message = null) }
            try {
                val counts = withContext(Dispatchers.IO) {
                    val repository = application.awaitServices().userDataRepository
                    if (scope == "history_progress") repository.clearHistoryAndProgress() else repository.clearUserRecords()
                    val all = repository.exportAllRaw()
                    DataRecordCounts(
                        favorites = arrayCount(all[NativeUserDataRepository.KEY_FAVORITES]),
                        history = arrayCount(all[NativeUserDataRepository.KEY_HISTORY]),
                        progress = arrayCount(all[NativeUserDataRepository.KEY_PROGRESS]),
                        searches = arrayCount(all[NativeUserDataRepository.KEY_SEARCHES]),
                        sources = arrayCount(all[NativeUserDataRepository.KEY_SOURCES]),
                        repositories = arrayCount(all["multi_repos"]),
                    )
                }
                _uiState.update {
                    it.copy(
                        clearing = false,
                        counts = counts,
                        message = if (scope == "history_progress") "播放历史与进度已清除；其他记录未修改。" else "收藏、播放历史、进度和搜索历史已清除；设置、内容源、多仓及迁移备份已保留。",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(clearing = false, error = error.message ?: "DATA_CLEAR_FAILED") }
            } finally {
                _uiState.update { it.copy(clearing = false) }
                releaseMutation()
            }
        }
    }

    private fun arrayCount(raw: String?): Int? = try {
        if (raw == null) 0 else JSONArray(raw).length()
    } catch (_: JSONException) {
        null
    }
}

private data class DataManagementLoadResult(
    val counts: DataRecordCounts,
    val migration: LegacyMigrationResult,
    val recoveryOperations: List<RecoverableImportOperation>,
    val liveCacheCounts: LiveCacheCounts,
)

data class LiveCacheCounts(
    val sources: Int,
    val channels: Int,
    val streams: Int,
    val epg: Int,
) {
    val totalEntries: Int get() = channels + streams + epg
}

private fun JSONArray?.sumOfObjects(key: String): Int {
    if (this == null) return 0
    var total = 0
    for (index in 0 until length()) total += optJSONObject(index)?.optInt(key, 0) ?: 0
    return total
}

data class DataRecordCounts(
    val favorites: Int?,
    val history: Int?,
    val progress: Int?,
    val searches: Int?,
    val sources: Int?,
    val repositories: Int?,
)

data class DataManagementUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val exporting: Boolean = false,
    val clearing: Boolean = false,
    val previewingImport: Boolean = false,
    val applyingImport: Boolean = false,
    val rollingBackImport: Boolean = false,
    val recoveryOperations: List<RecoverableImportOperation> = emptyList(),
    val importPreview: NativeDataImportPreview.Result? = null,
    val legacyBackupPreview: LegacyBackupFilePreview? = null,
    val importActions: Map<String, ImportKeyAction> = emptyMap(),
    val counts: DataRecordCounts? = null,
    val liveCacheCounts: LiveCacheCounts? = null,
    val migration: LegacyMigrationResult? = null,
    val error: String? = null,
    val message: String? = null,
)

data class LegacyBackupFilePreview(
    val exportedAt: Long,
    val backupCount: Int,
    val keys: List<String>,
    val byteCount: Int,
    val rawValueByteCount: Long,
    val sha256: String,
    val integrityVerified: Boolean,
    val integrityStatus: String,
)

/** Stable digest over sorted raw key/value pairs; it does not depend on JSON object key order. */
private fun legacyBackupEntriesSha256(entries: Map<String, String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.toSortedMap().forEach { (key, value) ->
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(keyBytes.size).array())
        digest.update(keyBytes)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(valueBytes.size).array())
        digest.update(valueBytes)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

/** Import actions are intentionally key-scoped; there is no implicit merge or global replace. */
enum class ImportKeyAction { SKIP, REPLACE_KEY }

/** Only independent user-record arrays are enabled until cross-key reference reconciliation is implemented. */
fun canReplaceImportKey(key: String): Boolean = key in setOf(
    NativeUserDataRepository.KEY_FAVORITES,
    NativeUserDataRepository.KEY_HISTORY,
    NativeUserDataRepository.KEY_PROGRESS,
    NativeUserDataRepository.KEY_SEARCHES,
)
