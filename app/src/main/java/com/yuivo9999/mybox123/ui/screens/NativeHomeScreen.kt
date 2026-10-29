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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.app.AppDestination
import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.ui.theme.MyBoxAmber
import com.yuivo9999.mybox123.ui.theme.MyBoxBackground
import com.yuivo9999.mybox123.ui.theme.MyBoxBorder
import com.yuivo9999.mybox123.ui.theme.MyBoxGold
import com.yuivo9999.mybox123.ui.theme.MyBoxGoldLight
import com.yuivo9999.mybox123.ui.theme.MyBoxOutline
import com.yuivo9999.mybox123.ui.theme.MyBoxSurface
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextMuted
import com.yuivo9999.mybox123.ui.theme.MyBoxTextPrimary
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

/**
 * 首页：对标前端 HomePage.jsx，纯原生 Jetpack Compose 构建
 * 包含：精选顶部栏、焦点 Hero Banner、继续观看、内容专区快速筛选、热门精选 3 列海报网格与各分类横向画廊
 */
@Composable
fun NativeHomeScreen(
    onNavigate: (AppDestination) -> Unit,
    onSearchMovies: (String) -> Unit = {},
    onOpenHistoryMovieDetail: (String) -> Unit = {},
    onOpenContentPreviewDetail: (String) -> Unit = {},
    onPlayMovie: (NativeMovieItem) -> Unit = {},
    initialShowHistory: Boolean = false,
    onHistoryDismissed: () -> Unit = {},
) {
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
    val state by previewViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        previewViewModel.load(force = true)
    }

    var sourceMenuExpanded by remember { mutableStateOf(false) }

    val allMovies = state.allMovies
    val heroMovie = allMovies.firstOrNull()

    // Sub-category lists for carousels
    val movieSectionList = remember(allMovies) { allMovies.filter { it.category == "电影" } }
    val seriesSectionList = remember(allMovies) { allMovies.filter { it.category == "电视剧" } }
    val animeSectionList = remember(allMovies) { allMovies.filter { it.category == "动漫" } }
    val varietySectionList = remember(allMovies) { allMovies.filter { it.category == "综艺" } }
    val shortDramaList = remember(allMovies) { allMovies.filter { it.category == "短剧" } }

    val activeCategory = state.selectedCategoryId
    val filteredMovies = remember(allMovies, activeCategory) {
        if (activeCategory == "all") allMovies
        else allMovies.filter {
            val cat = when (activeCategory) {
                "movie" -> "电影"
                "series" -> "电视剧"
                "anime" -> "动漫"
                "variety" -> "综艺"
                "short_drama" -> "短剧"
                else -> activeCategory
            }
            it.category == cat || it.category.contains(cat)
        }.ifEmpty { allMovies }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MyBoxBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 精致顶部栏：左侧标题与来源，右侧快速切源胶囊与搜索
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MyBoxGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "TVBOX 4K 影音",
                            style = MaterialTheme.typography.labelSmall,
                            color = MyBoxTextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "精选首页",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MyBoxTextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // MovieSourcePill 切源胶囊
                    Box {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MyBoxSurfaceRaised,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxOutline),
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
                                    text = state.sourceName?.take(6) ?: "内置源",
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
                                        previewViewModel.selectSource(source.sourceId)
                                        sourceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 全源搜索按钮
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MyBoxSurfaceRaised,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxOutline),
                        modifier = Modifier.clickable { onNavigate(AppDestination.MOVIES) }
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MyBoxTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. 焦点精选 Hero Banner
        if (heroMovie != null) {
            item {
                HomeHeroBanner(
                    movie = heroMovie,
                    onPlay = { onPlayMovie(heroMovie) },
                    onDetail = { onOpenContentPreviewDetail(heroMovie.rawJson) }
                )
            }
        }

        // 3. 继续观看 Row
        if (state.continueWatching.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "继续观看",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MyBoxTextPrimary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(state.continueWatching) { item ->
                            ContinueWatchingCard(
                                item = item,
                                onClick = { onPlayMovie(item.movie) }
                            )
                        }
                    }
                }
            }
        }

        // 4. 分类快捷筛选栏 (内容专区)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "内容专区",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MyBoxTextPrimary
                    )
                    Text(
                        text = "影视库全览 >",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyBoxGold,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onNavigate(AppDestination.MOVIES) }
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(state.categories) { category ->
                        val isSelected = activeCategory == category.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFEEF1F5) else MyBoxSurfaceRaised,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                            modifier = Modifier.clickable { previewViewModel.selectCategory(category.id) }
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF101217) else MyBoxTextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. 热门精选 3 列海报网格 (6张卡片)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 热门精选",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MyBoxTextPrimary
                    )
                    Text(
                        text = "${filteredMovies.size} 部内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyBoxTextMuted
                    )
                }

                MovieThreeColumnGrid(
                    movies = filteredMovies.take(6),
                    onMovieClick = { onOpenContentPreviewDetail(it.rawJson) }
                )
            }
        }

        // 6. 🎬 院线与高清电影 横向画廊
        if (movieSectionList.isNotEmpty()) {
            item {
                MovieSectionRow(
                    title = "🎬 院线与高清电影",
                    actionLabel = "查看全部电影 >",
                    movies = movieSectionList.take(10),
                    onAction = { onNavigate(AppDestination.MOVIES) },
                    onMovieClick = { onOpenContentPreviewDetail(it.rawJson) }
                )
            }
        }

        // 7. 📺 同步热播剧集 横向画廊
        if (seriesSectionList.isNotEmpty()) {
            item {
                MovieSectionRow(
                    title = "📺 同步热播剧集",
                    actionLabel = "查看全部剧集 >",
                    movies = seriesSectionList.take(10),
                    onAction = { onNavigate(AppDestination.MOVIES) },
                    onMovieClick = { onOpenContentPreviewDetail(it.rawJson) }
                )
            }
        }

        // 8. 🎨 热血与国创动漫 横向画廊
        if (animeSectionList.isNotEmpty()) {
            item {
                MovieSectionRow(
                    title = "🎨 热血与国创动漫",
                    actionLabel = "查看全部动漫 >",
                    movies = animeSectionList.take(10),
                    onAction = { onNavigate(AppDestination.MOVIES) },
                    onMovieClick = { onOpenContentPreviewDetail(it.rawJson) }
                )
            }
        }

        // 9. 🎤 欢笑综艺娱乐
        if (varietySectionList.isNotEmpty()) {
            item {
                MovieSectionRow(
                    title = "🎤 欢笑综艺娱乐",
                    actionLabel = "查看全部综艺 >",
                    movies = varietySectionList.take(10),
                    onAction = { onNavigate(AppDestination.MOVIES) },
                    onMovieClick = { onOpenContentPreviewDetail(it.rawJson) }
                )
            }
        }

        // 10. ⚡ 爆款热门短剧
        if (shortDramaList.isNotEmpty()) {
            item {
                MovieSectionRow(
                    title = "⚡ 爆款热门短剧",
                    actionLabel = "查看全部短剧 >",
                    movies = shortDramaList.take(10),
                    onAction = { onNavigate(AppDestination.MOVIES) },
                    onMovieClick = { onOpenContentPreviewDetail(it.rawJson) }
                )
            }
        }

        // 11. 更多内容推荐 (剩余网格)
        if (filteredMovies.size > 6) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "✨ 更多内容推荐",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MyBoxTextPrimary
                    )

                    MovieThreeColumnGrid(
                        movies = filteredMovies.drop(6),
                        onMovieClick = { onOpenContentPreviewDetail(it.rawJson) }
                    )
                }
            }
        }
    }
}

/** 焦点 Banner 推荐卡片 */
@Composable
fun HomeHeroBanner(
    movie: NativeMovieItem,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MyBoxSurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
            // 背景海报/壁纸
            val imgUrl = movie.toJson().optString("backdrop").ifBlank { movie.poster }
            NativePosterImage(
                url = imgUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )

            // 渐变蒙层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x9907080B),
                                Color(0xF207080B)
                            )
                        )
                    )
            )

            // 焦点文字与按钮
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("● 今日焦点", style = MaterialTheme.typography.labelSmall, color = MyBoxGold, fontWeight = FontWeight.Bold)
                    Text("·", style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary)
                    Text(movie.category.ifBlank { "4K超清" }, style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary)
                    if (movie.description.isNotBlank()) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MyBoxTextSecondary)
                        Text("4K超清国语", style = MaterialTheme.typography.labelSmall, color = MyBoxGoldLight)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (movie.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = movie.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MyBoxTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF2F4F8),
                            contentColor = Color(0xFF101217)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("立即播放", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onDetail,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("查看详情", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** 继续观看横向滑动卡片 */
@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MyBoxSurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
        modifier = modifier
            .width(200.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            NativePosterImage(
                url = item.movie.poster,
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.movie.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MyBoxTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.episodeTitle} · ${item.positionMinutes} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MyBoxTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 3列网格海报展示 */
@Composable
fun MovieThreeColumnGrid(
    movies: List<NativeMovieItem>,
    onMovieClick: (NativeMovieItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val chunked = remember(movies) { movies.chunked(3) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        chunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { movie ->
                    Box(modifier = Modifier.weight(1f)) {
                        HomePosterCard(movie = movie, onClick = { onMovieClick(movie) })
                    }
                }
                // Fill empty slots if last row has less than 3
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 横向滚动电影行 */
@Composable
fun MovieSectionRow(
    title: String,
    actionLabel: String,
    movies: List<NativeMovieItem>,
    onAction: () -> Unit,
    onMovieClick: (NativeMovieItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MyBoxTextPrimary
            )
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MyBoxGold,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(movies) { movie ->
                Box(modifier = Modifier.width(115.dp)) {
                    HomePosterCard(movie = movie, onClick = { onMovieClick(movie) })
                }
            }
        }
    }
}

/** 影视海报卡片组件 (对标前端 MovieCard.jsx) */
@Composable
fun HomePosterCard(
    movie: NativeMovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MyBoxSurfaceRaised)
                .border(1.dp, MyBoxBorder, RoundedCornerShape(12.dp))
        ) {
            NativePosterImage(
                url = movie.poster,
                modifier = Modifier.fillMaxSize()
            )

            // 底部暗色渐变
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1.0f to Color(0xCC07080B)
                        )
                    )
            )

            // 左上角品质标
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MyBoxGold, MyBoxAmber)
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "4K",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0B0D12)
                )
            }

            // 右下角集数/清晰度说明
            val remarks = movie.toJson().optString("updateInfo").ifBlank { movie.category }
            if (remarks.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xD90C0F16))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = remarks,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = movie.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MyBoxTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = movie.category.ifBlank { "影视" },
            style = MaterialTheme.typography.labelSmall,
            color = MyBoxTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
