package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val KanakLightColorScheme = lightColorScheme(
    primary = KanakPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = KanakAccentLight,
    onPrimaryContainer = KanakPrimaryBlue,
    background = KanakBackground,
    onBackground = KanakPrimaryText,
    surface = KanakSurface,
    onSurface = KanakPrimaryText,
    surfaceVariant = KanakSurfaceVariant,
    onSurfaceVariant = KanakSecondaryText,
    outline = KanakDivider,
    error = KanakError,
    onError = Color.White
)

@Composable
fun KanakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KanakLightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    KanakTheme(darkTheme = darkTheme, content = content)
}
