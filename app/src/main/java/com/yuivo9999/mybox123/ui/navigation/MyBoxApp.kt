package com.yuivo9999.mybox123.ui.navigation

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.yuivo9999.mybox123.data.storage.FavoriteRecord
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.app.AppDestination
import com.yuivo9999.mybox123.app.AppViewModel
import com.yuivo9999.mybox123.ui.screens.NativeMigrationScreen
import com.yuivo9999.mybox123.ui.screens.NativeLiveBrowseScreen
import com.yuivo9999.mybox123.ui.screens.NativeHomeScreen
import com.yuivo9999.mybox123.ui.screens.NativeFavoritesScreen
import com.yuivo9999.mybox123.ui.screens.NativeMovieCatalogScreen
import com.yuivo9999.mybox123.ui.screens.NativeMoviePlaybackScreen
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.ui.screens.NativeProfileHubScreen
import com.yuivo9999.mybox123.ui.screens.ProfileSubpage
import com.yuivo9999.mybox123.ui.theme.MyBoxBackground
import com.yuivo9999.mybox123.ui.theme.MyBoxSurface

@Composable
fun MyBoxApp(
    onExit: () -> Unit,
    appViewModel: AppViewModel = viewModel(),
) {
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    var pendingMovieFavoriteJson by remember { mutableStateOf<String?>(null) }
    var pendingLiveFavoriteJson by remember { mutableStateOf<String?>(null) }
    var pendingMovieSearchKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMoviePlayback by rememberSaveable { mutableStateOf<Pair<NativeMovieItem, String?>?>(null) }
    var restoreHomeHistoryAfterDetail by rememberSaveable { mutableStateOf(false) }
    var restoreHomeAfterContentPreviewDetail by rememberSaveable { mutableStateOf(false) }
    var profileSubpageName by rememberSaveable { mutableStateOf(ProfileSubpage.SOURCES.name) }

    BackHandler {
        if (uiState.destination != AppDestination.HOME) {
            pendingMovieFavoriteJson = null
            pendingLiveFavoriteJson = null
            pendingMovieSearchKeyword = null
            restoreHomeHistoryAfterDetail = false
            restoreHomeAfterContentPreviewDetail = false
            appViewModel.navigateHome()
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackPressAt < EXIT_CONFIRMATION_WINDOW_MS) {
                onExit()
            } else {
                lastBackPressAt = now
                Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isFullScreen = uiState.destination == AppDestination.MOVIE_PLAYBACK

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MyBoxBackground,
        bottomBar = {
            if (!isFullScreen) {
                Surface(
                    color = MyBoxSurface,
                    tonalElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, com.yuivo9999.mybox123.ui.theme.MyBoxBorder),
                ) {
                    NavigationBar(
                        containerColor = MyBoxSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                    ) {
                        AppDestination.tabEntries.forEach { destination ->
                            val isSelected = uiState.destination == destination
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    restoreHomeHistoryAfterDetail = false
                                    restoreHomeAfterContentPreviewDetail = false
                                    if (destination != AppDestination.MOVIES) {
                                        pendingMovieFavoriteJson = null
                                        pendingMovieSearchKeyword = null
                                    }
                                    if (destination != AppDestination.LIVE) pendingLiveFavoriteJson = null
                                    appViewModel.navigateTo(destination)
                                },
                                icon = {
                                    Icon(
                                        imageVector = destinationIcon(destination),
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                                alwaysShowLabel = true,
                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = com.yuivo9999.mybox123.ui.theme.MyBoxGold,
                                    selectedTextColor = com.yuivo9999.mybox123.ui.theme.MyBoxGold,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unselectedIconColor = com.yuivo9999.mybox123.ui.theme.MyBoxTextMuted,
                                    unselectedTextColor = com.yuivo9999.mybox123.ui.theme.MyBoxTextMuted,
                                )
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState.destination) {
                AppDestination.HOME -> NativeHomeScreen(
                    onSearchMovies = { keyword ->
                        pendingMovieSearchKeyword = keyword.trim()
                        pendingMovieFavoriteJson = null
                        restoreHomeHistoryAfterDetail = false
                        restoreHomeAfterContentPreviewDetail = false
                        appViewModel.navigateTo(AppDestination.MOVIES)
                    },
                    onNavigate = { destination ->
                        restoreHomeHistoryAfterDetail = false
                        restoreHomeAfterContentPreviewDetail = false
                        appViewModel.navigateTo(destination)
                    },
                    onOpenHistoryMovieDetail = { rawJson ->
                        restoreHomeHistoryAfterDetail = true
                        restoreHomeAfterContentPreviewDetail = false
                        pendingMovieFavoriteJson = rawJson
                        appViewModel.navigateTo(AppDestination.MOVIES)
                    },
                    onOpenContentPreviewDetail = { rawJson ->
                        restoreHomeHistoryAfterDetail = false
                        restoreHomeAfterContentPreviewDetail = true
                        pendingMovieFavoriteJson = rawJson
                        appViewModel.navigateTo(AppDestination.MOVIES)
                    },
                    onPlayMovie = { movie ->
                        pendingMoviePlayback = Pair(movie, movie.sourceId)
                        appViewModel.navigateTo(AppDestination.MOVIE_PLAYBACK)
                    },
                    initialShowHistory = restoreHomeHistoryAfterDetail,
                    onHistoryDismissed = { restoreHomeHistoryAfterDetail = false },
                )
                AppDestination.PROFILE -> NativeProfileHubScreen(
                    selectedPageName = profileSubpageName,
                    onSelectedPageNameChange = { profileSubpageName = it },
                )
                AppDestination.LIVE -> NativeLiveBrowseScreen(
                    favoriteNavigationJson = pendingLiveFavoriteJson,
                    onFavoriteNavigationConsumed = { pendingLiveFavoriteJson = null },
                )
                AppDestination.MOVIES -> NativeMovieCatalogScreen(
                    favoriteNavigationJson = pendingMovieFavoriteJson,
                    onFavoriteNavigationConsumed = { pendingMovieFavoriteJson = null },
                    pendingSearchKeyword = pendingMovieSearchKeyword,
                    onSearchNavigationConsumed = { pendingMovieSearchKeyword = null },
                    returnToHomeAfterDetail = restoreHomeHistoryAfterDetail || restoreHomeAfterContentPreviewDetail,
                    onReturnToHomeAfterDetail = {
                        pendingMovieFavoriteJson = null
                        pendingMovieSearchKeyword = null
                        if (!restoreHomeHistoryAfterDetail) restoreHomeAfterContentPreviewDetail = false
                        appViewModel.navigateHome()
                    },
                    onPlayMovie = { movie ->
                        pendingMoviePlayback = Pair(movie, movie.sourceId)
                        appViewModel.navigateTo(AppDestination.MOVIE_PLAYBACK)
                    },
                )
                AppDestination.MOVIE_PLAYBACK -> NativeMoviePlaybackScreen(
                    movie = pendingMoviePlayback?.first ?: NativeMovieItem(
                        sourceId = "",
                        sourceItemId = "",
                        contentId = "",
                        title = "",
                        poster = "",
                        category = "",
                        description = "",
                        episodes = emptyList(),
                        rawJson = "{}",
                    ),
                    sourceId = pendingMoviePlayback?.second ?: "",
                    onPlaybackFinished = {
                        pendingMoviePlayback = null
                        appViewModel.navigateHome()
                    },
                )
                AppDestination.FAVORITES -> NativeFavoritesScreen(
                    onOpenFavorite = { record: FavoriteRecord ->
                        val json = record.toJson().toString()
                        if (record.targetType.equals("channel", true) || record.targetType.equals("live", true) || record.toJson().optString("kind").equals("live", true)) {
                            pendingLiveFavoriteJson = json
                            appViewModel.navigateTo(AppDestination.LIVE)
                        } else {
                            pendingMovieFavoriteJson = json
                            appViewModel.navigateTo(AppDestination.MOVIES)
                        }
                    },
                )
                else -> NativeMigrationScreen(destination = uiState.destination)
            }
        }
    }
}

private fun destinationIcon(destination: AppDestination): ImageVector = when (destination) {
    AppDestination.HOME -> Icons.Filled.Home
    AppDestination.LIVE -> Icons.Filled.PlayArrow
    AppDestination.MOVIES -> Icons.Filled.Movie
    AppDestination.MOVIE_PLAYBACK -> Icons.Filled.VideoLibrary
    AppDestination.FAVORITES -> Icons.Filled.FavoriteBorder
    AppDestination.PROFILE -> Icons.Filled.Person
}

private const val EXIT_CONFIRMATION_WINDOW_MS = 2_000L
