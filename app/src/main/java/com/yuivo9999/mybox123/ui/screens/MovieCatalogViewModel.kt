package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.data.storage.SearchRecord
import com.yuivo9999.mybox123.data.storage.FavoriteRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import com.yuivo9999.mybox123.domain.movie.NativeMoviePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native non-player catalog, category, search and detail flow. Playback is deliberately deferred. */
class MovieCatalogViewModel(
    private val application: MyBoxApplication,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MovieCatalogUiState())
    val uiState: StateFlow<MovieCatalogUiState> = _uiState.asStateFlow()
    private var requestGeneration = 0L
    private var requestJob: Job? = null
    private var searchHistoryJob: Job? = null
    private val searchHistoryMutationMutex = Mutex()
    private val searchHistoryGeneration = AtomicLong(0L)
    private var detailGeneration = 0L
    private var detailJob: Job? = null
    private var categoryGeneration = 0L
    private var categoryJob: Job? = null

    fun loadSources() {
        if (_uiState.value.sourcesLoading || _uiState.value.sourcesLoaded) return
        viewModelScope.launch {
            _uiState.update { it.copy(sourcesLoading = true, error = null) }
            try {
                val externalSources = application.awaitServices().sourceManagementService.getSources()
                    .filter { it.sourceType == "movie" && it.enabled }
                    .sortedBy { it.order }
                val builtinSource = NativeContentSource.fromJson(
                    JSONObject()
                        .put("sourceId", "builtin")
                        .put("name", "内置精选源")
                        .put("sourceType", "movie")
                        .put("enabled", true)
                        .put("order", -1),
                    -1
                )
                val sources = listOf(builtinSource) + externalSources
                val selected = sources.firstOrNull { it.sourceId == _uiState.value.selectedSourceId }
                    ?: builtinSource
                _uiState.update {
                    it.copy(sourcesLoading = false, sourcesLoaded = true, sources = sources,
                        selectedSourceId = selected.sourceId, selectedCategoryId = null, categories = emptyList(), categoriesLoading = false, categoryError = null, error = null)
                }
                loadCategories(selected.sourceId)
                loadPage(reset = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(sourcesLoading = false, sourcesLoaded = false, error = error.message ?: "MOVIE_SOURCES_LOAD_FAILED") }
            }
        }
    }

    fun selectSource(sourceId: String) {
        if (_uiState.value.selectedSourceId == sourceId) return
        categoryJob?.cancel()
        _uiState.update { it.copy(selectedSourceId = sourceId, selectedCategoryId = null, categories = emptyList(), categoriesLoading = false, categoryError = null, items = emptyList(), page = 1, total = 0, error = null) }
        loadCategories(sourceId)
        loadPage(reset = true)
    }

    fun selectCategory(categoryId: String?) {
        val normalized = categoryId?.trim()?.takeIf { it.isNotEmpty() }
        if (_uiState.value.selectedCategoryId == normalized) return
        _uiState.update { it.copy(selectedCategoryId = normalized, items = emptyList(), page = 1, total = 0, error = null) }
        loadPage(reset = true)
    }

    private fun loadCategories(sourceId: String) {
        categoryJob?.cancel()
        val generation = ++categoryGeneration
        _uiState.update { it.copy(categoriesLoading = true, categoryError = null) }
        categoryJob = viewModelScope.launch {
            try {
                val categories = if (sourceId == "builtin") {
                    com.yuivo9999.mybox123.data.movie.StaticCatalogData.categories
                } else {
                    application.awaitServices().movieSourceService.getCategories(sourceId)
                }
                if (generation == categoryGeneration && _uiState.value.selectedSourceId == sourceId) {
                    _uiState.update { state ->
                        val selectedStillExists = categories.any { it.id == state.selectedCategoryId }
                        state.copy(categories = categories, categoriesLoading = false, categoryError = null,
                            selectedCategoryId = if (selectedStillExists) state.selectedCategoryId else null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == categoryGeneration && _uiState.value.selectedSourceId == sourceId) {
                    _uiState.update { it.copy(categories = emptyList(), categoriesLoading = false, categoryError = error.message ?: "MOVIE_CATEGORIES_LOAD_FAILED") }
                }
            }
        }
    }

    fun retryCategories() {
        _uiState.value.selectedSourceId?.let(::loadCategories)
    }

    fun updateKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
    }

    fun search() {
        val keyword = _uiState.value.keyword.trim()
        _uiState.update { it.copy(keyword = keyword, page = 1, items = emptyList(), total = 0) }
        if (keyword.isNotEmpty()) persistSearchKeyword(keyword)
        loadPage(reset = true)
    }

    /** Applies a HOME search intent before source initialization can issue its first catalog request. */
    fun searchFromNavigation(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        _uiState.update { it.copy(keyword = normalized, page = 1, items = emptyList(), total = 0, error = null) }
        if (_uiState.value.sourcesLoaded) {
            search()
        } else {
            // loadSources() reads the latest UI state when it starts the initial page request.
            // If source loading is already in flight, its completion will use this updated keyword.
            loadSources()
        }
    }

    fun loadSearchHistory() {
        if (_uiState.value.searchHistoryLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(searchHistoryLoading = true, searchHistoryError = null) }
            try {
                val records = withContext(Dispatchers.IO) {
                    application.awaitServices().userDataRepository.getSearchRecords()
                        .filter { it.keyword.isNotBlank() }
                        .sortedByDescending { it.searchedAt }
                        .take(MAX_SEARCH_HISTORY)
                }
                _uiState.update { it.copy(searchHistoryLoading = false, searchHistoryLoaded = true, searchHistory = records) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(searchHistoryLoading = false, searchHistoryError = error.message ?: "SEARCH_HISTORY_LOAD_FAILED") }
            }
        }
    }

    fun useSearchKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
        search()
    }

    fun deleteSearchRecord(searchId: String) {
        // Invalidate older, not-yet-committed search writes so they cannot restore a deleted row.
        searchHistoryGeneration.incrementAndGet()
        searchHistoryJob?.cancel()
        searchHistoryJob = viewModelScope.launch {
            _uiState.update { it.copy(searchHistoryLoading = true, searchHistoryError = null) }
            try {
                val remaining = withContext(Dispatchers.IO) {
                    searchHistoryMutationMutex.withLock {
                        val repository = application.awaitServices().userDataRepository
                        val records = repository.getSearchRecords()
                        repository.saveSearchRecords(records.filterNot { it.searchId == searchId })
                        repository.getSearchRecords().filter { it.keyword.isNotBlank() }.sortedByDescending { it.searchedAt }.take(MAX_SEARCH_HISTORY)
                    }
                }
                _uiState.update { it.copy(searchHistoryLoading = false, searchHistoryLoaded = true, searchHistory = remaining) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(searchHistoryLoading = false, searchHistoryError = error.message ?: "SEARCH_HISTORY_DELETE_FAILED") }
            }
        }
    }

    fun clearSearchHistory() {
        // Invalidate persistence jobs that started before the user confirmed clear-all.
        searchHistoryGeneration.incrementAndGet()
        searchHistoryJob?.cancel()
        searchHistoryJob = viewModelScope.launch {
            _uiState.update { it.copy(searchHistoryLoading = true, searchHistoryError = null) }
            try {
                withContext(Dispatchers.IO) {
                    searchHistoryMutationMutex.withLock {
                        application.awaitServices().userDataRepository.saveSearchRecords(emptyList())
                    }
                }
                _uiState.update { it.copy(searchHistoryLoading = false, searchHistoryLoaded = true, searchHistory = emptyList(), searchHistoryError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(searchHistoryLoading = false, searchHistoryError = error.message ?: "SEARCH_HISTORY_CLEAR_FAILED") }
            }
        }
    }

    private fun persistSearchKeyword(keyword: String) {
        val generation = searchHistoryGeneration.get()
        viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    searchHistoryMutationMutex.withLock {
                        if (generation != searchHistoryGeneration.get()) return@withLock null
                        val repository = application.awaitServices().userDataRepository
                        val existing = repository.getSearchRecords()
                        val matching = existing.firstOrNull { it.keyword.trim().equals(keyword, ignoreCase = true) }
                        val now = System.currentTimeMillis()
                        val updated = if (matching != null) {
                            SearchRecord.fromJson(matching.toJson().put("keyword", keyword).put("searchedAt", now).put("count", matching.count + 1))
                        } else {
                            SearchRecord.fromJson(JSONObject().put("keyword", keyword).put("searchedAt", now).put("count", 1))
                        }
                        val next = (listOf(updated) + existing.filterNot { it.keyword.trim().equals(keyword, ignoreCase = true) })
                            .sortedByDescending { it.searchedAt }
                            .take(MAX_SEARCH_HISTORY)
                        repository.saveSearchRecords(next)
                        next
                    }
                }
                if (records != null) _uiState.update { state ->
                    // A clear/delete may have superseded this persistence after its DB write completed.
                    if (generation == searchHistoryGeneration.get() && state.searchHistoryLoaded) {
                        state.copy(searchHistory = records)
                    } else state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Search itself should remain usable if optional history persistence fails.
            }
        }
    }

    fun openDetail(movie: NativeMovieItem) {
        val sourceId = movie.sourceId.takeIf { it.isNotBlank() } ?: _uiState.value.selectedSourceId.orEmpty()
        if (sourceId.isBlank()) {
            _uiState.update { it.copy(navigationError = "该条目缺少内容源标识，无法定位到影视详情。") }
            return
        }
        _uiState.update { it.copy(selectedSourceId = sourceId, navigationError = null) }
        detailJob?.cancel()
        val generation = ++detailGeneration
        _uiState.update { it.copy(detailOpen = true, detailMovie = movie, detailLoading = true, detailError = null, detailFavoriteSaved = false) }
        detailJob = viewModelScope.launch {
            try {
                val lookupId = movie.sourceItemId.ifBlank { movie.contentId }
                val detail = if (sourceId == "builtin") {
                    com.yuivo9999.mybox123.data.movie.StaticCatalogData.movies.firstOrNull {
                        it.contentId == lookupId || it.sourceItemId == lookupId || it.title.equals(movie.title, ignoreCase = true)
                    } ?: movie
                } else {
                    val service = application.awaitServices().movieSourceService
                    if (lookupId.isBlank()) null else service.getDetail(sourceId, lookupId)
                }
                if (generation == detailGeneration) {
                    _uiState.update { it.copy(detailMovie = detail ?: movie, detailLoading = false, detailError = if (detail == null) "详情源未返回匹配条目，当前显示目录中的可用信息。" else null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == detailGeneration) {
                    _uiState.update { it.copy(detailMovie = movie, detailLoading = false, detailError = error.message ?: "MOVIE_DETAIL_LOAD_FAILED") }
                }
            }
        }
    }


    fun openFavoriteJson(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            val sourceId = json.optString("sourceId").trim().takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
            val itemId = listOf(json.optString("sourceItemId"), json.optString("contentId"), json.optString("targetId"))
                .firstNotNullOfOrNull { value -> value.trim().takeIf { it.isNotEmpty() && it != "null" } }.orEmpty()
            if (sourceId.isBlank() || itemId.isBlank()) {
                _uiState.update { it.copy(navigationError = "该旧影视收藏缺少内容源或条目标识，无法准确定位；收藏记录未被修改。") }
                return
            }
            val source = _uiState.value.sources.firstOrNull { it.sourceId == sourceId && it.enabled }
            if (source == null) {
                _uiState.update { it.copy(navigationError = "收藏对应的影视源不存在或已停用。请先在“我的 - 内容源”检查该源；收藏记录未被修改。") }
                return
            }
            json.put("sourceId", sourceId)
                .put("sourceItemId", itemId)
                .put("contentId", json.optString("contentId").ifBlank { itemId })
                .put("title", json.optString("title").ifBlank { json.optString("name").ifBlank { "未命名收藏" } })
                .put("poster", json.optString("poster"))
                .put("category", json.optString("category"))
                .put("description", json.optString("description"))
                .put("episodes", json.optJSONArray("episodes") ?: org.json.JSONArray())
            openDetail(NativeMovieItem.fromJson(json))
        } catch (error: Exception) {
            _uiState.update { it.copy(navigationError = "无法解析这条影视收藏：${error.message ?: "记录格式不兼容"}。收藏记录未被修改。") }
        }
    }

    fun closeDetail() {
        detailGeneration++
        detailJob?.cancel()
        _uiState.update { it.copy(detailOpen = false, detailMovie = null, detailLoading = false, detailError = null, detailFavoriteSaved = false) }
    }

    fun addDetailToFavorites() {
        val state = _uiState.value
        val movie = state.detailMovie ?: return
        if (state.detailFavoriteSaving) return
        val sourceId = state.selectedSourceId ?: movie.sourceId
        val source = state.sources.firstOrNull { it.sourceId == sourceId }
        val itemId = movie.sourceItemId.ifBlank { movie.contentId }.ifBlank { movie.title }
        val favoriteId = "favorite:content:$sourceId:$itemId"
        // Saving may finish after the user closes this detail or opens another one.
        // Persist the requested favorite, but only publish completion into the same detail session.
        val generation = detailGeneration
        viewModelScope.launch {
            if (generation == detailGeneration && _uiState.value.detailOpen) {
                _uiState.update { it.copy(detailFavoriteSaving = true, detailFavoriteSaved = false, detailError = null) }
            }
            try {
                withContext(Dispatchers.IO) {
                    val repository = application.awaitServices().userDataRepository
                    val records = repository.getFavoriteRecords()
                    if (records.none { it.favoriteId == favoriteId }) {
                        val now = System.currentTimeMillis()
                        val record = FavoriteRecord.fromJson(
                            movie.toJson()
                                .put("favoriteId", favoriteId)
                                .put("targetType", "content")
                                .put("targetId", itemId)
                                .put("kind", "movie")
                                .put("sourceId", sourceId)
                                .put("sourceName", source?.name.orEmpty())
                                .put("sourceItemId", movie.sourceItemId)
                                .put("contentId", movie.contentId)
                                .put("title", movie.title)
                                .put("name", movie.title)
                                .put("poster", movie.poster)
                                .put("category", movie.category)
                                .put("description", movie.description)
                                .put("createdAt", now)
                                .put("lastAccessedAt", now)
                        )
                        repository.saveFavoriteRecords(records + record)
                    }
                }
                if (generation == detailGeneration && _uiState.value.detailOpen) {
                    _uiState.update { it.copy(detailFavoriteSaving = false, detailFavoriteSaved = true) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == detailGeneration && _uiState.value.detailOpen) {
                    _uiState.update { it.copy(detailFavoriteSaving = false, detailError = error.message ?: "MOVIE_FAVORITE_SAVE_FAILED") }
                }
            }
        }
    }

    fun refresh() = loadPage(reset = true)

    fun loadNextPage() {
        val state = _uiState.value
        if (state.loading || state.items.size.toLong() >= state.total || state.selectedSourceId == null) return
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        val snapshot = _uiState.value
        val sourceId = snapshot.selectedSourceId ?: return
        if (snapshot.loading) {
            requestJob?.cancel()
        }
        val generation = ++requestGeneration
        val page = if (reset) 1 else snapshot.page + 1
        requestJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val result: NativeMoviePage = if (sourceId == "builtin") {
                    val all = com.yuivo9999.mybox123.data.movie.StaticCatalogData.movies
                    val filtered = all.filter { movie ->
                        val catMatches = snapshot.selectedCategoryId == null || snapshot.selectedCategoryId == "all" ||
                            when (snapshot.selectedCategoryId) {
                                "movie" -> movie.category == "电影"
                                "series" -> movie.category == "电视剧"
                                "anime" -> movie.category == "动漫"
                                "variety" -> movie.category == "综艺"
                                "short_drama" -> movie.category == "短剧"
                                else -> movie.category.contains(snapshot.selectedCategoryId!!)
                            }
                        val kwMatches = snapshot.keyword.isBlank() ||
                            movie.title.contains(snapshot.keyword, ignoreCase = true) ||
                            movie.category.contains(snapshot.keyword, ignoreCase = true) ||
                            movie.description.contains(snapshot.keyword, ignoreCase = true)
                        catMatches && kwMatches
                    }
                    NativeMoviePage(
                        sourceId = "builtin",
                        page = 1,
                        pageSize = filtered.size,
                        total = filtered.size.toLong(),
                        items = filtered,
                        httpStatus = 200
                    )
                } else {
                    val service = application.awaitServices().movieSourceService
                    if (snapshot.keyword.isBlank()) {
                        service.getList(sourceId = sourceId, page = page, categoryId = snapshot.selectedCategoryId)
                    } else {
                        service.search(sourceId = sourceId, keyword = snapshot.keyword, page = page, categoryId = snapshot.selectedCategoryId)
                    }
                }
                if (generation == requestGeneration) {
                    _uiState.update {
                        it.copy(loading = false, page = result.page, total = result.total,
                            items = if (reset) result.items else it.items + result.items, error = null,
                            lastHttpStatus = result.httpStatus)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == requestGeneration) {
                    _uiState.update { it.copy(loading = false, error = error.message ?: "MOVIE_LIST_LOAD_FAILED") }
                }
            }
        }
    }
}

data class MovieCatalogUiState(
    val sourcesLoading: Boolean = false,
    val sourcesLoaded: Boolean = false,
    val sources: List<NativeContentSource> = emptyList(),
    val selectedSourceId: String? = null,
    val categories: List<NativeMovieCategory> = emptyList(),
    val categoriesLoading: Boolean = false,
    val categoryError: String? = null,
    val selectedCategoryId: String? = null,
    val keyword: String = "",
    val loading: Boolean = false,
    val page: Int = 1,
    val total: Long = 0,
    val items: List<NativeMovieItem> = emptyList(),
    val error: String? = null,
    val lastHttpStatus: Int? = null,
    val searchHistoryLoading: Boolean = false,
    val searchHistoryLoaded: Boolean = false,
    val searchHistory: List<SearchRecord> = emptyList(),
    val searchHistoryError: String? = null,
    val detailOpen: Boolean = false,
    val detailMovie: NativeMovieItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val detailFavoriteSaving: Boolean = false,
    val detailFavoriteSaved: Boolean = false,
    val navigationError: String? = null,
)

private const val MAX_SEARCH_HISTORY = 50

