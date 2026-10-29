package com.yuivo9999.mybox123.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Keeps root navigation state out of Activity and composable implementation details. */
class AppViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun navigateTo(destination: AppDestination) {
        _uiState.update { current -> current.copy(destination = destination) }
    }

    fun navigateHome() {
        navigateTo(AppDestination.HOME)
    }
}
