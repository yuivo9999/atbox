package com.yuivo9999.mybox123.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.domain.source.SourceHealthCheckResult
import com.yuivo9999.mybox123.ui.theme.AppAppearanceController
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeSourceManagementScreen(modifier: Modifier = Modifier) {
    val appearance by AppAppearanceController.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) { sourceManagementViewModelFactory(application) }
    val sourceViewModel: SourceManagementViewModel = viewModel(factory = factory)
    val state by sourceViewModel.uiState.collectAsStateWithLifecycle()
    var editingSource by remember { mutableStateOf<NativeContentSource?>(null) }
    var showNewSource by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var deletingSource by remember { mutableStateOf<NativeContentSource?>(null) }
    var selectedSourceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingBulkAction by remember { mutableStateOf<Boolean?>(null) }
    var confirmBatchHealthCheck by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var confirmRestoreDefaults by remember { mutableStateOf(false) }
    val createSourceDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) sourceViewModel.exportSources(context.contentResolver, uri)
    }
    val openSourceDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sourceViewModel.importDocument(context.contentResolver, uri)
        else sourceViewModel.reportFileImportCancelled()
    }

    LaunchedEffect(sourceViewModel) { sourceViewModel.load() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("内容源管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${state.sources.size} 个源 · 原生服务", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
            }
            OutlinedButton(onClick = { sourceViewModel.load(force = true) }, enabled = !state.loading && state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting) { Text("刷新") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { showNewSource = true }, enabled = state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting, modifier = Modifier.weight(1f)) { Text("新增源") }
            OutlinedButton(onClick = { showImport = true }, enabled = state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting, modifier = Modifier.weight(1f)) { Text("粘贴导入") }
            OutlinedButton(onClick = { openSourceDocument.launch(arrayOf("*/*")) }, enabled = state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting && !state.loading, modifier = Modifier.weight(1f)) { Text("文件导入") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { createSourceDocument.launch("tvbox-sources.json") }, enabled = state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting && !state.loading, modifier = Modifier.weight(1f)) { Text(if (state.exporting) "正在导出…" else "导出源配置") }
            OutlinedButton(onClick = { confirmRestoreDefaults = true }, enabled = state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting && !state.loading, modifier = Modifier.weight(1f)) { Text("恢复内置源") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = state.sources.isNotEmpty() && state.sources.all { it.sourceId in selectedSourceIds },
                onCheckedChange = { checked -> selectedSourceIds = if (checked) state.sources.map { it.sourceId }.toSet() else emptySet() },
                enabled = state.sources.isNotEmpty() && state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting,
            )
            Text("全选 · 已选 ${selectedSourceIds.count { id -> state.sources.any { it.sourceId == id } }} 个", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { pendingBulkAction = true },
                enabled = selectedSourceIds.isNotEmpty() && state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting,
            ) { Text("批量启用") }
            OutlinedButton(
                onClick = { pendingBulkAction = false },
                enabled = selectedSourceIds.isNotEmpty() && state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting,
            ) { Text("批量停用") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { confirmBatchDelete = true },
                enabled = selectedSourceIds.isNotEmpty() && state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting && !state.loading,
                modifier = Modifier.weight(1f),
            ) { Text("批量删除") }
            Text("仅删除所选源配置，不删除收藏、历史或进度；部分失败会逐项报告。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { confirmBatchHealthCheck = true },
                enabled = selectedSourceIds.isNotEmpty() && selectedSourceIds.size <= 10 && state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting && !state.loading,
                modifier = Modifier.weight(1f),
            ) { Text(if (state.testingSourceId == "__batch__") "检测 ${state.batchTestCompleted}/${state.batchTestTotal}" else "批量连通性检测（最多10个）") }
            Text("逐项检测并保留结果；每批最多 10 个源。若选择超过 10 个，请减少选择后分批检测。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, modifier = Modifier.weight(1f))
        }
        if (state.testingSourceId == "__batch__" && state.batchTestTotal > 0) {
            Text("正在检测 ${state.batchTestCompleted + 1}/${state.batchTestTotal}：${state.sources.firstOrNull { it.sourceId == state.batchCurrentSourceId }?.name ?: "准备中"}", style = MaterialTheme.typography.labelMedium, color = MyBoxTextSecondary)
        }
        Text(
            "支持新增、编辑、删除、粘贴文本或从手机文件导入、配置 JSON 导出、恢复缺失的项目内置源，以及批量启停、批量删除和最多 10 个源的顺序连通性检测。批量删除仅移除选中的源配置，不删除收藏、历史或播放进度；默认源引用会按现有服务规则清理。文件导入仅读取所选文件，不执行其中脚本；单文件最多读取 5 MiB。恢复操作只补充缺失项，不覆盖或删除现有源；内置地址不会在恢复时联网检测。连通性失败不等于所有适配器业务均不可用；扩展运行能力仍受阶段二能力矩阵限制。",
            style = MaterialTheme.typography.bodySmall,
            color = MyBoxTextSecondary,
        )
        state.operationMessage?.let { StatusCard(it) }
        state.error?.let { error ->
            StatusCard("操作失败：${friendlySourceError(error)}")
            OutlinedButton(onClick = { sourceViewModel.load(force = true) }, enabled = !state.loading) { Text("重新加载") }
        }

        when {
            state.loading && state.sources.isEmpty() -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("正在读取内容源…", color = MyBoxTextSecondary)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp), modifier = Modifier.weight(1f)) {
                if (state.sources.isEmpty()) item { StatusCard("还没有内容源。可以新增单个源、粘贴文本，或从手机文件导入 TXT、M3U、JSON/TVBox 配置。") }
                items(state.sources, key = { it.sourceId }) { source ->
                    SourceCard(
                        source = source,
                        isDefault = if (source.sourceType == "live") state.defaultLiveSourceId == source.sourceId else state.defaultMovieSourceId == source.sourceId,
                        busy = state.mutatingSourceId == source.sourceId || state.mutatingSourceId == "__import__" || state.mutatingSourceId == "__new_source__" || state.mutatingSourceId == "__bulk__" || state.mutatingSourceId == "__restore_defaults__" || state.mutatingSourceId == "__file_import__" || state.mutatingSourceId == "__bulk_delete__",
                        testing = state.testingSourceId == source.sourceId || state.batchCurrentSourceId == source.sourceId,
                        healthResult = state.healthResults[source.sourceId],
                        selected = source.sourceId in selectedSourceIds,
                        onSelectedChange = { checked -> selectedSourceIds = if (checked) selectedSourceIds + source.sourceId else selectedSourceIds - source.sourceId },
                        actionsEnabled = state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting,
                        onEnabledChange = { sourceViewModel.setEnabled(source, it) },
                        onTest = { sourceViewModel.testSource(source) },
                        onEdit = { editingSource = source },
                        onDelete = { deletingSource = source },
                        onSetDefault = { sourceViewModel.setDefaultSource(source) },
                    )
                }
            }
        }
    }

    if (showNewSource || editingSource != null) {
        SourceEditDialog(
            source = editingSource,
            onDismiss = { showNewSource = false; editingSource = null },
            onSave = { name, type, url -> sourceViewModel.saveSource(editingSource, name, type, url); showNewSource = false; editingSource = null },
        )
    }
    if (showImport) {
        SourceImportDialog(
            onDismiss = { showImport = false },
            onImport = { text, fileName -> sourceViewModel.importText(text, fileName); showImport = false },
        )
    }
    if (confirmRestoreDefaults) {
        AlertDialog(
            onDismissRequest = { confirmRestoreDefaults = false },
            title = { Text("恢复项目内置源？") },
            text = { Text("只补充当前列表中缺失的项目内置源。不会删除手动添加的源，不会覆盖已有同地址源的名称、启停状态或扩展配置，也不会联网检测内置地址。") },
            confirmButton = {
                Button(onClick = { confirmRestoreDefaults = false; sourceViewModel.restoreDefaultSources() }, enabled = state.mutatingSourceId == null && !state.exporting) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmRestoreDefaults = false }) { Text("取消") } },
        )
    }
    pendingBulkAction?.let { enable ->
        val targetCount = selectedSourceIds.count { id -> state.sources.any { it.sourceId == id } }
        AlertDialog(
            onDismissRequest = { pendingBulkAction = null },
            title = { Text(if (enable) "批量启用内容源？" else "批量停用内容源？") },
            text = { Text("将${if (enable) "启用" else "停用"}已选择的 $targetCount 个内容源。${if (enable) "" else "被停用的源若为默认源，对应默认选择会被清除。"}此操作不删除源配置、收藏或历史记录。") },
            confirmButton = {
                Button(onClick = {
                    val ids = selectedSourceIds.toList()
                    pendingBulkAction = null
                    sourceViewModel.setSourcesEnabled(ids, enable)
                    selectedSourceIds = emptySet()
                }, enabled = targetCount > 0 && state.mutatingSourceId == null) { Text("确认执行") }
            },
            dismissButton = { TextButton(onClick = { pendingBulkAction = null }) { Text("取消") } },
        )
    }
    if (confirmBatchDelete) {
        val targets = state.sources.filter { it.sourceId in selectedSourceIds }
        val previewNames = targets.take(12).joinToString("、") { it.name }
        val remainingCount = (targets.size - 12).coerceAtLeast(0)
        val defaultCount = targets.count { source ->
            if (source.sourceType == "live") state.defaultLiveSourceId == source.sourceId else state.defaultMovieSourceId == source.sourceId
        }
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("批量删除内容源？") },
            text = {
                Text(
                    "将逐项删除已选中的 ${targets.size} 个源配置，此操作不可撤销。\n" +
                        "${previewNames}${if (remainingCount > 0) " 等（另有 $remainingCount 个）" else ""}\n" +
                        "${if (defaultCount > 0) "$defaultCount 个是当前默认源，对应默认引用将被清除。\n" else ""}" +
                        "只删除内容源定义，不删除收藏、播放历史、播放进度或其他用户数据。每项会单独记录结果；部分失败不会回滚已成功删除的源。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = targets.map { it.sourceId }
                        confirmBatchDelete = false
                        selectedSourceIds = emptySet()
                        sourceViewModel.removeSources(ids)
                    },
                    enabled = targets.isNotEmpty() && state.mutatingSourceId == null && state.testingSourceId == null && !state.exporting,
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") } },
        )
    }
    if (confirmBatchHealthCheck) {
        val targets = state.sources.filter { it.sourceId in selectedSourceIds }.take(10)
        AlertDialog(
            onDismissRequest = { confirmBatchHealthCheck = false },
            title = { Text("批量检测内容源？") },
            text = { Text("将按列表顺序逐个进行有限 HTTP 连通性检测，共 ${targets.size} 个源，最多 10 个。每个源的结果会单独保留。检测失败只表示本次探测未通过，不等于该源所有业务功能都不可用；检测会产生网络请求。") },
            confirmButton = {
                Button(onClick = {
                    val ids = targets.map { it.sourceId }
                    confirmBatchHealthCheck = false
                    sourceViewModel.testSources(ids)
                }, enabled = targets.isNotEmpty() && state.testingSourceId == null && state.mutatingSourceId == null) { Text("开始检测") }
            },
            dismissButton = { TextButton(onClick = { confirmBatchHealthCheck = false }) { Text("取消") } },
        )
    }
    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            title = { Text("删除内容源？") },
            text = { Text("将删除“${source.name}”及其源配置。若它是默认源，对应默认选择会被清除。此操作不会删除收藏或历史记录。") },
            confirmButton = { Button(onClick = { sourceViewModel.removeSource(source); deletingSource = null }) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { deletingSource = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SourceEditDialog(
    source: NativeContentSource?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(source?.sourceId) { mutableStateOf(source?.name.orEmpty()) }
    var sourceType by remember(source?.sourceId) { mutableStateOf(source?.sourceType ?: "movie") }
    var sourceRef by remember(source?.sourceId) { mutableStateOf(source?.sourceRef.orEmpty()) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "新增内容源" else "编辑内容源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("源名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column {
                    Text("源类型", style = MaterialTheme.typography.labelMedium, color = MyBoxTextSecondary)
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (sourceType == "live") "直播源" else "影视源") }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("影视源") }, onClick = { sourceType = "movie"; typeMenuExpanded = false })
                        DropdownMenuItem(text = { Text("直播源") }, onClick = { sourceType = "live"; typeMenuExpanded = false })
                    }
                }
                OutlinedTextField(value = sourceRef, onValueChange = { sourceRef = it }, label = { Text("源地址 / URL") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Text("编辑只修改名称、类型和地址；其他扩展字段会保留。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, sourceType, sourceRef) }, enabled = name.isNotBlank() && sourceRef.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SourceImportDialog(onDismiss: () -> Unit, onImport: (String, String) -> Unit) {
    var fileName by remember { mutableStateOf("source.txt") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入内容源文本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("文件名（用于格式识别）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("粘贴 TXT / M3U / JSON / TVBox 内容") }, modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 9)
                Text("本入口导入粘贴的文本，不会自动读取手机文件。导入前请确认内容来源可信。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
        },
        confirmButton = { Button(onClick = { onImport(content, fileName.ifBlank { "source.txt" }) }, enabled = content.isNotBlank()) { Text("开始导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SourceCard(
    source: NativeContentSource,
    isDefault: Boolean,
    busy: Boolean,
    testing: Boolean,
    healthResult: SourceHealthCheckResult?,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    actionsEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = onSelectedChange, enabled = actionsEnabled && !busy && !testing)
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${source.sourceType.sourceTypeLabel()} · ${source.adapterType ?: source.tvboxAdapterKind ?: "默认适配"}${if (isDefault) " · 默认" else ""}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                }
                Switch(checked = source.enabled, onCheckedChange = onEnabledChange, enabled = actionsEnabled && !busy)
            }
            Text(source.sourceRef.ifBlank { "未配置源地址" }, style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${if (source.enabled) "已启用" else "已停用"} · ${source.status}", style = MaterialTheme.typography.labelMedium, color = MyBoxTextSecondary, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onTest, enabled = actionsEnabled && source.sourceRef.isNotBlank()) { Text(if (testing) "检测中…" else "检测") }
            }
            healthResult?.let { result ->
                Text(
                    text = if (result.ok) {
                        "最近检测：成功${result.httpStatus?.let { " · HTTP $it" } ?: ""} · ${result.durationMs} ms"
                    } else {
                        "最近检测：失败 · ${result.errorCode ?: "SOURCE_TEST_FAILED"}${result.httpStatus?.let { " · HTTP $it" } ?: ""}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MyBoxTextSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, enabled = actionsEnabled, modifier = Modifier.weight(1f)) { Text("编辑") }
                OutlinedButton(onClick = onSetDefault, enabled = actionsEnabled && source.enabled && !isDefault, modifier = Modifier.weight(1f)) { Text(if (isDefault) "默认源" else "设为默认") }
                TextButton(onClick = onDelete, enabled = actionsEnabled) { Text("删除") }
            }
            if (busy || testing) {
                Spacer(Modifier.height(2.dp))
                Text(if (busy) "正在保存内容源…" else "正在进行限额 HTTP 连通性检测…", style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary)
            }
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MyBoxSurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun String.sourceTypeLabel(): String = if (this == "live") "直播源" else "影视源"

private fun friendlySourceError(raw: String): String = when {
    raw.contains("SOURCE_REGISTRY_SYNC_REQUIRED") -> "源数据已变更，但能力注册同步失败；刷新或重试操作以修复。"
    raw.contains("NATIVE_SERVICES_UNAVAILABLE") -> "原生服务初始化失败，请重试。"
    raw.contains("SOURCE_NOT_FOUND") -> "该内容源已不存在，请刷新列表。"
    raw.contains("SOURCE_NAME_AND_URL_REQUIRED") -> "源名称和地址不能为空。"
    raw.contains("SOURCE_IMPORT_EMPTY") -> "没有从文件或文本中解析出可导入的内容源。"
    raw.contains("SOURCE_IMPORT_TOO_LARGE") || raw.contains("SOURCE_FILE_TOO_LARGE") -> "导入文件超过 5 MiB 上限，请拆分后再导入。"
    raw.contains("SOURCE_FILE_UNREADABLE") -> "无法读取所选文件，请检查文件权限或重新选择。"
    raw.contains("SOURCE_FILE_UTF8_INVALID") -> "文件不是有效的 UTF-8 文本，请先转换编码后再导入。"
    raw.contains("SOURCE_IMPORT_JSON_INVALID") || raw.contains("SOURCE_IMPORT_TVBOX_EMPTY") -> "文件内容不是可识别的 TXT、M3U、JSON 或 TVBox 配置，或未包含有效源条目。"
    raw.contains("SOURCE_REGISTRY_SYNC_REQUIRED") -> "源配置可能已保存，但能力注册同步失败；已尝试刷新列表，请检查状态后再操作。"
    raw.contains("SOURCE_BULK_UPDATE_FAILED") -> "批量操作未能完成，请刷新列表后重试。"
    raw.contains("SOURCE_BULK_DELETE_PARTIAL_FAILURE") -> "部分内容源未能删除；请查看逐项结果并刷新后重试。"
    raw.contains("SOURCE_BULK_DELETE_FAILED") -> "批量删除过程中发生错误；已尝试刷新列表，请核对逐项结果。"
    else -> raw.take(180)
}

private fun sourceManagementViewModelFactory(application: MyBoxApplication): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SourceManagementViewModel::class.java)) return SourceManagementViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
