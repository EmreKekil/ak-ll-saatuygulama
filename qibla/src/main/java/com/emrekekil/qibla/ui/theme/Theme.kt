package com.emrekekil.qibla.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/** Kâbe işaretçisi ve hizalanma vurgusu için sabit renkler (dinamik temadan bağımsız). */
object QiblaColors {
    val Kaaba = Color(0xFFE9C46A)
    val Aligned = Color(0xFF6EDC9E)
    val Sun = Color(0xFFFFB74D)
}

/** Saat dinamik renkleri desteklemiyorsa kullanılan sade, yeşil tonlu palet. */
private val QiblaColorScheme = ColorScheme(
    primary = Color(0xFF7DD8A8),
    primaryDim = Color(0xFF5FBF8E),
    primaryContainer = Color(0xFF113D2A),
    onPrimary = Color(0xFF00391F),
    onPrimaryContainer = Color(0xFFB8F2D0),
    secondary = Color(0xFFB4CCBE),
    secondaryContainer = Color(0xFF24332B),
    onSecondaryContainer = Color(0xFFD0E8DA),
    tertiary = Color(0xFFE9C46A),
    tertiaryContainer = Color(0xFF473811),
    onTertiaryContainer = Color(0xFFFFE3A3),
    surfaceContainerLow = Color(0xFF101512),
    surfaceContainer = Color(0xFF1A201C),
    surfaceContainerHigh = Color(0xFF242B26),
    onSurface = Color(0xFFE4EAE6),
    onSurfaceVariant = Color(0xFFA8B3AC),
    outline = Color(0xFF6E7A72),
    outlineVariant = Color(0xFF3E4842),
    background = Color.Black,
    onBackground = Color(0xFFE4EAE6),
)

@Composable
fun QiblaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(context) { dynamicColorScheme(context) } ?: QiblaColorScheme
    MaterialTheme(colorScheme = colors, content = content)
}
