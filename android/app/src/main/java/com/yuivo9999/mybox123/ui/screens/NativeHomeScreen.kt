package com.yuivo9999.mybox123.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yuivo9999.mybox123.app.AppDestination
import com.yuivo9999.mybox123.ui.theme.LocalAppAppearance
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

/** Native content-entry hub. It intentionally offers navigation, not fabricated recommendations. */
@Composable
fun NativeHomeScreen(
    onNavigate: (AppDestination) -> Unit,
    onSearchMovies: (String) -> Unit = {},
    onOpenHistoryMovieDetail: (String) -> Unit = {},
    onOpenContentPreviewDetail: (String) -> Unit = {},
    initialShowHistory: Boolean = false,
    onHistoryDismissed: () -> Unit = {},
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var movieSearchKeyword by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(initialShowHistory) {
        // Root navigation can dispose/recreate this screen. Reconcile the explicit return intent
        // instead of trusting a previously saveable local flag to represent the current route.
        showHistory = initialShowHistory
    }
    BackHandler(enabled = showHistory) {
        showHistory = false
        onHistoryDismissed()
    }

    if (showHistory) {
        NativePlaybackHistoryScreen(
            onBack = {
                showHistory = false
                onHistoryDismissed()
            },
            onOpenMovieDetail = onOpenHistoryMovieDetail,
        )
        return
    }

    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                HomeContentPreviewViewModel(application) as T
        }
    }
    val previewViewModel: HomeContentPreviewViewModel = viewModel(factory = factory)
    val previewState by previewViewModel.uiState.collectAsStateWithLifecycle()
    // Re-read enabled sources whenever HOME is entered so PROFILE source edits are reflected.
    LaunchedEffect(previewViewModel) { previewViewModel.load(force = true) }

    val appearance = LocalAppAppearance.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("内容首页", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("选择一个内容入口继续浏览。本页只展示当前已接入的原生功能，不生成虚构推荐。", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("快速搜索", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("输入关键词后直接进入影视目录并执行现有搜索。", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
                    OutlinedTextField(
                        value = movieSearchKeyword,
                        onValueChange = { movieSearchKeyword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索影视内容") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            movieSearchKeyword.trim().takeIf { it.isNotEmpty() }?.let(onSearchMovies)
                        }),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { movieSearchKeyword.trim().takeIf { it.isNotEmpty() }?.let(onSearchMovies) },
                            enabled = movieSearchKeyword.isNotBlank(),
                        ) { Text("搜索") }
                    }
                }
            }
        }
        item {
            HomeContentPreviewCard(
                loading = previewState.loading,
                sourceName = previewState.sourceName,
                sources = previewState.sources,
                selectedSourceId = previewState.selectedSourceId,
                onSelectSource = previewViewModel::selectSource,
                items = previewState.items,
                error = previewState.error,
                onRetry = { previewViewModel.load(force = true) },
                onOpenItem = { item ->
                    val json = item.toJson()
                        .put("sourceId", item.sourceId)
                        .put("sourceItemId", item.sourceItemId.ifBlank { item.contentId })
                        .put("contentId", item.contentId.ifBlank { item.sourceItemId })
                        .put("targetType", "content")
                        .put("title", item.title)
                        .toString()
                    onOpenContentPreviewDetail(json)
                },
                onOpenCatalog = { onNavigate(AppDestination.MOVIES) },
                categories = previewState.categories,
                selectedCategoryId = previewState.selectedCategoryId,
                onSelectCategory = previewViewModel::selectCategory,
            )
        }
        item {
            HomeEntryCard(
                title = "影视目录",
                description = "浏览已启用的影视源，搜索内容、查看详情与剧集信息。点播播放仍属于后续阶段。",
                actionLabel = "打开影视",
                onClick = { onNavigate(AppDestination.MOVIES) },
            )
        }
        item {
            HomeEntryCard(
                title = "直播频道",
                description = "选择直播源，按分组或关键词浏览频道并管理收藏；可尝试通过原生播放器播放兼容线路，实际播放仍待设备验证。",
                actionLabel = "浏览直播",
                onClick = { onNavigate(AppDestination.LIVE) },
            )
        }
        item {
            HomeEntryCard(
                title = "我的收藏",
                description = "查看已保存的影视与直播频道，可打开对应详情或频道浏览。",
                actionLabel = "查看收藏",
                onClick = { onNavigate(AppDestination.FAVORITES) },
            )
        }
        item {
            HomeEntryCard(
                title = "最近播放与进度",
                description = "只读查看本机已有的历史和进度记录。自动续播与新的进度采集尚未接入。",
                actionLabel = "查看记录",
                onClick = { showHistory = true },
            )
        }
        item {
            HomeEntryCard(
                title = "我的与设置",
                description = "管理内容源、仓库定义、外观偏好和本机数据。",
                actionLabel = "打开管理",
                onClick = { onNavigate(AppDestination.PROFILE) },
            )
        }
        item {
            Text("首页说明：当前版本提供原生功能入口，不代表旧版推荐信息流已迁移。", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun HomeEntryCard(title: String, description: String, actionLabel: String, onClick: () -> Unit) {
    val appearance = LocalAppAppearance.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(appearance.cardPadding.dp),
            verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onClick) { Text(actionLabel) }
            }
        }
    }
}


@Composable
private fun HomeContentPreviewCard(
    loading: Boolean,
    sourceName: String?,
    sources: List<HomePreviewSource>,
    selectedSourceId: String?,
    onSelectSource: (String) -> Unit,
    items: List<NativeMovieItem>,
    error: String?,
    onRetry: () -> Unit,
    onOpenItem: (NativeMovieItem) -> Unit,
    onOpenCatalog: () -> Unit,
    categories: List<NativeMovieCategory> = emptyList(),
    selectedCategoryId: String? = null,
    onSelectCategory: (String?) -> Unit = {},
) {
    val appearance = LocalAppAppearance.current
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(appearance.cardPadding.dp),
            verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
        ) {
            Text("内容源目录预览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                sourceName?.let { "来自已启用内容源：$it。按该源返回顺序展示，不代表推荐或热度排名。" }
                    ?: "仅展示已启用影视源实际返回的内容，不生成演示影片或推荐排名。",
                style = MaterialTheme.typography.bodySmall,
                color = MyBoxTextSecondary,
            )
            if (sources.size > 1) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { sourceMenuExpanded = true }, enabled = !loading) {
                            Text("切换内容源")
                        }
                        DropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false },
                        ) {
                            sources.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(if (source.sourceId == selectedSourceId) "✓ ${source.name}" else source.name) },
                                    onClick = {
                                        sourceMenuExpanded = false
                                        onSelectSource(source.sourceId)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (categories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("影视分类", style = MaterialTheme.typography.labelMedium, color = MyBoxTextSecondary)
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        enabled = !loading && categories.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val selectedName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "全部分类"
                        Text(if (loading) "正在读取分类…" else selectedName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("全部分类") }, onClick = {
                            categoryMenuExpanded = false
                            onSelectCategory(null)
                        })
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = {
                                categoryMenuExpanded = false
                                onSelectCategory(category.id)
                            })
                        }
                    }
                }
            }
            when {
                loading -> CircularProgressIndicator()
                error != null -> {
                    Text("目录暂时无法加载：${error.take(140)}", style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary)
                    OutlinedButton(onClick = onRetry) { Text("重试加载") }
                }
                sourceName == null -> Text("尚无已启用的影视源。可在“我的 - 内容源”添加或启用内容源。", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
                items.isEmpty() -> Text("该内容源当前未返回目录条目。可进入影视目录查看或切换其他来源。", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
                else -> items.forEach { item ->
                    OutlinedButton(onClick = { onOpenItem(item) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            if (item.poster.isNotBlank()) {
                                NativePosterImage(url = item.poster, modifier = Modifier.width(54.dp).height(78.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.title.ifBlank { "未命名内容" }, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (item.category.isNotBlank()) Text(item.category, style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary, maxLines = 1)
                                Text("查看详情", style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary)
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onOpenCatalog) { Text("打开影视目录") }
            }
        }
    }
}
