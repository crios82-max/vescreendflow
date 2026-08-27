package com.veplayer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Tesla-like dark cockpit palette */
val Night = Color(0xFF000000)
val Panel = Color(0xFF111111)
val Card = Color(0xFF1A1A1A)
val Teal = Color(0xFF3E9EFD) // map/route blue accent
val Accent = Color(0xFF18C964) // spotify/phone green
val Amber = Color(0xFFF5A623)
val Mist = Color(0xFFF2F2F2)
val Mute = Color(0xFF9A9A9A)
val Road = Color(0xFF2A2A2A)
val Lane = Color(0xFF5A5A5A)

private val Colors =
    darkColorScheme(
        primary = Teal,
        secondary = Accent,
        background = Night,
        surface = Panel,
        onPrimary = Mist,
        onSecondary = Night,
        onBackground = Mist,
        onSurface = Mist,
    )

@Composable
fun VePlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
