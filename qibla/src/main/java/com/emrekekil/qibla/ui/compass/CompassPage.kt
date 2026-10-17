package com.emrekekil.qibla.ui.compass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.emrekekil.qibla.R
import com.emrekekil.qibla.data.QiblaInfo
import com.emrekekil.qibla.geo.Angles
import com.emrekekil.qibla.geo.Solar
import com.emrekekil.qibla.sensor.MagQuality
import com.emrekekil.qibla.ui.Labels
import com.emrekekil.qibla.ui.theme.QiblaColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val KEEP_SCREEN_ON_MS = 120_000L

@Composable
fun CompassPage(
    info: QiblaInfo?,
    state: CompassState,
    active: Boolean,
    onCalibrate: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    // Pusula ekranında saat gösterilmez: üstteki yön işaretiyle çakışırdı.
    ScreenScaffold(timeText = {}) {
        if (info == null) {
            NoLocation(onOpenLocation)
        } else {
            Compass(info, state, active, onCalibrate, onOpenLocation)
        }
    }
}

@Composable
private fun Compass(
    info: QiblaInfo,
    state: CompassState,
    active: Boolean,
    onCalibrate: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    val res = LocalResources.current
    val sunAzimuth by produceState<Double?>(null, info.position) {
        while (true) {
            val sun = Solar.position(System.currentTimeMillis(), info.position.latitude, info.position.longitude)
            value = sun.azimuth.takeIf { sun.altitude > 0 }
            delay(60_000)
        }
    }
    // Ekranı açık tut, ancak dokunulmadan 2 dakika geçerse bırak (pil).
    var touches by remember { mutableIntStateOf(0) }
    var keepOn by remember { mutableStateOf(true) }
    LaunchedEffect(touches, active) {
        keepOn = true
        delay(KEEP_SCREEN_ON_MS)
        keepOn = false
    }
    // Merkezdeki yazı yalnızca tam derece değiştiğinde yeniden oluşturulur.
    val error by remember(info) {
        derivedStateOf {
            val heading = state.magneticHeading
            if (heading.isNaN()) null else Angles.signedDelta(heading + info.declination, info.bearing).roundToInt()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (active && keepOn) Modifier.keepScreenOn() else Modifier)
            .clickable(interactionSource = null, indication = null) { touches++ },
    ) {
        CompassDial(
            trueHeading = { state.magneticHeading + info.declination },
            qiblaBearing = info.bearing,
            sunAzimuth = sunAzimuth,
            aligned = state.aligned,
            cardinalLabels = stringArrayResource(R.array.cardinals).toList(),
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val e = error
            when {
                !state.available -> CenterText(stringResource(R.string.compass_unavailable), Labels.degrees(info.bearing))
                state.tooSteep -> CenterText(stringResource(R.string.hold_flat), null)
                e == null -> CenterText(stringResource(R.string.compass_starting), null)
                state.aligned -> {
                    Text(
                        stringResource(R.string.aligned),
                        style = MaterialTheme.typography.titleMedium,
                        color = QiblaColors.Aligned,
                    )
                    Text("✓", style = MaterialTheme.typography.displaySmall, color = QiblaColors.Aligned)
                }
                else -> {
                    Text(
                        stringResource(if (e > 0) R.string.turn_right else R.string.turn_left),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${abs(e)}°", style = MaterialTheme.typography.displayMedium)
                }
            }
            Text(
                stringResource(R.string.qibla_bearing_short, Labels.degrees(info.bearing)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Üstte: kalibrasyon veya parazit uyarısı.
        val warning = when {
            state.quality == MagQuality.NEEDS_CALIBRATION -> R.string.warn_calibrate
            state.quality == MagQuality.BAD -> R.string.warn_interference
            else -> null
        }
        if (warning != null) {
            CompactButton(
                onClick = onCalibrate,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(),
                label = { Text(stringResource(warning), maxLines = 1) },
            )
        } else if (state.tilted && !state.tooSteep) {
            Text(
                stringResource(R.string.hold_flat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 34.dp),
            )
        }

        // Altta: konum kaynağı; dokununca konum sayfası.
        Text(
            text = Labels.source(res, info),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp, start = 48.dp, end = 48.dp)
                .clickable(onClick = onOpenLocation),
        )
    }
}

@Composable
private fun CenterText(title: String, subtitle: String?) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 56.dp),
    )
    subtitle?.let { Text(it, style = MaterialTheme.typography.displaySmall) }
}

@Composable
private fun NoLocation(onOpenLocation: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.no_location_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.no_location_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        FilledTonalButton(
            onClick = onOpenLocation,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.set_location)) },
        )
    }
}
