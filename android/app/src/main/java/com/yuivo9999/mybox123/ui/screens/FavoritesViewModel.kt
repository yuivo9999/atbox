package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.FavoriteRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native favorite-list state. Repository disk access is always dispatched away from the UI thread. */
class FavoritesViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()
    private var generation = 0L

    fun load(force: Boolean = false) {
        if (_uiState.value.loading || (_uiState.value.loaded && !force)) return
        val request = ++generation
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val repository = application.awaitServices().userDataRepository
                val records = withContext(Dispatchers.IO) { repository.getFavoriteRecords() }
                    .sortedByDescending { it.lastAccessedAt }
                if (request == generation) {
                    _uiState.update { it.copy(loading = false, loaded = true, favorites = records, error = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == generation) {
                    _uiState.update { it.copy(loading = false, loaded = false, error = error.message ?: "FAVORITES_LOAD_FAILED") }
                }
            }
        }
    }

    fun remove(record: FavoriteRecord) {
        if (_uiState.value.mutatingFavoriteId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingFavoriteId = record.favoriteId, message = null, error = null) }
            try {
                val repository = application.awaitServices().userDataRepository
                val remaining = withContext(Dispatchers.IO) {
                    repository.getFavoriteRecords().filterNot { it.favoriteId == record.favoriteId }
                        .also(repository::saveFavoriteRecords)
                }
                _uiState.update {
                    it.copy(loading = false, loaded = true, favorites = remaining.sortedByDescending(FavoriteRecord::lastAccessedAt),
                        mutatingFavoriteId = null, message = "已移除收藏", error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingFavoriteId = null, error = error.message ?: "FAVORITE_REMOVE_FAILED") }
            }
        }
    }
}

data class FavoritesUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val favorites: List<FavoriteRecord> = emptyList(),
    val mutatingFavoriteId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

internal data class FavoritePresentation(
    val record: FavoriteRecord,
    val title: String,
    val subtitle: String,
    val isLive: Boolean,
)

internal fun FavoriteRecord.toPresentation(): FavoritePresentation {
    val json = toJson()
    val isLive = targetType.equals("channel", true) || targetType.equals("live", true) ||
        json.optString("kind").equals("live", true)
    val title = listOf("title", "name", "vod_name", "channelName", "label")
        .firstNotNullOfOrNull { key -> json.optString(key).trim().takeIf(String::isNotEmpty) }
        ?: targetId?.toString()?.takeIf(String::isNotBlank)
        ?: "未命名收藏"
    val source = listOf("sourceName", "source", "groupName", "category")
        .firstNotNullOfOrNull { key -> json.optString(key).trim().takeIf(String::isNotEmpty) }
    val target = targetId?.toString()?.takeIf { it.isNotBlank() }
    val subtitle = listOfNotNull(if (isLive) "直播频道" else "影视内容", source, target?.takeIf { it != title })
        .distinct().joinToString(" · ")
    return FavoritePresentation(this, title, subtitle, isLive)
}
