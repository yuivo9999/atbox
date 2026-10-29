package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import com.yuivo9999.mybox123.domain.movie.NativeMoviePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A non-playing preview of one enabled native movie source; it is not a recommendation feed. */
class HomeContentPreviewViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeContentPreviewState())
    val uiState: StateFlow<HomeContentPreviewState> = _uiState.asStateFlow()
    private var requestGeneration = 0L

    fun load(force: Boolean = false) {
        if (_uiState.value.loading && !force) return
        if (_uiState.value.loaded && !force) return
        val generation = ++requestGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val services = application.awaitServices()
                val sources = services.sourceManagementService.getSources()
                    .filter { it.sourceType == "movie" && it.enabled }
                    .sortedBy { it.order }
                    .map { HomePreviewSource(it.sourceId, it.name) }
                val selectedId = _uiState.value.selectedSourceId
                    ?.takeIf { id -> sources.any { it.sourceId == id } }
                val source = sources.firstOrNull { it.sourceId == selectedId }
                    ?: sources.firstOrNull()
                if (generation != requestGeneration) return@launch
                _uiState.update {
                    it.copy(
                        sources = sources,
                        selectedSourceId = source?.sourceId,
                        sourceName = source?.name,
                        items = emptyList(),
                        error = null,
                    )
                }
                if (source == null) {
                    _uiState.update { it.copy(loading = false, loaded = true, sourceName = null, items = emptyList(), error = null) }
                    return@launch
                }
                // Load categories for the selected source (if not already loaded)
                if (_uiState.value.categories.isEmpty() && _uiState.value.selectedSourceId == source.sourceId) {
                    val categories = services.movieSourceService.getCategories(source.sourceId)
                    if (generation == requestGeneration) _uiState.update { it.copy(categories = categories) }
                }
                val page: NativeMoviePage = services.movieSourceService.getList(
                    source.sourceId,
                    page = 1,
                    pageSize = 6,
                    categoryId = _uiState.value.selectedCategoryId
                )
                if (generation == requestGeneration) _uiState.update {
                    it.copy(loading = false, loaded = true, sourceName = source.name, items = page.items, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == requestGeneration) _uiState.update {
                    val selectedName = it.sources.firstOrNull { source -> source.sourceId == it.selectedSourceId }?.name
                    it.copy(
                        loading = false,
                        loaded = true,
                        sourceName = selectedName ?: it.sourceName,
                        items = emptyList(),
                        error = error.message ?: "HOME_CONTENT_PREVIEW_FAILED",
                    )
                }
            }
        }
    }

    fun selectSource(sourceId: String) {
        val source = _uiState.value.sources.firstOrNull { it.sourceId == sourceId } ?: return
        if (_uiState.value.selectedSourceId == sourceId && _uiState.value.error == null) return
        // Invalidate any in-flight request before starting the newly selected source.
        requestGeneration++
        _uiState.update {
            it.copy(
                selectedSourceId = source.sourceId,
                sourceName = source.name,
                loading = false,
                loaded = false,
                items = emptyList(),
                error = null,
                selectedCategoryId = null,
                categories = emptyList(),
            )
        }
        load(force = true)
    }

    fun selectCategory(categoryId: String?) {
        if (_uiState.value.selectedCategoryId == categoryId) return
        // Category change resets pagination (page=1) via load()
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        load(force = true)
    }
}

data class HomePreviewSource(val sourceId: String, val name: String)

data class HomeContentPreviewState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val sources: List<HomePreviewSource> = emptyList(),
    val selectedSourceId: String? = null,
    val sourceName: String? = null,
    val items: List<NativeMovieItem> = emptyList(),
    val error: String? = null,
    val categories: List<NativeMovieCategory> = emptyList(),
    val selectedCategoryId: String? = null,
)
