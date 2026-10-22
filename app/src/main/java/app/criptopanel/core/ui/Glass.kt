package app.criptopanel.core.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val GlassShape = RoundedCornerShape(24.dp)

/** Rim of a glass panel: catches more light at the top edge. */
val GlassRim = BorderStroke(
    1.dp,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))),
)

/** A Material card made of frosted glass. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = GlassFill)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = GlassShape, colors = colors, border = GlassRim, content = content)
    } else {
        Card(modifier = modifier, shape = GlassShape, colors = colors, border = GlassRim, content = content)
    }
}

private data class Blob(val color: Color, val radius: Float, val phase: Float, val speed: Float)

// Never brighter than #33343A: text contrast on glass is measured against it.
private val blobs = listOf(
    Blob(Color(0xFF33343A), 0.75f, 0.00f, 1f),
    Blob(Color(0xFF2C2D33), 0.65f, 0.33f, 2f),
    Blob(Color(0xFF26272C), 0.85f, 0.66f, 1f),
)

/** Slowly drifting, heavily blurred grey blobs on near-black: the "liquid" behind the glass. Static when animations are off. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val time: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "liquid").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
            label = "liquid-time",
        )
    } else {
        remember { mutableFloatStateOf(0.15f) }
    }
    // blur() needs Android 12+; earlier versions ignore it and the radial gradients alone stay soft.
    Canvas(modifier.fillMaxSize().blur(80.dp)) {
        drawRect(InkColor)
        val t = time.value // Read while drawing: each frame redraws without recomposing.
        val side = min(size.width, size.height)
        blobs.forEach { b ->
            val angle = 2 * PI * (t * b.speed + b.phase)
            val center = Offset(
                size.width * (0.5f + 0.32f * sin(angle).toFloat()),
                size.height * (0.5f + 0.36f * cos(angle * 0.5 + b.phase).toFloat()),
            )
            val radius = side * b.radius
            drawCircle(Brush.radialGradient(listOf(b.color, b.color.copy(alpha = 0f)), center, radius), radius, center)
        }
    }
}
