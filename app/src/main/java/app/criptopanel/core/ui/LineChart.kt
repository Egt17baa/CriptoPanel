package app.criptopanel.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Minimal line chart scaled to the min/max of [points]; draws nothing for fewer than 2 points. */
@Composable
fun LineChart(points: List<Double>, modifier: Modifier, color: Color, fill: Boolean = false) {
    if (points.size < 2) {
        Spacer(modifier)
        return
    }
    Canvas(modifier) {
        val min = points.min()
        val range = (points.max() - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val line = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range * size.height).toFloat()
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
    }
}
