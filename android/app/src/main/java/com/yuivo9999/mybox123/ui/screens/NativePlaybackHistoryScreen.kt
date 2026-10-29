package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.HistoryRecord
import com.yuivo9999.mybox123.data.storage.PlaybackProgressRecord
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NativePlaybackHistoryScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenMovieDetail: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) { playbackHistoryViewModelFactory(application) }
    val historyViewModel: PlaybackHistoryViewModel = viewModel(factory = factory)
    val state by historyViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(historyViewModel) { historyViewModel.load() }

    val historyItems = remember(state.history) { state.history.map(HistoryRecord::toHistoryPresentation) }
    val progressItems = remember(state.progress) { state.progress.map(PlaybackProgressRecord::toProgressPresentation) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (onBack != null) {
                    OutlinedButton(onClick = onBack) { Text("返回首页") }
                }
                Text("继续观看", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("播放进度 ${progressItems.size} 条 · 最近记录 ${historyItems.size} 条", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
            }
            OutlinedButton(onClick = { historyViewModel.load(force = true) }, enabled = !state.loading) { Text("刷新") }
        }
        Text(
            "当前展示本机已保存的历史与进度。此页面只读；实际播放进度采集、继续播放和清理操作将在播放器阶段接入。",
            style = MaterialTheme.typography.bodySmall,
            color = MyBoxTextSecondary,
        )
        state.error?.let { error ->
            HistoryStatusCard("读取失败：${friendlyHistoryError(error)}")
            Button(onClick = { historyViewModel.load(force = true) }, enabled = !state.loading) { Text("重试") }
        }
        when {
            state.loading && !state.loaded -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("正在读取播放记录…", color = MyBoxTextSecondary)
                }
            }
            state.loaded && historyItems.isEmpty() && progressItems.isEmpty() -> HistoryStatusCard("暂无播放记录或进度。播放器接入后，新记录将由播放流程写入。")
            else -> LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (progressItems.isNotEmpty()) {
                    item { Text("继续观看", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                    items(progressItems, key = { "progress:${it.key}" }) { item -> ProgressCard(item) }
                }
                if (historyItems.isNotEmpty()) {
                    item { Text("最近播放", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
                    items(historyItems, key = { "history:${it.key}" }) { item -> HistoryCard(item, onOpenMovieDetail) }
                }
            }
        }
    }
}

private data class HistoryPresentation(
    val key: String,
    val title: String,
    val subtitle: String,
    val completed: Boolean,
    val detailNavigationJson: String?,
    val detailUnavailableReason: String?,
)
private data class ProgressPresentation(val key: String, val title: String, val subtitle: String, val fraction: Float?, val completed: Boolean)

private fun HistoryRecord.toHistoryPresentation(): HistoryPresentation {
    val json = toJson()
    val title = listOf("title", "name", "vod_name", "contentName", "channelName")
        .firstNotNullOfOrNull { json.optString(it).trim().takeIf(String::isNotEmpty) }
        ?: targetId?.toString()?.takeIf(String::isNotBlank)
        ?: "未命名内容"
    val type = if (targetType.equals("live", true) || targetType.equals("channel", true)) "直播频道" else "影视内容"
    val episode = episodeId.takeIf(String::isNotBlank)?.let { "剧集：$it" }
    val time = lastPlayedAt.formatHistoryTime()
    val normalizedType = targetType.trim().lowercase(Locale.ROOT)
    val sourceIdValue = sourceId?.toString()?.trim().orEmpty().takeUnless { it.isEmpty() || it == "null" }
    val sourceItemIdValue = sourceItemId?.toString()?.trim().orEmpty().takeUnless { it.isEmpty() || it == "null" }
    val targetIdValue = targetId?.toString()?.trim().orEmpty().takeUnless { it.isEmpty() || it == "null" }
    val stableItemId = sourceItemIdValue ?: targetIdValue
    val isMovieContent = normalizedType in setOf("content", "movie", "vod")
    val unavailableReason = when {
        !isMovieContent -> "此记录不是影视内容，保持只读。"
        sourceIdValue == null -> "记录缺少内容源标识，无法安全定位详情。"
        stableItemId == null -> "记录缺少稳定条目标识，无法安全定位详情。"
        else -> null
    }
    val detailJson = if (unavailableReason == null) {
        json.put("sourceId", sourceIdValue)
            .put("sourceItemId", stableItemId)
            .put("contentId", targetIdValue ?: stableItemId)
            .put("targetType", "content")
            .put("title", title)
            .toString()
    } else null
    return HistoryPresentation(
        historyId, title, listOfNotNull(type, episode, time).joinToString(" · "), completed,
        detailJson, unavailableReason,
    )
}

private fun PlaybackProgressRecord.toProgressPresentation(): ProgressPresentation {
    val title = contentId?.toString()?.takeIf(String::isNotBlank) ?: "未命名内容"
    val position = positionSeconds.toLong().coerceAtLeast(0L)
    val duration = durationSeconds?.toLong()?.takeIf { it > 0L }
    val fraction = if (completed) 1f else duration?.let { (position.toFloat() / it).coerceIn(0f, 1f) }
    val progressLabel = if (completed) "已完成" else if (duration != null) "${position.formatDuration()} / ${duration.formatDuration()}" else "已观看 ${position.formatDuration()}"
    val episode = episodeId.takeIf(String::isNotBlank)?.let { "剧集：$it" }
    val time = updatedAt.formatHistoryTime()
    return ProgressPresentation(progressId, title, listOfNotNull(episode, progressLabel, time).joinToString(" · "), fraction, completed)
}

@Composable
private fun ProgressCard(item: ProgressPresentation) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.fraction?.let { fraction -> LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryPresentation, onOpenMovieDetail: ((String) -> Unit)?) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.completed) Text("已完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (item.detailNavigationJson != null && onOpenMovieDetail != null) {
                OutlinedButton(onClick = { onOpenMovieDetail(item.detailNavigationJson) }) {
                    Text("查看影视详情")
                }
            } else if (item.detailUnavailableReason != null) {
                Text(item.detailUnavailableReason, style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
        }
    }
}

@Composable
private fun HistoryStatusCard(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MyBoxSurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun Long.formatHistoryTime(): String = if (this <= 0L) "时间未知" else runCatching {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(Date(this))
}.getOrDefault("时间未知")

private fun Long.formatDuration(): String {
    val seconds = coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining) else "%d:%02d".format(minutes, remaining)
}

private fun friendlyHistoryError(raw: String): String = when {
    raw.contains("NATIVE_SERVICES_UNAVAILABLE") -> "原生服务初始化失败，请稍后重试。"
    raw.contains("USER_DATA_RECORD_INVALID") -> "播放记录存在无法识别的数据；原始数据未被自动清空。"
    else -> raw.take(180)
}

private fun playbackHistoryViewModelFactory(application: MyBoxApplication): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaybackHistoryViewModel::class.java)) return PlaybackHistoryViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
