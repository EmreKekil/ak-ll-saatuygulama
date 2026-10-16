package com.emrekekil.watchinfo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/** Saat dinamik renkleri desteklemiyorsa kullanılan sade, soğuk tonlu palet. */
private val WatchInfoColors = ColorScheme(
    primary = Color(0xFF8CCDFF),
    primaryDim = Color(0xFF6FB3E8),
    primaryContainer = Color(0xFF173A52),
    onPrimary = Color(0xFF00344F),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFB6C9D8),
    secondaryContainer = Color(0xFF26323C),
    onSecondaryContainer = Color(0xFFD2E5F4),
    tertiary = Color(0xFFFFC38A),
    tertiaryContainer = Color(0xFF4A2D0F),
    onTertiaryContainer = Color(0xFFFFDCBF),
    surfaceContainerLow = Color(0xFF111417),
    surfaceContainer = Color(0xFF1B1F23),
    surfaceContainerHigh = Color(0xFF262B30),
    onSurface = Color(0xFFE6EAEE),
    onSurfaceVariant = Color(0xFFA9B2BB),
    outline = Color(0xFF6F7880),
    outlineVariant = Color(0xFF3F474E),
    background = Color.Black,
    onBackground = Color(0xFFE6EAEE),
)

@Composable
fun WatchInfoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Wear OS 6 saat kadranından türetilen dinamik renkleri destekler; yoksa kendi paletimiz.
    val colors = remember(context) { dynamicColorScheme(context) } ?: WatchInfoColors
    MaterialTheme(colorScheme = colors, content = content)
}
