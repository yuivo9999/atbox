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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.FavoriteRecord
import com.yuivo9999.mybox123.ui.theme.MyBoxBackground
import com.yuivo9999.mybox123.ui.theme.MyBoxBorder
import com.yuivo9999.mybox123.ui.theme.MyBoxGold
import com.yuivo9999.mybox123.ui.theme.MyBoxHeart
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextMuted
import com.yuivo9999.mybox123.ui.theme.MyBoxTextPrimary
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeFavoritesScreen(
    modifier: Modifier = Modifier,
    onOpenFavorite: (FavoriteRecord) -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) { favoritesViewModelFactory(application) }
    val favoritesViewModel: FavoritesViewModel = viewModel(factory = factory)
    val state by favoritesViewModel.uiState.collectAsStateWithLifecycle()

    var activeTab by rememberSaveable { mutableStateOf("movies") }

    LaunchedEffect(favoritesViewModel) { favoritesViewModel.load() }

    val presentations = remember(state.favorites) { state.favorites.map(FavoriteRecord::toPresentation) }
    val movieFavorites = remember(presentations) { presentations.filter { !it.isLive } }
    val liveFavorites = remember(presentations) { presentations.filter { it.isLive } }

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
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MyBoxHeart,
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
                    text = "我的收藏",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MyBoxTextPrimary
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MyBoxSurfaceRaised,
                border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                modifier = Modifier.clickable { favoritesViewModel.load(force = true) }
            ) {
                Text(
                    text = "刷新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MyBoxTextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 2. Segmented Bar: 影视 vs Live
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isMovies = activeTab == "movies"
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isMovies) Color(0xFFEEF1F5) else MyBoxSurfaceRaised,
                border = if (isMovies) null else androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeTab = "movies" }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "影视收藏 (${movieFavorites.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isMovies) FontWeight.Bold else FontWeight.Normal,
                        color = if (isMovies) Color(0xFF101217) else MyBoxTextSecondary
                    )
                }
            }

            val isLive = activeTab == "live"
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isLive) Color(0xFFEEF1F5) else MyBoxSurfaceRaised,
                border = if (isLive) null else androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeTab = "live" }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Live 收藏 (${liveFavorites.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLive) Color(0xFF101217) else MyBoxTextSecondary
                    )
                }
            }
        }

        // 3. Content
        when {
            state.loading && state.favorites.isEmpty() -> {
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
                        Text("正在载入收藏…", color = MyBoxTextSecondary, fontSize = 13.sp)
                    }
                }
            }
            activeTab == "movies" -> {
                if (movieFavorites.isEmpty()) {
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
                            Icon(Icons.Default.Movie, contentDescription = null, tint = MyBoxTextMuted, modifier = Modifier.size(40.dp))
                            Text("还没有影视收藏", color = MyBoxTextMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 84.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(movieFavorites, key = { it.record.favoriteId }) { fav ->
                            val record = fav.record
                            val rawJson = record.toJson()
                            val posterUrl = rawJson.optString("poster").ifBlank { rawJson.optString("vod_pic") }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFavorite(record) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MyBoxSurfaceRaised)
                                        .border(1.dp, MyBoxBorder, RoundedCornerShape(10.dp))
                                ) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = fav.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Remove Icon top-right
                                    IconButton(
                                        onClick = { favoritesViewModel.remove(record) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "取消收藏",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = fav.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MyBoxTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = fav.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MyBoxTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // Live tab
                if (liveFavorites.isEmpty()) {
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
                            Icon(Icons.Default.Radio, contentDescription = null, tint = MyBoxTextMuted, modifier = Modifier.size(40.dp))
                            Text("还没有 Live 收藏", color = MyBoxTextMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(liveFavorites, key = { it.record.favoriteId }) { fav ->
                            val record = fav.record
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MyBoxSurfaceRaised,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFavorite(record) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                                            .border(1.dp, MyBoxGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = fav.title.take(3),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MyBoxGold
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = fav.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MyBoxTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = fav.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MyBoxTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        IconButton(onClick = { favoritesViewModel.remove(record) }) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "取消收藏",
                                                tint = MyBoxHeart,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Button(
                                            onClick = { onOpenFavorite(record) },
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
                    }
                }
            }
        }
    }
}

private fun favoritesViewModelFactory(application: MyBoxApplication): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FavoritesViewModel::class.java)) return FavoritesViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
