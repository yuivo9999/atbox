package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.ui.theme.MyBoxAmber
import com.yuivo9999.mybox123.ui.theme.MyBoxBackground
import com.yuivo9999.mybox123.ui.theme.MyBoxBorder
import com.yuivo9999.mybox123.ui.theme.MyBoxGold
import com.yuivo9999.mybox123.ui.theme.MyBoxGoldLight
import com.yuivo9999.mybox123.ui.theme.MyBoxHeart
import com.yuivo9999.mybox123.ui.theme.MyBoxOutline
import com.yuivo9999.mybox123.ui.theme.MyBoxSurface
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextMuted
import com.yuivo9999.mybox123.ui.theme.MyBoxTextPrimary
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary
import org.json.JSONObject

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

    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }

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
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
            .statusBarsPadding()
    ) {
        // 1. Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MyBoxGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "TVBOX 4K 影音",
                        style = MaterialTheme.typography.labelSmall,
                        color = MyBoxGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "直播中心",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MyBoxTextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Source Selector Pill
                Box {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MyBoxSurfaceRaised,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                        modifier = Modifier.clickable { sourceMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MyBoxGold)
                            )
                            Text(
                                text = selectedSource?.name?.take(6) ?: "内置央卫视",
                                style = MaterialTheme.typography.labelMedium,
                                color = MyBoxTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false }
                    ) {
                        state.sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.name) },
                                onClick = {
                                    sourceMenuExpanded = false
                                    vm.selectSource(source.sourceId)
                                }
                            )
                        }
                    }
                }

                // Search Icon
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (showSearchBar) MyBoxGold else MyBoxSurfaceRaised,
                    border = if (showSearchBar) null else androidx.compose.foundation.BorderStroke(1.dp, MyBoxOutline),
                    modifier = Modifier.clickable { showSearchBar = !showSearchBar }
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (showSearchBar) Color(0xFF101217) else MyBoxTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Search Input (if expanded)
        if (showSearchBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.keyword,
                    onValueChange = vm::updateKeyword,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索频道名称或节目…", color = MyBoxTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MyBoxSurfaceRaised,
                        unfocusedContainerColor = MyBoxSurfaceRaised,
                        focusedBorderColor = MyBoxGold,
                        unfocusedBorderColor = MyBoxBorder,
                        focusedTextColor = MyBoxTextPrimary,
                        unfocusedTextColor = MyBoxTextPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (state.keyword.isNotBlank()) {
                            IconButton(onClick = { vm.updateKeyword("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清空", tint = MyBoxTextMuted)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }
        }

        // 3. Category Horizontal Chip Bar
        if (categories.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == state.selectedCategory
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFFEEF1F5) else MyBoxSurfaceRaised,
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                        modifier = Modifier.clickable { vm.selectCategory(category) }
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF101217) else MyBoxTextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 4. Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filtered.size} 个频道",
                style = MaterialTheme.typography.bodySmall,
                color = MyBoxTextMuted
            )
            if (state.channelsLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = MyBoxGold)
                    Text("载入中…", style = MaterialTheme.typography.bodySmall, color = MyBoxGold)
                }
            }
        }

        // 5. Channel List
        when {
            state.channelsLoading && state.channels.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = MyBoxGold)
                        Text("正在读取直播频道…", color = MyBoxTextSecondary, fontSize = 13.sp)
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = MyBoxTextMuted, modifier = Modifier.size(36.dp))
                        Text(
                            if (state.keyword.isBlank()) "暂无直播频道" else "未找到与“${state.keyword}”相关的频道",
                            color = MyBoxTextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (cat, chList) ->
                        if (state.selectedCategory == LiveBrowseViewModel.ALL_CATEGORIES) {
                            item(key = "hdr:$cat") {
                                Text(
                                    text = "$cat (${chList.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MyBoxGold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                        }

                        items(chList, key = { it.optString("sourceChannelId").ifBlank { it.optString("channelId") } }) { channel ->
                            SangtianChannelCard(
                                channel = channel,
                                favorite = vm.isFavorite(channel),
                                onPlay = { vm.openPlayback(channel) },
                                onToggleFavorite = { vm.addFavorite(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Channel Card matching LiveFeature.jsx Sangtian styling */
@Composable
private fun SangtianChannelCard(
    channel: JSONObject,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val name = channel.optString("name").ifBlank { "未命名频道" }
    val category = channel.optString("category").ifBlank { "综合" }
    val program = channel.optString("currentProgram").ifBlank { channel.optString("description") }.ifBlank { "高清电视直播" }
    val streamCount = channel.optJSONArray("streams")?.length() ?: 1

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MyBoxSurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel Brand Icon / Logo
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (name.contains("CCTV", ignoreCase = true)) Color(0xFFC026D3).copy(alpha = 0.2f)
                        else Color(0xFFF59E0B).copy(alpha = 0.2f)
                    )
                    .border(
                        1.dp,
                        if (name.contains("CCTV", ignoreCase = true)) Color(0xFFC026D3).copy(alpha = 0.5f)
                        else MyBoxGold.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (name.contains("CCTV", ignoreCase = true)) "CCTV" else category.take(2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (name.contains("CCTV", ignoreCase = true)) Color(0xFFE879F9) else MyBoxGold
                )
            }

            // Channel Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MyBoxTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("直播中", fontSize = 9.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Medium)
                    }
                }
                Text(
                    text = program,
                    style = MaterialTheme.typography.bodySmall,
                    color = MyBoxTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$category · $streamCount 条线路",
                    style = MaterialTheme.typography.labelSmall,
                    color = MyBoxTextMuted
                )
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (favorite) MyBoxHeart else MyBoxTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = MyBoxGold, contentColor = Color(0xFF101217)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("播放", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun liveBrowseViewModelFactory(application: MyBoxApplication): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LiveBrowseViewModel(application) as T
        }
    }
