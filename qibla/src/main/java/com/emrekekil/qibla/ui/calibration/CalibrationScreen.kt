package com.emrekekil.qibla.ui.calibration

import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.emrekekil.qibla.R
import com.emrekekil.qibla.sensor.MagQuality
import com.emrekekil.qibla.ui.compass.CompassState
import com.emrekekil.qibla.ui.theme.QiblaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Sekiz çizerek manyetometre kalibrasyonu; durum canlı gösterilir. */
@Composable
fun CalibrationScreen(compass: CompassState, onDone: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "figure8")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val ready = compass.magAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
        (compass.quality == MagQuality.GOOD || compass.quality == MagQuality.UNKNOWN)
    val outline = MaterialTheme.colorScheme.outline
    val dot = if (ready) QiblaColors.Aligned else MaterialTheme.colorScheme.primary

    ScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.calibrate), style = MaterialTheme.typography.titleMedium)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                // Sekiz (lemniskat) eğrisi ve üzerinde dolaşan nokta.
                val a = size.width * 0.38f
                val c = center
                fun point(t: Float): Offset {
                    val d = 1 + sin(t) * sin(t)
                    return Offset(c.x + a * cos(t) / d, c.y + a * sin(t) * cos(t) / d * 1.2f)
                }
                val path = Path()
                for (i in 0..100) {
                    val p = point((i / 100f) * 2 * PI.toFloat())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, outline, style = Stroke(2.dp.toPx()))
                drawCircle(dot, 6.dp.toPx(), point(phase))
            }
            Text(
                stringResource(if (ready) R.string.calibration_done else R.string.calibration_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = if (ready) QiblaColors.Aligned else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            CompactButton(onClick = onDone, label = { Text(stringResource(R.string.done)) })
        }
    }
}
