package com.yuivo9999.mybox123.ui.theme

import com.yuivo9999.mybox123.MyBoxApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Shared runtime appearance snapshot for native Compose screens. */
data class AppAppearance(
    val fontSize: String = "medium",
    val fontFamily: String = "system",
    val cardStyle: String = "poster",
    val density: String = "comfortable",
) {
    val fontScale: Float get() = when (fontSize) { "small" -> 0.92f; "large" -> 1.12f; else -> 1f }
    val compact: Boolean get() = density == "compact"
    val listCards: Boolean get() = cardStyle == "list"
    val outerPadding: Int get() = if (compact) 12 else 16
    val itemSpacing: Int get() = if (compact) 6 else 10
    val cardPadding: Int get() = if (compact) 10 else 14

    companion object {
        fun fromSettings(settings: JSONObject) = AppAppearance(
            fontSize = settings.optString("fontSize", "medium").takeIf { it in setOf("small", "medium", "large") } ?: "medium",
            fontFamily = settings.optString("fontFamily", "system").takeIf { it in setOf("system", "serif", "monospace") } ?: "system",
            cardStyle = settings.optString("cardStyle", "poster").takeIf { it in setOf("poster", "list") } ?: "poster",
            density = settings.optString("density", "comfortable").takeIf { it in setOf("comfortable", "compact") } ?: "comfortable",
        )
    }
}

object AppAppearanceController {
    private val mutableAppearance = MutableStateFlow(AppAppearance())
    val appearance: StateFlow<AppAppearance> = mutableAppearance.asStateFlow()

    fun applySettings(settings: JSONObject) {
        mutableAppearance.value = AppAppearance.fromSettings(settings)
    }

    suspend fun load(application: MyBoxApplication) {
        val settings = withContext(Dispatchers.IO) {
            application.awaitServices().userDataRepository.getSettings()
        }
        applySettings(settings)
    }
}
