package com.yuivo9999.mybox123.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.ui.theme.AppAppearanceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Persists native settings without dropping fields not represented by this screen. */
class SettingsViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun load(force: Boolean = false) {
        if (_uiState.value.loading || (_uiState.value.loaded && !force)) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val settings = withContext(Dispatchers.IO) {
                    application.awaitServices().userDataRepository.getSettings()
                }
                AppAppearanceController.applySettings(settings)
                _uiState.update {
                    it.copy(
                        loading = false,
                        loaded = true,
                        autoplayResume = settings.optBoolean("autoplayResume", true),
                        theme = normalizeSupportedTheme(settings.optString("theme", "sangtian")),
                        fontSize = settings.optString("fontSize", "medium"),
                        fontFamily = settings.optString("fontFamily", "system"),
                        cardStyle = settings.optString("cardStyle", "poster"),
                        density = settings.optString("density", "comfortable"),
                        error = null,
                        message = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.message ?: "SETTINGS_LOAD_FAILED") }
            }
        }
    }

    fun setAutoplayResume(enabled: Boolean) = updateSetting("autoplayResume", enabled)
    fun setFontSize(value: String) = updateSetting("fontSize", value, allowed = setOf("small", "medium", "large"))
    fun setFontFamily(value: String) = updateSetting("fontFamily", value, allowed = setOf("system", "serif", "monospace"))
    fun setCardStyle(value: String) = updateSetting("cardStyle", value, allowed = setOf("poster", "list"))
    fun setDensity(value: String) = updateSetting("density", value, allowed = setOf("comfortable", "compact"))

    private fun updateSetting(key: String, value: Any, allowed: Set<String>? = null) {
        if (_uiState.value.saving) return
        if (allowed != null) {
            val stringValue = value as? String ?: return
            if (stringValue !in allowed) return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, message = null) }
            try {
                val repository = application.awaitServices().userDataRepository
                val updated = withContext(Dispatchers.IO) {
                    val settings = repository.getSettings()
                    settings.put(key, value)
                    repository.saveSettings(settings)
                    repository.getSettings()
                }
                AppAppearanceController.applySettings(updated)
                _uiState.update {
                    it.copy(
                        saving = false,
                        autoplayResume = updated.optBoolean("autoplayResume", true),
                        theme = normalizeSupportedTheme(updated.optString("theme", "sangtian")),
                        fontSize = updated.optString("fontSize", "medium"),
                        fontFamily = updated.optString("fontFamily", "system"),
                        cardStyle = updated.optString("cardStyle", "poster"),
                        density = updated.optString("density", "comfortable"),
                        message = "设置已保存。",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(saving = false, error = error.message ?: "SETTINGS_SAVE_FAILED") }
            }
        }
    }
}

data class SettingsUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val autoplayResume: Boolean = true,
    val theme: String = "sangtian",
    val fontSize: String = "medium",
    val fontFamily: String = "system",
    val cardStyle: String = "poster",
    val density: String = "comfortable",
    val error: String? = null,
    val message: String? = null,
)

/** The current native palette has one implemented theme; unknown legacy values must not be presented as active. */
private fun normalizeSupportedTheme(value: String): String = value.takeIf { it == "sangtian" } ?: "sangtian"
