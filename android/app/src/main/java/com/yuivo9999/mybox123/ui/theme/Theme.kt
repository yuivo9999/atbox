package com.yuivo9999.mybox123.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.yuivo9999.mybox123.MyBoxApplication

private val MyBoxDarkColorScheme = darkColorScheme(
    primary = MyBoxAccent,
    onPrimary = MyBoxBackground,
    secondary = MyBoxTextSecondary,
    onSecondary = MyBoxBackground,
    background = MyBoxBackground,
    onBackground = MyBoxTextPrimary,
    surface = MyBoxSurface,
    onSurface = MyBoxTextPrimary,
    surfaceVariant = MyBoxSurfaceVariant,
    onSurfaceVariant = MyBoxTextSecondary,
    outline = MyBoxOutline,
)

val LocalAppAppearance = staticCompositionLocalOf { AppAppearance() }

@Composable
fun MyBoxTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as? MyBoxApplication
    val appearance by AppAppearanceController.appearance.collectAsState()

    LaunchedEffect(application) {
        if (application != null) runCatching { AppAppearanceController.load(application) }
    }

    val baseTypography = Typography()
    val fontFamily = when (appearance.fontFamily) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    val scale = appearance.fontScale
    fun androidx.compose.ui.text.TextStyle.adjust() = copy(fontSize = fontSize.scaleBy(scale), fontFamily = fontFamily)
    val typography = baseTypography.copy(
        displayLarge = baseTypography.displayLarge.adjust(),
        displayMedium = baseTypography.displayMedium.adjust(),
        displaySmall = baseTypography.displaySmall.adjust(),
        headlineLarge = baseTypography.headlineLarge.adjust(),
        headlineMedium = baseTypography.headlineMedium.adjust(),
        headlineSmall = baseTypography.headlineSmall.adjust(),
        titleLarge = baseTypography.titleLarge.adjust(),
        titleMedium = baseTypography.titleMedium.adjust(),
        titleSmall = baseTypography.titleSmall.adjust(),
        bodyLarge = baseTypography.bodyLarge.adjust(),
        bodyMedium = baseTypography.bodyMedium.adjust(),
        bodySmall = baseTypography.bodySmall.adjust(),
        labelLarge = baseTypography.labelLarge.adjust(),
        labelMedium = baseTypography.labelMedium.adjust(),
        labelSmall = baseTypography.labelSmall.adjust(),
    )

    CompositionLocalProvider(LocalAppAppearance provides appearance) {
        MaterialTheme(
            colorScheme = MyBoxDarkColorScheme,
            typography = typography,
            content = content,
        )
    }
}

private fun TextUnit.scaleBy(scale: Float): TextUnit = this * scale
