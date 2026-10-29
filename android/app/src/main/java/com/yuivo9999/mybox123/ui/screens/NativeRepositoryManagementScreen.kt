package com.yuivo9999.mybox123.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.ui.theme.AppAppearanceController
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeRepositoryManagementScreen(modifier: Modifier = Modifier) {
    val appearance by AppAppearanceController.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(RepositoryManagementViewModel::class.java)) return RepositoryManagementViewModel(application) as T
                throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
            }
        }
    }
    val viewModel: RepositoryManagementViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    var editing by remember { mutableStateOf<RepositoryEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showImporter by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RepositoryEntry?>(null) }

    LaunchedEffect(viewModel) { viewModel.load() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("多仓库管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("管理 TVBox 多仓地址与启用状态", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
            OutlinedButton(onClick = { viewModel.load(force = true) }, enabled = !state.loading && !state.saving && !state.aggregationRunning) { Text("刷新") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { editing = null; showEditor = true }, enabled = !state.saving && state.syncingRepositoryId == null && !state.aggregationRunning, modifier = Modifier.weight(1f)) { Text("添加仓库") }
            OutlinedButton(onClick = { showImporter = true }, enabled = !state.saving && state.syncingRepositoryId == null && !state.aggregationRunning, modifier = Modifier.weight(1f)) { Text("文本导入") }
        }
        OutlinedButton(onClick = { viewModel.aggregatePreview() }, enabled = !state.saving && state.syncingRepositoryId == null && !state.aggregationRunning && state.loaded, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.aggregationRunning) "正在批量聚合预览…" else "批量聚合预览（最多 5 个仓库）")
        }
        Text("单仓预览和批量聚合均只读。批量预览最多读取 5 个已启用仓库，每仓最多尝试 3 个地址；不会导入、保存或应用到影视/直播源。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, modifier = Modifier.padding(vertical = 10.dp))
        state.message?.let { RepositoryInfoCard(it) }
        state.error?.let { RepositoryInfoCard("操作失败：$it") }
        state.aggregation?.let { summary ->
            Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("聚合预览结果（未保存）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("仓库：成功 ${summary.successfulRepositories}，失败 ${summary.failedRepositories.size}", style = MaterialTheme.typography.bodySmall)
                    summary.outcomes.forEach { outcome ->
                        val status = if (outcome.success) "成功" else "失败"
                        Text("$status：${outcome.name} · ${outcome.detail}${if (outcome.success && outcome.usedUrl.isNotBlank()) " · 地址 ${outcome.usedUrl}" else ""}", style = MaterialTheme.typography.bodySmall, color = if (outcome.success) MyBoxTextSecondary else MaterialTheme.colorScheme.error)
                    }
                    Text("影视站点：原始 ${summary.sitesRaw}，去重后 ${summary.sitesUnique}，重复 ${summary.sitesRaw - summary.sitesUnique}", style = MaterialTheme.typography.bodySmall)
                    Text("直播分组：原始 ${summary.livesRaw}，去重后 ${summary.livesUnique}，重复 ${summary.livesRaw - summary.livesUnique}", style = MaterialTheme.typography.bodySmall)
                    Text("解析规则：原始 ${summary.parsesRaw}，去重后 ${summary.parsesUnique}，重复 ${summary.parsesRaw - summary.parsesUnique}", style = MaterialTheme.typography.bodySmall)
                    Text("分类结果：安全完全重复组 ${summary.safeDuplicateGroups}；疑似冲突组 ${summary.potentialConflictGroups}；不支持条目 ${summary.unsupportedEntries}", style = MaterialTheme.typography.bodySmall, color = if (summary.unsupportedEntries > 0 || summary.potentialConflictGroups > 0) MaterialTheme.colorScheme.error else MyBoxTextSecondary)
                    if (summary.reviewMetadataTruncated) {
                        Text("警告：审查元数据已达到导出上限，当前 JSON 的重复/冲突/不支持条目明细不完整。不要据此认定已完成全面审查；请缩小仓库范围或分批检查。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("审查元数据未触发当前导出上限；这不代表记录已通过业务语义核验。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                    }
                    summary.failedRepositories.take(3).forEach { Text("未成功：$it", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary) }
                    Text("导出会区分三类：完整内容与身份均一致的记录归为安全重复；同一身份提示但内容不同的记录保留在配置并标为疑似冲突；非对象条目列为不支持项且不进入配置。疑似冲突不会自动覆盖或删除。每条重复/冲突/不支持记录均附仓库名称、来源地址及原始数组下标，方便回查。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                    Text("安全边界：当前不写入正式配置，因此没有自动备份或回滚动作。未来若开放导入，必须先生成可验证备份、显示冲突清单、经用户确认后再执行原子化写入；失败时恢复备份。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                    OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(summary.exportJson)) }, modifier = Modifier.fillMaxWidth()) { Text("复制结构化 JSON 预览") }
                    Text("导出格式：mybox-tvbox-aggregate-preview-v1；包含 validation、validationIssues、safeDuplicates、potentialConflicts 和 importReadiness。importReadiness.readyForImport 固定为 false，并列出未来导入前置条件；本版本没有导入或应用入口。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                }
            }
        }
        when {
            state.loading && !state.loaded -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(12.dp)) {
                CircularProgressIndicator()
                Text("正在读取仓库…", color = MyBoxTextSecondary)
            }
            state.error != null && !state.loaded -> Button(onClick = { viewModel.load(force = true) }, enabled = !state.loading) { Text("重试") }
            state.repositories.isEmpty() -> RepositoryInfoCard("当前没有仓库。可以添加地址，或通过文本导入多个仓库。")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp), modifier = Modifier.weight(1f)) {
                items(state.repositories, key = { it.id }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(if (entry.enabled) "已启用" else "已停用", style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary)
                                }
                                Switch(checked = entry.enabled, onCheckedChange = { viewModel.toggle(entry, it) }, enabled = !state.saving && !state.aggregationRunning)
                            }
                            Text(entry.primaryUrl, style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            if (entry.backupUrls.isNotEmpty()) Text("备用地址：${entry.backupUrls.size} 个", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            if (entry.status == "preview_ok") {
                                Text("最近预览：影视 ${entry.siteCount} · 直播 ${entry.liveCount} · 解析 ${entry.parseCount}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                                if (entry.usedUrl.isNotBlank()) Text("命中地址：${entry.usedUrl}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            }
                            if (entry.status == "preview_failed" && entry.errorMessage.isNotBlank()) Text("最近失败：${entry.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { viewModel.syncPreview(entry) }, enabled = !state.saving && state.syncingRepositoryId == null && !state.aggregationRunning && entry.enabled, modifier = Modifier.weight(1f)) { Text(if (state.syncingRepositoryId == entry.id) "尝试中…" else "同步预览") }
                                OutlinedButton(onClick = { editing = entry; showEditor = true }, enabled = !state.saving && state.syncingRepositoryId == null && !state.aggregationRunning, modifier = Modifier.weight(1f)) { Text("编辑") }
                                OutlinedButton(onClick = { deleting = entry }, enabled = !state.saving && state.syncingRepositoryId == null && !state.aggregationRunning, modifier = Modifier.weight(1f)) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
        if (state.saving) Text("正在保存…", color = MyBoxTextSecondary, modifier = Modifier.padding(top = 8.dp))
    }

    if (showEditor) RepositoryEditDialog(
        initial = editing,
        onDismiss = { showEditor = false; editing = null },
        onSave = { name, url, backups -> viewModel.save(name, url, backups, editing?.id); showEditor = false; editing = null },
    )
    if (showImporter) RepositoryImportDialog(
        onDismiss = { showImporter = false },
        onImport = { text -> viewModel.importText(text); showImporter = false },
    )
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除仓库？") },
            text = { Text("将删除“${entry.name}”的本地仓库定义，不会删除其他内容源或用户记录。") },
            confirmButton = { TextButton(onClick = { viewModel.remove(entry); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RepositoryEditDialog(initial: RepositoryEntry?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember(initial?.id) { mutableStateOf(initial?.primaryUrl.orEmpty()) }
    var backups by remember(initial?.id) { mutableStateOf(initial?.backupUrls?.joinToString("\n").orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加仓库" else "编辑仓库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("仓库名称") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("主地址 URL") }, singleLine = false)
                OutlinedTextField(backups, { backups = it }, label = { Text("备用地址（每行一个，可选）") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, url, backups) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RepositoryImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入多仓地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("支持每行一个 URL、名称,URL 或 TVBox 的 {\"urls\":[{\"name\":\"…\",\"url\":\"https://…\",\"backupUrls\":[]}]} 格式。单次文本不超过 1 MiB、最多 500 个仓库、每仓最多 20 个备用地址；无效记录会整体拒绝，重复主地址会跳过。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                OutlinedTextField(text, { text = it }, label = { Text("粘贴仓库文本") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RepositoryInfoCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
    }
}
