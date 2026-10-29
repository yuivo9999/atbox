package com.yuivo9999.mybox123.app

/** Immutable UI state for the native application shell. */
data class AppUiState(
    val destination: AppDestination = AppDestination.HOME,
)
