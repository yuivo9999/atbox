package com.yuivo9999.mybox123.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.movie.StaticCatalogData
import com.yuivo9999.mybox123.data.storage.HistoryRecord
import com.yuivo9999.mybox123.ui.theme.MyBoxBackground
import com.yuivo9999.mybox123.ui.theme.MyBoxBorder
import com.yuivo9999.mybox123.ui.theme.MyBoxGold
import com.yuivo9999.mybox123.ui.theme.MyBoxGoldLight
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextMuted
import com.yuivo9999.mybox123.ui.theme.MyBoxTextPrimary
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(UnstableApi::class)
@Composable
fun NativeMoviePlaybackScreen(
    movie: NativeMovieItem,
    sourceId: String,
    onPlaybackFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Fallback if episodes is empty: check static catalog
    val effectiveMovie = remember(movie) {
        if (movie.episodes.isNotEmpty()) movie
        else {
            val matched = StaticCatalogData.movies.firstOrNull {
                it.contentId == movie.contentId || it.sourceItemId == movie.sourceItemId || it.title == movie.title
            }
            matched ?: movie
        }
    }

    var selectedEpisodeIndex by remember { mutableIntStateOf(0) }
    var selectedCandidateIndex by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }

    val currentEpisode = remember(effectiveMovie.episodes, selectedEpisodeIndex) {
        if (selectedEpisodeIndex in effectiveMovie.episodes.indices) {
            effectiveMovie.episodes[selectedEpisodeIndex]
        } else null
    }

    val candidates = remember(currentEpisode) {
        val list = mutableListOf<Pair<String, String>>()
        val rawCandidates = currentEpisode?.optJSONArray("candidates")
        if (rawCandidates != null && rawCandidates.length() > 0) {
            for (i in 0 until rawCandidates.length()) {
                val obj = rawCandidates.optJSONObject(i)
                if (obj != null) {
                    val label = obj.optString("label").ifBlank { "线路 ${i + 1}" }
                    val url = obj.optString("mediaUrl").ifBlank { obj.optString("url") }
                    if (url.isNotBlank()) list.add(label to url)
                }
            }
        }
        if (list.isEmpty()) {
            val defaultUrl = currentEpisode?.optString("url")
                ?: currentEpisode?.optString("playUrl")
                ?: effectiveMovie.toJson().optString("url")
                ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
            list.add("4K超清专线" to defaultUrl)
        }
        list
    }

    val activePlayUrl = remember(candidates, selectedCandidateIndex) {
        if (selectedCandidateIndex in candidates.indices) candidates[selectedCandidateIndex].second
        else candidates.firstOrNull()?.second ?: ""
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Update media source when URL changes
    LaunchedEffect(exoPlayer, activePlayUrl) {
        if (activePlayUrl.isNotBlank()) {
            isBuffering = true
            errorMessage = null
            exoPlayer.setMediaItem(MediaItem.fromUri(activePlayUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Speed update
    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Player events listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                errorMessage = error.localizedMessage ?: "当前线路连接超时，可尝试切换其他线路"
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            // Record history before releasing
            val pos = exoPlayer.currentPosition / 1000L
            val dur = (exoPlayer.duration / 1000L).coerceAtLeast(0L)
            exoPlayer.removeListener(listener)
            exoPlayer.release()

            if (pos > 5L) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val repo = application.awaitServices().userDataRepository
                        val existing = repo.getHistoryRecords()
                        val record = HistoryRecord.fromJson(
                            JSONObject()
                                .put("historyId", "hist_${System.currentTimeMillis()}")
                                .put("targetType", "content")
                                .put("targetId", effectiveMovie.contentId.ifBlank { effectiveMovie.sourceItemId })
                                .put("title", effectiveMovie.title)
                                .put("sourceId", sourceId)
                                .put("positionSeconds", pos)
                                .put("durationSeconds", dur)
                                .put("lastPlayedAt", System.currentTimeMillis())
                                .put("poster", effectiveMovie.poster)
                                .put("episodeId", currentEpisode?.optString("episodeId") ?: "ep1")
                                .put("episodeTitle", currentEpisode?.optString("title") ?: "正片")
                        )
                        val updated = listOf(record) + existing.filterNot { it.targetId == record.targetId }
                        repo.saveHistoryRecords(updated.take(50))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    BackHandler {
        onPlaybackFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
    ) {
        // 1. Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onPlaybackFinished) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = effectiveMovie.title.ifBlank { "影片播放" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val epLabel = currentEpisode?.optString("title")?.ifBlank { "第 ${selectedEpisodeIndex + 1} 集" } ?: "正片 4K"
                    Text(
                        text = "$epLabel · ${candidates.getOrNull(selectedCandidateIndex)?.first ?: "4K线路"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyBoxGold,
                        fontSize = 11.sp
                    )
                }
            }

            // Speed Selector Button
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MyBoxSurfaceRaised,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
                    modifier = Modifier.clickable { speedMenuExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MyBoxGold, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${playbackSpeed}x",
                            fontSize = 12.sp,
                            color = MyBoxTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false }
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x 倍速") },
                            onClick = {
                                playbackSpeed = speed
                                speedMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 2. Video Player Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering && errorMessage == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = MyBoxGold, modifier = Modifier.size(36.dp))
                        Text("正在缓冲 4K 极速流…", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MyBoxGold,
                            modifier = Modifier.clickable {
                                errorMessage = null
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            }
                        ) {
                            Text(
                                text = "重新连接线路",
                                color = Color(0xFF101217),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Multi-line Switcher (Candidates)
        if (candidates.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("播放线路切换", style = MaterialTheme.typography.labelSmall, color = MyBoxTextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates.size) { idx ->
                        val isSel = idx == selectedCandidateIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MyBoxGoldLight else MyBoxSurfaceRaised,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) MyBoxGold else MyBoxBorder),
                            modifier = Modifier.clickable { selectedCandidateIndex = idx }
                        ) {
                            Text(
                                text = candidates[idx].first,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MyBoxGold else MyBoxTextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Episode Switcher
        if (effectiveMovie.episodes.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "选集 (${effectiveMovie.episodes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MyBoxTextPrimary
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(effectiveMovie.episodes.size) { index ->
                        val ep = effectiveMovie.episodes[index]
                        val name = ep.optString("title").ifBlank { ep.optString("name") }.ifBlank { "第 ${index + 1} 集" }
                        val isSelected = index == selectedEpisodeIndex

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MyBoxGoldLight else MyBoxSurfaceRaised,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MyBoxGold else MyBoxBorder),
                            modifier = Modifier.clickable {
                                selectedEpisodeIndex = index
                                selectedCandidateIndex = 0
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MyBoxGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MyBoxGold else MyBoxTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Synopsis
        if (effectiveMovie.description.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "影片简介",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MyBoxTextPrimary
                )
                Text(
                    text = effectiveMovie.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MyBoxTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
