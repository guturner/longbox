package com.guturner.longbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Newsprint and ink: off-white paper, near-black ink, a single bold accent.
private val Ink = Color(0xFF1B1B1F)
private val Paper = Color(0xFFF6F1E7)
private val Accent = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E2D3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6E66),
    background = Color(0xFF121214),
    surface = Color(0xFF121214),
    surfaceVariant = Color(0xFF26262B),
)

@Composable
fun LongboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
