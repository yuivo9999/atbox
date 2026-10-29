package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.FavoriteRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FavoritesUiState(
    val loading: Boolean = false,
    val favorites: List<FavoriteRecord> = emptyList(),
    val error: String? = null
)

class FavoritesViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val list = withContext(Dispatchers.IO) {
                    application.awaitServices().userDataRepository.getFavoriteRecords()
                }
                _uiState.update { it.copy(loading = false, favorites = list) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun remove(record: FavoriteRecord) {
        removeFavorite(record.targetId?.toString() ?: record.favoriteId)
    }

    fun removeFavorite(targetId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repo = application.awaitServices().userDataRepository
                    val updated = repo.getFavoriteRecords().filter { (it.targetId?.toString() ?: it.favoriteId) != targetId }
                    repo.saveFavoriteRecords(updated)
                }
                load(force = true)
            } catch (_: Exception) {}
        }
    }
}
