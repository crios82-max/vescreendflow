package com.senseflow.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0D9488)
private val Ink = Color(0xFF0F1C18)
private val Mist = Color(0xFFE8F2EE)

private val DarkColors = darkColorScheme(
    primary = Teal,
    background = Ink,
    surface = Ink,
    onPrimary = Mist,
    onBackground = Mist,
    onSurface = Mist,
)

private val LightColors = lightColorScheme(
    primary = Teal,
    background = Mist,
    surface = Mist,
    onPrimary = Mist,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun SenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
