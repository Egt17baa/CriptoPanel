package app.criptopanel.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PositiveColor = Color(0xFF16A34A)
val NegativeColor = Color(0xFFDC2626)

/** Green for gains, red for losses, default text color otherwise. */
fun changeColor(value: Double?): Color = when {
    value == null || value == 0.0 -> Color.Unspecified
    value > 0 -> PositiveColor
    else -> NegativeColor
}

@Composable
fun CriptoPanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
