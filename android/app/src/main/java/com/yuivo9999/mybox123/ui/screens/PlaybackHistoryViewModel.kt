package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.HistoryRecord
import com.yuivo9999.mybox123.data.storage.PlaybackProgressRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read-only native history/progress projection. Playback-generated writes belong to Stages 4/5. */
class PlaybackHistoryViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaybackHistoryUiState())
    val uiState: StateFlow<PlaybackHistoryUiState> = _uiState.asStateFlow()
    private var generation = 0L

    fun load(force: Boolean = false) {
        if (_uiState.value.loading || (_uiState.value.loaded && !force)) return
        val request = ++generation
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val repository = application.awaitServices().userDataRepository
                val snapshot = withContext(Dispatchers.IO) {
                    repository.getHistoryRecords().sortedByDescending(HistoryRecord::lastPlayedAt) to
                        repository.getProgressRecords().sortedByDescending(PlaybackProgressRecord::updatedAt)
                }
                if (request == generation) {
                    _uiState.update {
                        it.copy(loading = false, loaded = true, history = snapshot.first, progress = snapshot.second, error = null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == generation) {
                    _uiState.update { it.copy(loading = false, loaded = false, error = error.message ?: "PLAYBACK_HISTORY_LOAD_FAILED") }
                }
            }
        }
    }
}

data class PlaybackHistoryUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val history: List<HistoryRecord> = emptyList(),
    val progress: List<PlaybackProgressRecord> = emptyList(),
    val error: String? = null,
)
