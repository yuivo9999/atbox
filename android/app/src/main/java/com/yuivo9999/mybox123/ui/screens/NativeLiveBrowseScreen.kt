package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.LocalAppAppearance
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary
import org.json.JSONObject

/** Native live source/channel browser with the user-authorized Media3 playback route. */
@Composable
fun NativeLiveBrowseScreen(
    modifier: Modifier = Modifier,
    favoriteNavigationJson: String? = null,
    onFavoriteNavigationConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) { liveBrowseViewModelFactory(application) }
    val vm: LiveBrowseViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val appearance = LocalAppAppearance.current

    LaunchedEffect(vm) {
        vm.loadSources()
        vm.loadFavoriteIds()
    }
    LaunchedEffect(vm, favoriteNavigationJson, state.sourcesLoaded) {
        val pending = favoriteNavigationJson
        if (pending != null && state.sourcesLoaded) {
            vm.openFavoriteChannel(pending)
            onFavoriteNavigationConsumed()
        }
    }

    val selectedSource = state.sources.firstOrNull { it.sourceId == state.selectedSourceId }
    val categories = remember(state.channels) {
        listOf(LiveBrowseViewModel.ALL_CATEGORIES) + state.channels.map { it.optString("category").trim().ifBlank { "未分类" } }.distinct()
    }
    val filtered = remember(state.channels, state.keyword, state.selectedCategory) {
        val keyword = state.keyword.trim().lowercase()
        state.channels.filter { channel ->
            val category = channel.optString("category").trim().ifBlank { "未分类" }
            (state.selectedCategory == LiveBrowseViewModel.ALL_CATEGORIES || category == state.selectedCategory) &&
                (keyword.isBlank() || channel.optString("name").lowercase().contains(keyword) ||
                    category.lowercase().contains(keyword) || channel.optString("description").lowercase().contains(keyword))
        }
    }
    val grouped = remember(filtered, state.selectedCategory) {
        if (state.selectedCategory == LiveBrowseViewModel.ALL_CATEGORIES) filtered.groupBy { it.optString("category").trim().ifBlank { "未分类" } }
        else mapOf(state.selectedCategory to filtered)
    }

    val playbackChannel = state.playbackChannel
    if (playbackChannel != null) {
        val selectedStream = state.playbackStreams.firstOrNull { it.optString("streamId") == state.playbackSelectedStreamId }
        NativeLivePlayerScreen(
            channel = playbackChannel,
            streams = state.playbackStreams,
            selectedStream = selectedStream,
            loading = state.playbackLoading,
            error = state.playbackError,
            onSelectStream = vm::selectPlaybackStream,
            onBack = vm::closePlayback,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("直播频道", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("原生频道浏览 · Media3 播放", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
            OutlinedButton(onClick = { vm.loadSources(force = true) }, enabled = !state.sourcesLoading) { Text("刷新源") }
        }

        Text("选择直播源", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (state.sourcesLoading && state.sources.isEmpty()) {
            CircularProgressIndicator()
        } else if (state.sources.isEmpty()) {
            StatusCard(if (state.sourceError != null) "读取直播源失败：${state.sourceError}" else "暂无直播源。请先在“我的 - 内容源”添加并启用直播源。")
            if (state.sourceError != null) Button(onClick = { vm.loadSources(force = true) }, enabled = !state.sourcesLoading) { Text("重试") }
        } else {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.sources.forEach { source ->
                    if (source.sourceId == state.selectedSourceId) Button(onClick = {}, enabled = false) { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    else OutlinedButton(onClick = { vm.selectSource(source.sourceId) }) {
                        Text(source.name + if (source.enabled) "" else "（停用）", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            selectedSource?.let { source ->
                Text("${source.name} · ${if (source.enabled) "已启用" else "已停用"} · ${source.status}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.refreshChannels() }, enabled = !state.channelsLoading && selectedSource?.enabled == true) {
                    Text(if (state.channelsLoading) "正在读取…" else if (state.channels.isEmpty()) "加载频道" else "刷新频道")
                }
                Text("${state.channels.size} 个频道", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
        }

        state.sourceError?.let { StatusCard("直播源列表读取失败：$it") }
        state.channelError?.let { StatusCard("频道读取失败：$it") }
        state.message?.let { StatusCard(it) }
        state.navigationMessage?.let { StatusCard(it) }

        if (state.channels.isNotEmpty()) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = vm::updateKeyword,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索频道或分组") },
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { category ->
                    if (category == state.selectedCategory) Button(onClick = {}, enabled = false) { Text(category) }
                    else OutlinedButton(onClick = { vm.selectCategory(category) }) { Text(category) }
                }
            }
            if (filtered.isEmpty()) StatusCard("当前条件下没有匹配频道。")
            else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp)) {
                grouped.forEach { (category, channels) ->
                    if (state.selectedCategory == LiveBrowseViewModel.ALL_CATEGORIES) {
                        item(key = "group:$category") {
                            Text("$category · ${channels.size}", modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(channels, key = { it.optString("sourceChannelId").ifBlank { it.optString("sourceItemId") + ":" + it.optString("name") } }) { channel ->
                        LiveChannelCard(
                            channel = channel,
                            favorite = vm.isFavorite(channel),
                            busy = state.mutatingFavoriteId == channel.optString("sourceChannelId"),
                            compact = appearance.compact,
                            listStyle = appearance.listCards,
                            onFavorite = { vm.addFavorite(channel) },
                            onPlay = { vm.openPlayback(channel) },
                        )
                    }
                }
            }
        } else if (!state.channelsLoading && state.sources.isNotEmpty()) {
            StatusCard("选择一个已启用的直播源，然后点击“加载频道”。只有点选频道的“播放”按钮后才会解析线路并启动播放器。")
        }
    }
}

@Composable
private fun LiveChannelCard(channel: JSONObject, favorite: Boolean, busy: Boolean, compact: Boolean, listStyle: Boolean, onFavorite: () -> Unit, onPlay: () -> Unit) {
    val name = channel.optString("name").ifBlank { "未命名频道" }
    val category = channel.optString("category").ifBlank { "未分类" }
    val streamCount = channel.optJSONArray("streams")?.length() ?: if (channel.optJSONObject("stream") != null) 1 else 0
    Card(shape = RoundedCornerShape(if (listStyle) 8.dp else 16.dp), colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(if (compact) 9.dp else 13.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$category · ${if (streamCount > 0) "$streamCount 条线路描述" else "线路信息待解析"}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                val description = channel.optString("description").trim()
                if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("支持原生播放", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onPlay) { Text("播放") }
                OutlinedButton(onClick = onFavorite, enabled = !busy && !favorite) { Text(when { busy -> "保存中"; favorite -> "已收藏"; else -> "收藏" }) }
            }
        }
    }
}

@Composable
fun StatusCard(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MyBoxSurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(13.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun liveBrowseViewModelFactory(application: MyBoxApplication): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LiveBrowseViewModel::class.java)) return LiveBrowseViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
