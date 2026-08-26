package com.veplayer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF0B1220)
val Panel = Color(0xFF151E2E)
val Teal = Color(0xFF2DD4BF)
val Amber = Color(0xFFFBBF24)
val Mist = Color(0xFFE8EEF7)
val Mute = Color(0xFF94A3B8)

private val Colors =
    darkColorScheme(
        primary = Teal,
        secondary = Amber,
        background = Night,
        surface = Panel,
        onPrimary = Night,
        onSecondary = Night,
        onBackground = Mist,
        onSurface = Mist,
    )

@Composable
fun VePlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
