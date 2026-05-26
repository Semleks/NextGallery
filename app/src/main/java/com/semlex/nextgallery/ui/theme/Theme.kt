package com.semlex.nextgallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CloudGreen,
    onPrimary = Color(0xFF062116),
    primaryContainer = CloudGreenDeep,
    onPrimaryContainer = Color(0xFFD8FBE6),
    secondary = SignalAmber,
    onSecondary = Color(0xFF2B1800),
    background = Ink,
    onBackground = Mist,
    surface = Graphite,
    onSurface = Mist,
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = Muted,
    outline = Color(0xFF3A4048),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0707)
)

@Composable
fun NextGalleryTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
