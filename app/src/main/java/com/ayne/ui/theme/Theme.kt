package com.ayne.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Ayne accessibility color scheme: pure black (#000000) & vibrant yellow (#FFD600)
// WCAG AAA contrast standard
private val AyneColorScheme = darkColorScheme(
    primary = AyneVibrantYellow,
    onPrimary = AyneOnYellow,
    primaryContainer = AyneVibrantYellow,
    onPrimaryContainer = AyneOnYellow,
    secondary = AyneVibrantYellow,
    onSecondary = AyneOnYellow,
    background = AynePureBlack,
    onBackground = AynePureWhite,
    surface = AynePureBlack,
    onSurface = AynePureWhite,
    surfaceVariant = AyneDarkCard,
    onSurfaceVariant = AynePureWhite,
    outline = AyneVibrantYellow
)

@Composable
fun AyneTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AyneColorScheme,
        typography = Typography,
        content = content
    )
}

// Retained for backward-compatibility with default template
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AyneTheme(content = content)
}
