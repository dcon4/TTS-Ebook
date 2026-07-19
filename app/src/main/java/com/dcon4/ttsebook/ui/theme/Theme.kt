package com.dcon4.ttsebook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBB86FC),
    primaryContainer = Color(0xFF333333),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF000000),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF2A2A2A),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFAAAAAA)
)

@Composable
fun TtsEbookTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
