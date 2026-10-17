package com.emrekekil.qibla.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import com.emrekekil.qibla.ui.theme.QiblaColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Döner pusula kadranı. Kadran gerçek kuzeye göre döner; saat 12'deki sabit işaret saatin baktığı
 * yönü gösterir. Kâbe işareti kadranın kenarında kıble açısında, güneş işareti güneşin yönündedir.
 *
 * @param trueHeading Saatin gerçek kuzeye göre yönü; yalnızca çizim aşamasında okunur.
 */
@Composable
fun CompassDial(
    trueHeading: () -> Double,
    qiblaBearing: Double,
    sunAzimuth: Double?,
    aligned: Boolean,
    cardinalLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val letterStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
    val northStyle = letterStyle.copy(color = colors.primary)
    val accent = if (aligned) QiblaColors.Aligned else QiblaColors.Kaaba

    Canvas(modifier) {
        val heading = trueHeading()
        val radius = size.minDimension / 2
        val c = center
        val headingKnown = !heading.isNaN()
        val dialRotation = if (headingKnown) -heading.toFloat() else 0f

        // Kadran halkası ve çentikler (gerçek kuzeye göre döner).
        rotate(dialRotation, c) {
            for (deg in 0 until 360 step 5) {
                val major = deg % 30 == 0
                val length = if (major) 10.dp.toPx() else 5.dp.toPx()
                val outer = radius - 3.dp.toPx()
                val color = if (deg == 0) colors.primary else colors.outline
                drawRadialLine(c, deg.toDouble(), outer - length, outer, color, if (major) 2.dp.toPx() else 1.dp.toPx())
            }
        }

        // Ana yön harfleri dik kalsın diye konumları hesaplanıp döndürülmeden çizilir.
        cardinalLabels.forEachIndexed { i, label ->
            val angle = i * 90.0 + dialRotation
            val layout = measurer.measure(label, if (i == 0) northStyle else letterStyle)
            val p = polar(c, radius - 24.dp.toPx(), angle)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }

        // Güneş işareti.
        if (sunAzimuth != null && headingKnown) {
            val p = polar(c, radius - 12.dp.toPx(), sunAzimuth + dialRotation)
            drawCircle(QiblaColors.Sun, 4.dp.toPx(), p)
            for (ray in 0 until 360 step 45) {
                drawRadialLine(p, ray.toDouble(), 6.dp.toPx(), 8.5.dp.toPx(), QiblaColors.Sun, 1.5.dp.toPx())
            }
        }

        // Kâbe işareti (kenarda, kıble açısında).
        val kaabaAngle = qiblaBearing + dialRotation
        val kaaba = polar(c, radius - 13.dp.toPx(), kaabaAngle)
        val half = 6.dp.toPx()
        drawRoundRect(
            color = Color(0xFF111111),
            topLeft = Offset(kaaba.x - half, kaaba.y - half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        drawRect(accent, Offset(kaaba.x - half, kaaba.y - half * 0.45f), Size(half * 2, half * 0.35f))
        drawRoundRect(
            color = accent,
            topLeft = Offset(kaaba.x - half, kaaba.y - half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Stroke(1.dp.toPx()),
        )

        // Kıble oku: ortadaki diskin dışından Kâbe işaretine doğru.
        if (headingKnown) {
            rotate(kaabaAngle.toFloat(), c) {
                val inner = radius * 0.40f
                val tip = radius * 0.70f
                drawLine(accent, Offset(c.x, c.y - inner), Offset(c.x, c.y - tip + 6.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
                val head = Path().apply {
                    moveTo(c.x, c.y - tip)
                    lineTo(c.x - 9.dp.toPx(), c.y - tip + 14.dp.toPx())
                    lineTo(c.x + 9.dp.toPx(), c.y - tip + 14.dp.toPx())
                    close()
                }
                drawPath(head, accent)
            }
        }

        // Saatin baktığı yönü gösteren sabit işaret (saat 12).
        val marker = Path().apply {
            moveTo(c.x, c.y - radius + 18.dp.toPx())
            lineTo(c.x - 7.dp.toPx(), c.y - radius + 4.dp.toPx())
            lineTo(c.x + 7.dp.toPx(), c.y - radius + 4.dp.toPx())
            close()
        }
        drawPath(marker, if (aligned) QiblaColors.Aligned else colors.onSurface)
    }
}

/** Kuzeyden saat yönünde [degrees] açısında, merkezden [r] uzaklıktaki nokta. */
private fun polar(center: Offset, r: Float, degrees: Double): Offset {
    val rad = Math.toRadians(degrees)
    return Offset(center.x + r * sin(rad).toFloat(), center.y - r * cos(rad).toFloat())
}

private fun DrawScope.drawRadialLine(center: Offset, degrees: Double, from: Float, to: Float, color: Color, width: Float) {
    drawLine(color, polar(center, from, degrees), polar(center, to, degrees), width, StrokeCap.Round)
}
