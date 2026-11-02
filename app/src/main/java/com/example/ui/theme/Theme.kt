package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Color Scheme explicitly matching Board 1 to Board 9
private val KanakLightColorScheme = lightColorScheme(
    primary = KanakPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = KanakAccentLight,
    onPrimaryContainer = KanakPrimaryBlue,
    secondary = KanakSecondaryText,
    onSecondary = Color.White,
    background = KanakBackground,
    onBackground = KanakPrimaryText,
    surface = KanakSurface,
    onSurface = KanakPrimaryText,
    surfaceVariant = KanakSurfaceVariant,
    onSurfaceVariant = KanakSecondaryText,
    outline = KanakDivider,
    outlineVariant = KanakDivider,
    error = KanakError,
    onError = Color.White,
    errorContainer = KanakErrorBackground,
    onErrorContainer = KanakError
)

@Composable
fun KanakTheme(
    darkTheme: Boolean = false, // Always enforce the official Light Theme from the 9 design boards!
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KanakLightColorScheme,
        typography = Typography,
        content = content
    )
}
