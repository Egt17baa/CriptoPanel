package app.criptopanel.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Contrast checked against the brightest spot glass can sit on (#43444A): text 8.2:1, secondary 4.7:1,
// gains 5.6:1, losses 5.1:1. See docs/superpowers/specs/2026-10-22-criptopanel-mas-mercado-y-defi-design.md.
val PositiveColor = Color(0xFF4ADE80)
val NegativeColor = Color(0xFFFCA5A5)

val InkColor = Color(0xFF0B0B0D)
private val TextColor = Color(0xFFECECEE)
private val SecondaryTextColor = Color(0xFFB4B5BB)
private val SilverColor = Color(0xFFD7DAE0)

/** Menus, dialogs and pickers float over lists: they need an opaque surface to stay readable. */
val MenuColor = Color(0xFF1F2024)

/** Glass panel fill: never above 8 % white, or secondary text loses contrast. */
val GlassFill = Color.White.copy(alpha = 0.075f)

/** Green for gains, red for losses, default text color otherwise. */
fun changeColor(value: Double?): Color = when {
    value == null || value == 0.0 -> Color.Unspecified
    value > 0 -> PositiveColor
    else -> NegativeColor
}

// Always dark. Background and surface are transparent so the liquid background shows through every
// Scaffold and top bar; containers (cards, navigation bar, chips) are frosted glass.
private val GlassColors = darkColorScheme(
    primary = SilverColor,
    onPrimary = InkColor,
    primaryContainer = Color(0xFF2A2B30),
    onPrimaryContainer = TextColor,
    secondary = SecondaryTextColor,
    onSecondary = InkColor,
    secondaryContainer = Color.White.copy(alpha = 0.14f),
    onSecondaryContainer = TextColor,
    tertiary = SilverColor,
    background = Color.Transparent,
    onBackground = TextColor,
    surface = Color.Transparent,
    onSurface = TextColor,
    surfaceVariant = GlassFill,
    onSurfaceVariant = SecondaryTextColor,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = GlassFill,
    surfaceContainerLow = GlassFill,
    surfaceContainer = GlassFill,
    surfaceContainerHigh = GlassFill,
    surfaceContainerHighest = GlassFill,
    inverseSurface = TextColor,
    inverseOnSurface = InkColor,
    outline = Color.White.copy(alpha = 0.24f),
    outlineVariant = Color.White.copy(alpha = 0.12f),
    error = NegativeColor,
    onError = InkColor,
    errorContainer = Color(0x40F87171),
    onErrorContainer = TextColor,
)

@Composable
fun CriptoPanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GlassColors, content = content)
}
