package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.movie.StaticCatalogData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomePreviewSource(val sourceId: String, val name: String)

data class ContinueWatchingItem(
    val movie: NativeMovieItem,
    val episodeIndex: Int,
    val episodeTitle: String,
    val positionMinutes: Int,
)

data class HomeContentPreviewState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val sources: List<HomePreviewSource> = emptyList(),
    val selectedSourceId: String? = null,
    val sourceName: String? = null,
    val allMovies: List<NativeMovieItem> = emptyList(),
    val categories: List<NativeMovieCategory> = emptyList(),
    val selectedCategoryId: String = "movie",
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val error: String? = null,
)

class HomeContentPreviewViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeContentPreviewState(
            allMovies = StaticCatalogData.movies,
            categories = StaticCatalogData.categories,
            selectedCategoryId = "movie",
            selectedSourceId = "builtin",
            sourceName = "内置精选源",
            sources = listOf(HomePreviewSource("builtin", "内置精选源"))
        )
    )
    val uiState: StateFlow<HomeContentPreviewState> = _uiState.asStateFlow()
    private var requestGeneration = 0L

    fun load(force: Boolean = false) {
        if (_uiState.value.loading && !force) return
        val generation = ++requestGeneration
        viewModelScope.launch {
            try {
                val services = application.awaitServices()

                // 1. Fetch available sources
                val dbSources = services.sourceManagementService.getSources()
                    .filter { it.sourceType == "movie" && it.enabled }
                    .sortedBy { it.order }
                    .map { HomePreviewSource(it.sourceId, it.name) }

                val sourceList = buildList {
                    add(HomePreviewSource("builtin", "内置精选源"))
                    addAll(dbSources)
                }

                val currentSelectedId = _uiState.value.selectedSourceId
                val activeSourceId = if (sourceList.any { it.sourceId == currentSelectedId }) {
                    currentSelectedId ?: "builtin"
                } else {
                    "builtin"
                }
                val activeSource = sourceList.firstOrNull { it.sourceId == activeSourceId }

                // 2. Fetch history for "Continue Watching"
                val historyRecords = services.userDataRepository.getHistoryRecords()
                    .filter { it.targetType == "content" }
                    .sortedByDescending { it.lastPlayedAt }
                    .take(8)

                val progressRecords = services.userDataRepository.getProgressRecords()

                val continueList = historyRecords.mapNotNull { h ->
                    val targetIdStr = h.targetId?.toString().orEmpty()
                    val matchedMovie = StaticCatalogData.movies.firstOrNull { it.contentId == targetIdStr || it.sourceItemId == targetIdStr }
                    if (matchedMovie != null) {
                        val prog = progressRecords.firstOrNull { it.contentId?.toString() == targetIdStr }
                        val epIdx = 0
                        val epTitle = if (matchedMovie.episodes.isNotEmpty()) {
                            matchedMovie.episodes[0].optString("title").ifBlank { "正片" }
                        } else "正片"
                        val mins = ((prog?.positionSeconds ?: 0.0) / 60.0).toInt()
                        ContinueWatchingItem(matchedMovie, epIdx, epTitle, mins)
                    } else null
                }

                if (activeSourceId == "builtin") {
                    // Use rich curated static cinema dataset
                    if (generation == requestGeneration) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                loaded = true,
                                sources = sourceList,
                                selectedSourceId = "builtin",
                                sourceName = "内置精选源",
                                allMovies = StaticCatalogData.movies,
                                categories = StaticCatalogData.categories,
                                continueWatching = continueList,
                                error = null
                            )
                        }
                    }
                } else {
                    // Load online source content
                    _uiState.update {
                        it.copy(
                            loading = true,
                            sources = sourceList,
                            selectedSourceId = activeSourceId,
                            sourceName = activeSource?.name ?: "在线影视源",
                            continueWatching = continueList,
                        )
                    }

                    val categories = try {
                        services.movieSourceService.getCategories(activeSourceId)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val catList = buildList {
                        addAll(categories)
                        if (none { it.id == "all" || it.name == "全部" }) {
                            add(NativeMovieCategory(id = "all", name = "全部", rawJson = "{\"id\":\"all\",\"name\":\"全部\"}"))
                        }
                    }

                    val page = services.movieSourceService.getList(
                        activeSourceId,
                        page = 1,
                        pageSize = 30,
                        categoryId = _uiState.value.selectedCategoryId.takeIf { it != "all" }
                    )

                    val merged = if (page.items.isNotEmpty()) page.items else StaticCatalogData.movies

                    if (generation == requestGeneration) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                loaded = true,
                                allMovies = merged,
                                categories = if (catList.isNotEmpty()) catList else StaticCatalogData.categories,
                                error = null
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == requestGeneration) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loaded = true,
                            allMovies = StaticCatalogData.movies,
                            categories = StaticCatalogData.categories,
                            error = error.localizedMessage
                        )
                    }
                }
            }
        }
    }

    fun selectSource(sourceId: String) {
        if (_uiState.value.selectedSourceId == sourceId) return
        requestGeneration++
        _uiState.update {
            it.copy(
                selectedSourceId = sourceId,
                loading = true,
                selectedCategoryId = "movie",
            )
        }
        load(force = true)
    }

    fun selectCategory(categoryId: String) {
        if (_uiState.value.selectedCategoryId == categoryId) return
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        if (_uiState.value.selectedSourceId != "builtin") {
            load(force = true)
        }
    }
}
