package com.yuivo9999.mybox123.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MyBoxGold,
    secondary = MyBoxAmber,
    tertiary = MyBoxGoldLight,
    background = MyBoxBackground,
    surface = MyBoxSurface,
    onPrimary = MyBoxBackground,
    onSecondary = MyBoxBackground,
    onTertiary = MyBoxBackground,
    onBackground = MyBoxTextPrimary,
    onSurface = MyBoxTextPrimary,
    surfaceVariant = MyBoxSurfaceVariant,
    onSurfaceVariant = MyBoxTextSecondary,
    outline = MyBoxOutline,
)

@Composable
fun MyBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
