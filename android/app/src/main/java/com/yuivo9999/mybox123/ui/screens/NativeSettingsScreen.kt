package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.ui.theme.AppAppearanceController
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeSettingsScreen(modifier: Modifier = Modifier) {
    val appearance by AppAppearanceController.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) return SettingsViewModel(application) as T
                throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
            }
        }
    }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("应用设置与外观", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("原生设置 · 持久化到用户设置仓库", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
            }
            OutlinedButton(onClick = { viewModel.load(force = true) }, enabled = !state.loading && !state.saving) { Text("刷新") }
        }
        when {
            state.loading && !state.loaded -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator()
                Text("正在读取设置…", color = MyBoxTextSecondary)
            }
            state.error != null && !state.loaded -> {
                SettingsInfoCard("读取失败：${state.error}")
                Button(onClick = { viewModel.load(force = true) }, enabled = !state.loading) { Text("重试") }
            }
            else -> {
                Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("继续上次播放", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("保存 autoplayResume 偏好；播放器接入后再由播放流程消费此设置。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        }
                        Switch(checked = state.autoplayResume, onCheckedChange = viewModel::setAutoplayResume, enabled = !state.saving)
                    }
                }
                SettingsChoiceCard("字体大小", state.fontSize, listOf("small" to "小", "medium" to "中", "large" to "大"), !state.saving, viewModel::setFontSize)
                SettingsChoiceCard("字体", state.fontFamily, listOf("system" to "系统", "serif" to "衬线", "monospace" to "等宽"), !state.saving, viewModel::setFontFamily)
                SettingsChoiceCard("内容卡片", state.cardStyle, listOf("poster" to "海报卡片", "list" to "列表"), !state.saving, viewModel::setCardStyle)
                SettingsChoiceCard("页面密度", state.density, listOf("comfortable" to "舒适", "compact" to "紧凑"), !state.saving, viewModel::setDensity)
                SettingsInfoCard("已生效：桑田深色主题（当前唯一实现配色）、字体大小、字体类型；卡片样式和页面密度已接入影视、直播、收藏列表。仅保存、尚未生效：‘继续上次播放’偏好，待播放器流程迁移后消费。未知旧主题值只回退显示为当前支持主题，不代表旧主题已迁移。")
                state.message?.let { SettingsInfoCard(it) }
                state.error?.let { SettingsInfoCard("保存失败：$it") }
                if (state.saving) Text("正在保存…", color = MyBoxTextSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsChoiceCard(
    title: String,
    selected: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                choices.forEach { (value, label) ->
                    if (selected == value) Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text(label) }
                    else OutlinedButton(onClick = { onSelect(value) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun SettingsInfoCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
    }
}
