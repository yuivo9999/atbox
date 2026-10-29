package com.yuivo9999.mybox123.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.storage.SearchRecord
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.LocalAppAppearance
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeMovieCatalogScreen(
    modifier: Modifier = Modifier,
    favoriteNavigationJson: String? = null,
    onFavoriteNavigationConsumed: () -> Unit = {},
    pendingSearchKeyword: String? = null,
    onSearchNavigationConsumed: () -> Unit = {},
    returnToHomeAfterDetail: Boolean = false,
    onReturnToHomeAfterDetail: () -> Unit = {},
    onPlayMovie: (NativeMovieItem) -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as MyBoxApplication
    val factory = remember(application) { movieCatalogViewModelFactory(application) }
    val viewModel: MovieCatalogViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var confirmClearSearchHistory by rememberSaveable { mutableStateOf(false) }
    val appearance = LocalAppAppearance.current

    // Consume system Back for in-screen routes before the root shell returns to HOME.
    BackHandler(enabled = state.detailOpen || showHistory) {
        if (state.detailOpen) {
            if (returnToHomeAfterDetail) {
                viewModel.closeDetail()
                onReturnToHomeAfterDetail()
            } else viewModel.closeDetail()
        } else if (showHistory) {
            showHistory = false
        }
    }

    LaunchedEffect(viewModel) { viewModel.loadSources() }
    LaunchedEffect(viewModel, pendingSearchKeyword) {
        pendingSearchKeyword?.let { rawKeyword ->
            rawKeyword.trim().takeIf { it.isNotEmpty() }?.let(viewModel::searchFromNavigation)
            // A non-null one-shot intent is consumed even if it normalizes to blank.
            onSearchNavigationConsumed()
        }
    }
    LaunchedEffect(viewModel, showHistory) {
        if (showHistory) viewModel.loadSearchHistory()
    }
    LaunchedEffect(viewModel, favoriteNavigationJson, state.sourcesLoaded) {
        val pending = favoriteNavigationJson
        if (pending != null && state.sourcesLoaded) {
            viewModel.openFavoriteJson(pending)
            onFavoriteNavigationConsumed()
        }
    }
    if (confirmClearSearchHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearSearchHistory = false },
            title = { Text("清空搜索历史？") },
            text = { Text("此操作只删除本应用保存的影视搜索关键词历史，不会删除影视源、收藏、播放历史或其他用户数据。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearSearchHistory = false
                    viewModel.clearSearchHistory()
                }) { Text("清空历史") }
            },
            dismissButton = { TextButton(onClick = { confirmClearSearchHistory = false }) { Text("取消") } },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("影视目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("原生内容源 · 非播放页面", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    if (state.detailOpen) {
                        viewModel.closeDetail()
                        if (returnToHomeAfterDetail) onReturnToHomeAfterDetail()
                    } else {
                        showHistory = !showHistory
                    }
                }) { Text(if (state.detailOpen) "返回目录" else if (showHistory) "返回目录" else "搜索历史") }
                if (!state.detailOpen) OutlinedButton(onClick = { viewModel.refresh() }, enabled = !state.loading && state.selectedSourceId != null) { Text("刷新") }
            }
        }

        state.navigationError?.let { error -> StatusCard(error) }

        if (state.detailOpen) {
            MovieDetailPanel(
                movie = state.detailMovie,
                sourceName = state.sources.firstOrNull { it.sourceId == state.selectedSourceId }?.name.orEmpty(),
                loading = state.detailLoading,
                error = state.detailError,
                favoriteSaving = state.detailFavoriteSaving,
                favoriteSaved = state.detailFavoriteSaved,
                onFavorite = viewModel::addDetailToFavorites,
                modifier = Modifier.weight(1f),
            )
        } else {
        if (state.sources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("当前内容源", style = MaterialTheme.typography.labelMedium, color = MyBoxTextSecondary)
                OutlinedButton(onClick = { sourceMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(state.sources.firstOrNull { it.sourceId == state.selectedSourceId }?.name ?: "选择内容源", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = sourceMenuExpanded, onDismissRequest = { sourceMenuExpanded = false }) {
                    state.sources.forEach { source ->
                        DropdownMenuItem(text = { Text(source.name) }, onClick = {
                            sourceMenuExpanded = false
                            viewModel.selectSource(source.sourceId)
                        })
                    }
                }
            }
        }

        if (!showHistory && !state.detailOpen && (state.categories.isNotEmpty() || state.categoriesLoading || state.categoryError != null)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("影视分类", style = MaterialTheme.typography.labelMedium, color = MyBoxTextSecondary)
                OutlinedButton(
                    onClick = { categoryMenuExpanded = true },
                    enabled = !state.categoriesLoading && state.categories.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val selectedName = state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name ?: "全部分类"
                    Text(if (state.categoriesLoading) "正在读取分类…" else selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("全部分类") }, onClick = {
                        categoryMenuExpanded = false
                        viewModel.selectCategory(null)
                    })
                    state.categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name) }, onClick = {
                            categoryMenuExpanded = false
                            viewModel.selectCategory(category.id)
                        })
                    }
                }
                state.categoryError?.let { error ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("分类不可用：${friendlyMovieError(error)}。仍可使用全部目录和搜索。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::retryCategories, enabled = !state.categoriesLoading) { Text("重试") }
                    }
                }
            }
        }

        if (!showHistory) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::updateKeyword,
                modifier = Modifier.weight(1f),
                label = { Text("搜索影视") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            )
            Button(onClick = { viewModel.search() }, enabled = !state.loading && state.selectedSourceId != null) { Text("搜索") }
        }

        if (showHistory) {
            SearchHistoryPanel(
                records = state.searchHistory,
                loading = state.searchHistoryLoading,
                error = state.searchHistoryError,
                onRetry = viewModel::loadSearchHistory,
                onUse = { keyword -> showHistory = false; viewModel.useSearchKeyword(keyword) },
                onDelete = viewModel::deleteSearchRecord,
                onClearAll = { confirmClearSearchHistory = true },
                modifier = Modifier.weight(1f),
            )
        } else {
        Text("已加载 ${state.items.size} 条 · 源端总数 ${state.total}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
        state.error?.let { error ->
            InfoCard("加载失败：${friendlyMovieError(error)}")
            Button(onClick = { viewModel.refresh() }, enabled = !state.loading) { Text("重试") }
        }

        when {
            state.sourcesLoading || (state.loading && state.items.isEmpty()) -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text(if (state.sourcesLoading) "正在读取可用影视源…" else "正在读取影视目录…", color = MyBoxTextSecondary)
                }
            }
            state.sourcesLoaded && state.sources.isEmpty() -> InfoCard("没有已启用的影视源。请到“我的”页面管理或导入内容源。")
            state.items.isEmpty() && state.error == null && !state.loading -> InfoCard(if (state.keyword.isBlank()) "当前内容源没有返回影视条目。" else "没有找到匹配“${state.keyword}”的影视条目。")
            else -> {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp)) {
                    items(state.items, key = { it.sourceId + ":" + it.sourceItemId.ifBlank { it.contentId } }) { movie -> MovieCard(movie, onClick = { viewModel.openDetail(movie) }, onPlay = { onPlayMovie(movie) }) }
                    if (state.items.size.toLong() < state.total) {
                        item {
                            OutlinedButton(onClick = { viewModel.loadNextPage() }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.loading) "正在加载…" else "加载更多")
                            }
                        }
                    }
                }
            }
        }
        }
        }
        Text(
            when {
                state.detailOpen -> "详情页仅展示元数据、来源与剧集信息；选择剧集不会启动播放。"
                showHistory -> "搜索历史最多保留 50 个不同关键词；支持重新搜索、单条删除和确认后清空。"
                else -> "点击影视条目查看详情。播放入口仍属于后续阶段；部分旧适配器受阶段二能力矩阵限制。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MyBoxTextSecondary,
        )
    }
}

@Composable
private fun SearchHistoryPanel(
    records: List<SearchRecord>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onUse: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("搜索历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onClearAll, enabled = records.isNotEmpty() && !loading) { Text("清空全部") }
        }
        error?.let {
            InfoCard("搜索历史读取/维护失败：$it")
            OutlinedButton(onClick = onRetry, enabled = !loading) { Text("重试") }
        }
        when {
            loading && records.isEmpty() -> CircularProgressIndicator()
            records.isEmpty() && error == null -> InfoCard("暂无搜索历史。执行一次影视搜索后会自动记录关键词。")
            else -> LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.searchId }) { record ->
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(record.keyword, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("搜索 ${record.count.coerceAtLeast(1)} 次 · ${record.searchedAt.takeIf { it > 0L }?.let(::formatSearchTimestamp) ?: "时间未知"}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onUse(record.keyword) }, enabled = !loading) { Text("重新搜索") }
                                OutlinedButton(onClick = { onDelete(record.searchId) }, enabled = !loading) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSearchTimestamp(timestamp: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

@Composable
private fun MovieCard(movie: NativeMovieItem, onClick: () -> Unit, onPlay: () -> Unit = {}) {
    val appearance = LocalAppAppearance.current
    Card(shape = RoundedCornerShape(if (appearance.listCards) 8.dp else 16.dp), colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(appearance.cardPadding.dp), verticalArrangement = Arrangement.spacedBy(if (appearance.compact) 4.dp else 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (appearance.compact) 4.dp else 6.dp)) {
                    Text(movie.title.ifBlank { "未命名影视" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (movie.category.isNotBlank()) Text(movie.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (movie.description.isNotBlank()) Text(movie.description, style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, maxLines = if (appearance.listCards) 1 else 3, overflow = TextOverflow.Ellipsis)
                    Text("${movie.episodes.size} 项剧集信息 · 点击查看详情", style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary)
                }
                if (onPlay != {}) {
                    Button(onClick = onPlay, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("播放")
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieDetailPanel(
    movie: NativeMovieItem?,
    sourceName: String,
    loading: Boolean,
    error: String?,
    favoriteSaving: Boolean,
    favoriteSaved: Boolean,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (movie == null) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            else InfoCard("没有可展示的影视详情。请返回目录后重试。")
            error?.let { InfoCard("详情读取提示：${friendlyMovieError(it)}") }
        }
        return
    }
    val json = movie.toJson()
    val metadata = listOf(
        "年份" to listOf("year", "vod_year"),
        "地区" to listOf("area", "vod_area"),
        "导演" to listOf("director", "vod_director"),
        "演员" to listOf("actor", "vod_actor"),
        "语言" to listOf("language", "vod_lang"),
        "更新时间" to listOf("updateTime", "vod_time", "lastUpdated"),
        "评分" to listOf("score", "vod_score"),
    ).mapNotNull { (label, keys) ->
        val value = keys.firstNotNullOfOrNull { key -> json.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" } }
        value?.let { label to it }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text("正在读取完整详情…", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
        }
        error?.let { InfoCard("详情读取提示：${friendlyMovieError(it)}。当前仍显示目录中已有的信息。") }
        Text(movie.title.ifBlank { "未命名影视" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (movie.category.isNotBlank()) Text(movie.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("内容源：${sourceName.ifBlank { movie.sourceId }}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
        Text("来源条目 ID：${movie.sourceItemId.ifBlank { "未知" }}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
        if (metadata.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                metadata.forEach { (label, value) -> Text("$label：$value", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        val description = movie.description.ifBlank {
            listOf("content", "vod_content", "summary").firstNotNullOfOrNull { json.optString(it).trim().takeIf(String::isNotEmpty) }.orEmpty()
        }
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
        val poster = movie.poster.ifBlank { json.optString("vod_pic") }.trim()
        if (poster.isNotBlank()) {
            NativePosterImage(url = poster)
            Text("海报来源地址：$poster", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onFavorite, enabled = !favoriteSaving && !favoriteSaved) {
                Text(when { favoriteSaving -> "正在保存…"; favoriteSaved -> "已加入收藏"; else -> "加入收藏" })
            }
            Text("剧集 ${movie.episodes.size} 项", style = MaterialTheme.typography.labelMedium, color = MyBoxTextSecondary)
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (movie.episodes.isEmpty()) {
                item { InfoCard("该条目暂未提供可解析的剧集列表。") }
            } else {
                items(movie.episodes.size) { index ->
                    val episode = movie.episodes[index]
                    val title = listOf("title", "episodeTitle", "name", "label").firstNotNullOfOrNull { key ->
                        episode.optString(key).trim().takeIf(String::isNotEmpty)
                    } ?: "第${index + 1}集"
                    val candidates = episode.optJSONArray("playbackCandidates")?.length() ?: 0
                    Surface(shape = RoundedCornerShape(10.dp), color = MyBoxSurfaceRaised, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${index + 1}. $title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(if (candidates > 0) "已解析线路描述：$candidates 条" else "剧集元数据 · 未启动播放", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MyBoxSurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(15.dp), style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
    }
}

private fun friendlyMovieError(error: String): String = when {
    error.contains("SOURCE_DISABLED") -> "所选内容源已停用，请重新选择或启用该源。"
    error.contains("SOURCE_TYPE_NOT_MOVIE") -> "该内容源不是影视源。"
    error.contains("ENDPOINT_INVALID") -> "内容源地址无效。"
    error.contains("UNSUPPORTED") || error.contains("RUNTIME_REQUIRED") -> "该内容源需要尚未完整迁移的扩展能力。"
    error.contains("HTTP") -> "内容源请求失败，请检查地址、网络或服务端状态。"
    else -> error
}

private fun movieCatalogViewModelFactory(application: MyBoxApplication): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MovieCatalogViewModel::class.java)) {
                return MovieCatalogViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

@Composable
private fun StatusCard(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MyBoxSurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(13.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

