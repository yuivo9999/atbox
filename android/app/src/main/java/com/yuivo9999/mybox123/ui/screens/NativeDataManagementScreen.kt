package com.yuivo9999.mybox123.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.LegacyMigrationResult
import com.yuivo9999.mybox123.ui.theme.AppAppearanceController
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeDataManagementScreen(modifier: Modifier = Modifier) {
    val appearance by AppAppearanceController.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DataManagementViewModel::class.java)) return DataManagementViewModel(application) as T
                throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
            }
        }
    }
    val viewModel: DataManagementViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmClearRecords by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmClearLiveCache by remember { mutableStateOf(false) }
    var confirmApplyImport by remember { mutableStateOf(false) }
    var pendingRollbackOperationId by remember { mutableStateOf<String?>(null) }
    val openImportDocument = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.previewImport(context.contentResolver, uri)
    }
    val openLegacyBackupDocument = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.previewLegacyBackupFile(context.contentResolver, uri)
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) viewModel.writeExport(context.contentResolver, uri, pending.first, pending.second)
    }
    LaunchedEffect(viewModel) { viewModel.load() }

    if (confirmClearRecords) {
        AlertDialog(
            onDismissRequest = { confirmClearRecords = false },
            title = { Text("清除用户记录？") },
            text = { Text("将清除收藏、播放历史、进度和搜索历史。内容源、设置、多仓配置及迁移备份不会被清除。此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { confirmClearRecords = false; viewModel.clearUserRecords() }) { Text("确认清除") } },
            dismissButton = { TextButton(onClick = { confirmClearRecords = false }) { Text("取消") } },
        )
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("清除播放历史与进度？") },
            text = { Text("只清除播放历史和进度记录，不修改收藏、搜索历史、内容源、设置或迁移备份。此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { confirmClearHistory = false; viewModel.clearHistoryAndProgress() }) { Text("确认清除") } },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("取消") } },
        )
    }
    if (confirmClearLiveCache) {
        AlertDialog(
            onDismissRequest = { confirmClearLiveCache = false },
            title = { Text("清除直播缓存？") },
            text = { Text("将清除本机缓存的频道、播放流列表和节目单数据。下次进入相关页面时可能需要重新加载；不会清除收藏、历史、设置、内容源或多仓配置。") },
            confirmButton = { TextButton(onClick = { confirmClearLiveCache = false; viewModel.clearLiveCache() }, enabled = !state.clearing) { Text("确认清理") } },
            dismissButton = { TextButton(onClick = { confirmClearLiveCache = false }) { Text("取消") } },
        )
    }
    pendingRollbackOperationId?.let { operationId ->
        AlertDialog(
            onDismissRequest = { pendingRollbackOperationId = null },
            title = { Text("撤销该导入操作？") },
            text = { Text("操作编号：$operationId\n系统将使用该操作保存的快照恢复数据。如果相关键之后被其他操作修改，回滚会中止以避免覆盖新数据。") },
            confirmButton = { TextButton(onClick = { pendingRollbackOperationId = null; viewModel.rollbackImport(operationId) }, enabled = !state.rollingBackImport) { Text("确认撤销") } },
            dismissButton = { TextButton(onClick = { pendingRollbackOperationId = null }) { Text("取消") } },
        )
    }
    if (confirmApplyImport) {
        val preview = state.importPreview
        val selected = state.importActions.filter { (key, action) -> canReplaceImportKey(key) && action == ImportKeyAction.REPLACE_KEY }.keys.sorted()
        val byKey = preview?.supportedKeys?.associateBy { it.key }.orEmpty()
        val impactText = selected.joinToString("\n") { key ->
            val item = byKey[key]
            if (item == null) "$key：替换整个数据键"
            else "$key：现有 ${item.currentRecords} → 文件 ${item.incomingRecords}；重复身份 ${item.duplicateIncoming}，现有身份冲突 ${item.identityConflicts}，身份不明 ${item.recordsWithoutStableIdentity}；将整体替换此键的全部内容"
        }
        AlertDialog(
            onDismissRequest = { confirmApplyImport = false },
            title = { Text("确认应用所选数据键？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仅应用以下 ${selected.size} 个明确选择的键；每个键都会整体替换，不会逐条合并。其他键保持不变。")
                    Text(impactText)
                    Text("应用前会再次核对预览时的原始数据。如果期间发生变化，整个操作将拒绝应用。系统会在同一事务中保存恢复快照。")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected.isNotEmpty() && !state.applyingImport,
                    onClick = { confirmApplyImport = false; viewModel.applyImportPlan() },
                ) { Text(if (state.applyingImport) "正在应用…" else "确认替换所选键") }
            },
            dismissButton = { TextButton(onClick = { confirmApplyImport = false }) { Text("返回检查") } },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("数据管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("导出、记录清理与旧数据迁移状态", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
            }
            OutlinedButton(onClick = { viewModel.load(force = true) }, enabled = !state.loading && !state.exporting && !state.clearing && !state.previewingImport && !state.applyingImport && !state.rollingBackImport) { Text("刷新") }
        }
        when {
            state.loading && !state.loaded -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator()
                Text("正在读取数据摘要…", color = MyBoxTextSecondary)
            }
            state.error != null && !state.loaded -> {
                DataManagementInfoCard("读取失败：${state.error}")
                Button(onClick = { viewModel.load(force = true) }, enabled = !state.loading) { Text("重试") }
            }
            else -> {
                state.counts?.let { counts ->
                    Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("本地数据摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            DataCountLine("收藏", counts.favorites)
                            DataCountLine("播放历史", counts.history)
                            DataCountLine("播放进度", counts.progress)
                            DataCountLine("搜索历史", counts.searches)
                            DataCountLine("内容源", counts.sources)
                            DataCountLine("多仓定义", counts.repositories)
                            Text("计数只统计可解析的当前原生记录；格式异常时显示‘未知’，不会自动覆盖或修复原始数据。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("导出用户数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("导出当前原生用户数据为 JSON 文件；包含活动数据键，不包含 SQLite 迁移备份表。导出文件可能含个人使用记录，请妥善保管。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        Button(onClick = { viewModel.prepareExport { payload -> pendingExport = payload to "用户数据"; createDocument.launch("mybox-user-data.json") } }, enabled = !state.exporting && !state.clearing && !state.previewingImport && !state.applyingImport && !state.rollingBackImport) {
                            if (state.exporting) CircularProgressIndicator() else Text("导出 JSON 文件")
                        }
                        OutlinedButton(
                            onClick = { viewModel.prepareLegacyBackupExport { payload -> pendingExport = payload to "旧数据迁移备份"; createDocument.launch("mybox-legacy-migration-backup.json") } },
                            enabled = !state.exporting && !state.clearing && !state.previewingImport && !state.applyingImport && !state.rollingBackImport,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("导出旧数据迁移备份（${state.migration?.backupCount ?: "未知"} 项）") }
                        Text("迁移备份导出保留迁移时捕获的原始值（包括格式异常值），不会修改或清除数据库。此文件是独立备份格式，当前标准导入器不会接受它；不要将其当作普通用户数据导入文件。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        OutlinedButton(
                            onClick = { openLegacyBackupDocument.launch("application/json") },
                            enabled = !state.exporting && !state.clearing && !state.previewingImport && !state.applyingImport && !state.rollingBackImport,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.previewingImport) "正在校验…" else "校验旧数据迁移备份文件（只读）") }
                        state.legacyBackupPreview?.let { backup ->
                            Text("备份文件结构有效：${backup.backupCount} 项，文件 ${backup.byteCount} 字节，原始值 ${backup.rawValueByteCount} 字节。导出时间：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(backup.exportedAt))}。", fontWeight = FontWeight.SemiBold)
                            Text("数据完整性：${if (backup.integrityVerified) "SHA-256 校验通过" else "旧版文件没有数据校验和，无法验证内容完整性"}", style = MaterialTheme.typography.bodyMedium, color = if (backup.integrityVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text("文件 SHA-256：${backup.sha256}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            Text("包含的原始键：${backup.keys.joinToString().ifBlank { "（空备份）" }}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            Text("此处只校验文件结构、版本、计数与原始字符串值，不会恢复到数据库或覆盖任何当前数据。文件 SHA-256 用于识别当前文件；数据校验和可检测意外改动，但不证明文件来源可信。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("导入文件校验与预览（只读）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("选择本应用导出的 JSON 文件。校验后所有数据键默认跳过；只有逐键选择‘替换整个键’并二次确认才会应用。不会逐条合并；多仓定义因身份规则未统一而保持禁用。文件上限 5 MiB。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        OutlinedButton(onClick = { openImportDocument.launch("application/json") }, enabled = !state.exporting && !state.clearing && !state.previewingImport && !state.applyingImport && !state.rollingBackImport, modifier = Modifier.fillMaxWidth()) {
                            if (state.previewingImport) CircularProgressIndicator() else Text("选择 JSON 并预览")
                        }
                        state.importPreview?.let { preview ->
                            Text("格式版本：${preview.formatVersion}；支持键：${preview.supportedKeys.size}；待检查记录：${preview.totalIncomingRecords}", fontWeight = FontWeight.SemiBold)
                            Text("文件完整性：${if (preview.checksumStatus == "VERIFIED") "数据 SHA-256 校验通过" else "旧版文件没有数据校验和，完整性无法验证"}；导出时间：${preview.exportedAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "未知"}", style = MaterialTheme.typography.bodySmall, color = if (preview.checksumStatus == "VERIFIED") MyBoxTextSecondary else MaterialTheme.colorScheme.error)
                            if (preview.envelopeUnknownKeys.isNotEmpty()) Text("未知封套字段（不会导入）：${preview.envelopeUnknownKeys.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text("文件内重复身份：${preview.totalDuplicateIncoming}；与当前数据身份冲突：${preview.totalIdentityConflicts}；缺少稳定身份：${preview.totalRecordsWithoutStableIdentity}；整键冲突：${preview.totalWholeKeyConflicts}", style = MaterialTheme.typography.bodySmall)
                            Text("选择策略：逐键跳过或整体替换；不执行记录级自动合并。替换会移除该键中所有未包含在导入文件内的现有内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            preview.supportedKeys.forEach { item ->
                                val countLabel = if (item.key == "settings" || item.key == "selectedSources") "字段" else "记录"
                                val action = state.importActions[item.key] ?: ImportKeyAction.SKIP
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${item.key}：文件${countLabel} ${item.incomingRecords} / 当前${countLabel} ${item.currentRecords}；重复 ${item.duplicateIncoming}；身份冲突 ${item.identityConflicts}；身份不明 ${item.recordsWithoutStableIdentity}${if (item.wholeKeyConflict) "；整键冲突" else ""}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                                    if (!canReplaceImportKey(item.key)) {
                                        val reason = when (item.key) {
                                            "multi_repos" -> "多仓身份与合并规则尚未统一：本轮禁止应用该键。"
                                            "sources" -> "内容源替换可能使设置中的默认源引用失效：本轮禁止应用该键。"
                                            "settings", "selectedSources" -> "设置与选中源存在跨键引用：待实现引用一致性校验前禁止应用该键。"
                                            else -> "该键尚未纳入安全应用范围。"
                                        }
                                        Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(onClick = { viewModel.setImportAction(item.key, ImportKeyAction.SKIP) }, enabled = !state.applyingImport) { Text(if (action == ImportKeyAction.SKIP) "✓ 跳过" else "跳过此键") }
                                            OutlinedButton(onClick = { viewModel.setImportAction(item.key, ImportKeyAction.REPLACE_KEY) }, enabled = !state.applyingImport) { Text(if (action == ImportKeyAction.REPLACE_KEY) "✓ 整体替换" else "替换整个键") }
                                        }
                                    }
                                }
                            }
                            if (preview.unknownKeys.isNotEmpty()) Text("未知数据键（默认跳过）：${preview.unknownKeys.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (preview.checksumStatus == "LEGACY_NO_CHECKSUM") Text("该旧版文件未提供 dataSha256。结构校验通过不等于内容完整性已验证。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (preview.skippedUnsafeKeys.isNotEmpty()) preview.skippedUnsafeKeys.forEach { Text("需人工处理：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            val selectedCount = state.importActions.count { (key, action) -> canReplaceImportKey(key) && action == ImportKeyAction.REPLACE_KEY }
                            Button(onClick = { confirmApplyImport = true }, enabled = selectedCount > 0 && !state.applyingImport && !state.previewingImport, modifier = Modifier.fillMaxWidth()) { Text("检查并确认应用（${selectedCount} 个键）") }
                            if (state.applyingImport || state.rollingBackImport) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text(if (state.rollingBackImport) "正在恢复原数据…" else "正在保存恢复快照并应用…") }
                            Text("没有选择替换的键时不会写入。未知键、多仓、内容源和跨键设置保持不变。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("清理用户记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("清理仅限明确列出的记录或缓存，不清理内容源、设置、多仓配置或迁移备份。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        state.liveCacheCounts?.let { cache ->
                            Text("直播缓存：频道 ${cache.channels} 项、播放流 ${cache.streams} 项、节目单 ${cache.epg} 项；涉及 ${cache.sources} 个源。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        }
                        OutlinedButton(onClick = { confirmClearLiveCache = true }, enabled = !state.clearing && !state.exporting && !state.previewingImport && !state.applyingImport && !state.rollingBackImport, modifier = Modifier.fillMaxWidth()) { Text("清除直播缓存") }
                        OutlinedButton(onClick = { confirmClearHistory = true }, enabled = !state.clearing && !state.exporting && !state.previewingImport && !state.applyingImport && !state.rollingBackImport, modifier = Modifier.fillMaxWidth()) { Text("清除播放历史与进度") }
                        Button(onClick = { confirmClearRecords = true }, enabled = !state.clearing && !state.exporting && !state.previewingImport && !state.applyingImport && !state.rollingBackImport, modifier = Modifier.fillMaxWidth()) { Text("清除全部用户记录") }
                        if (state.clearing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("正在清理…") }
                    }
                }
                MigrationStatusCard(state.migration)
            }
        }
        state.error?.let { DataManagementInfoCard("操作失败：$it") }
        Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("恢复操作历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("显示最近 20 次导入操作。已应用的操作可尝试撤销；若数据之后被修改，系统会拒绝回滚。记录与快照会保留，不会在查看历史时自动清理。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                if (state.recoveryOperations.isEmpty()) {
                    Text("暂无导入恢复记录。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                } else {
                    state.recoveryOperations.forEach { operation ->
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(operation.createdAt)), style = MaterialTheme.typography.labelMedium)
                            val statusLabel = when (operation.status) {
                                "APPLIED" -> "已应用，可尝试撤销"
                                "ROLLED_BACK" -> "已撤销"
                                else -> "未知/需检查（${operation.status}）"
                            }
                            Text("状态：$statusLabel；影响 ${operation.affectedKeyCount} 个键；格式版本 ${operation.formatVersion}", style = MaterialTheme.typography.bodySmall)
                            if (operation.status != "APPLIED" && operation.status != "ROLLED_BACK") Text("该状态不提供自动撤销入口，请先检查操作记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (operation.status == "APPLIED" && !operation.rollbackEligible) Text("恢复快照记录数（${operation.snapshotEntryCount}）与影响键数（${operation.affectedKeyCount}）不一致；为防止不完整恢复，已禁用撤销。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text("数据键：${operation.affectedKeys.joinToString().ifBlank { "未知" }}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            Text("操作编号：${operation.operationId}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            if (operation.rollbackEligible) {
                                OutlinedButton(
                                    onClick = { pendingRollbackOperationId = operation.operationId },
                                    enabled = !state.applyingImport && !state.rollingBackImport && !state.previewingImport && !state.clearing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (state.rollingBackImport) "正在恢复…" else "撤销此操作") }
                            }
                        }
                    }
                }
            }
        }
        state.message?.let { DataManagementInfoCard(it) }
    }
}

@Composable
private fun DataCountLine(label: String, count: Int?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(count?.toString() ?: "未知", color = MyBoxTextSecondary)
    }
}

@Composable
private fun DataTextLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, color = MyBoxTextSecondary)
    }
}

@Composable
private fun MigrationStatusCard(result: LegacyMigrationResult?) {
    Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("旧数据迁移状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (result == null) {
                Text("当前会话未取得迁移报告。", color = MyBoxTextSecondary)
            } else {
                DataTextLine("迁移完成", if (result.completed) "是" else "否")
                DataTextLine("发现旧数据", if (result.sourceFound) "是" else "否")
                DataCountLine("导入记录数", result.importedCount)
                DataCountLine("备份记录数", result.backupCount)
                Text("最近迁移时间：${if (result.completedAt > 0L) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.completedAt)) else "无"}", style = MaterialTheme.typography.bodySmall)
                Text("状态：${if (result.alreadyRan) "此前已执行" else if (result.completed) "本次执行完成" else "未完成"}", style = MaterialTheme.typography.bodySmall)
                if (result.invalidKnownKeys.isNotEmpty()) Text("存在格式异常键：${result.invalidKnownKeys.joinToString()}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (result.encodedNonStringKeys.isNotEmpty()) Text("已编码的非字符串键：${result.encodedNonStringKeys.joinToString()}", style = MaterialTheme.typography.bodySmall)
                result.errorCode?.let { Text("迁移错误：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("迁移只读取 tvbox_user_data SharedPreferences；仅存在于 WebView localStorage 的数据不会出现在此报告中。旧 SharedPreferences 和迁移备份不会因清理用户记录而删除。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
        }
    }
}

@Composable
private fun DataManagementInfoCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), color = MyBoxTextSecondary)
    }
}
