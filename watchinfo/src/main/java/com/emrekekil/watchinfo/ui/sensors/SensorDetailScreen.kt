package com.emrekekil.watchinfo.ui.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.data.Format
import com.emrekekil.watchinfo.data.SensorCatalog
import com.emrekekil.watchinfo.ui.components.InfoListScreen
import com.emrekekil.watchinfo.ui.components.ListNote
import com.emrekekil.watchinfo.ui.components.LoadState
import kotlinx.coroutines.delay

private const val UI_UPDATE_INTERVAL_MS = 200L
private const val NO_DATA_TIMEOUT_MS = 3_000L

private sealed interface LiveState {
    data object Waiting : LiveState
    data object Unavailable : LiveState
    data object NoData : LiveState
    class Values(val values: FloatArray) : LiveState
}

@Composable
fun SensorDetailScreen(index: Int) {
    val context = LocalContext.current
    val sensor = remember(index) { SensorCatalog.sensors(context).getOrNull(index) }
    if (sensor == null) {
        InfoListScreen(title = stringResource(R.string.cat_sensors), state = LoadState.Ready(emptyList()), live = false)
        return
    }
    val details = remember(sensor) { LoadState.Ready(SensorCatalog.details(context, sensor)) }
    val live = rememberLiveValues(sensor)
    val labels = remember(sensor) { SensorCatalog.valueLabels(context, sensor) }

    InfoListScreen(
        title = sensor.name,
        state = details,
        live = live is LiveState.Values,
        extraContent = { spec ->
            item(key = "live") {
                when (live) {
                    is LiveState.Values -> Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        val count = labels?.size?.coerceAtMost(live.values.size) ?: live.values.size.coerceAtMost(6)
                        for (i in 0 until count) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = labels?.getOrNull(i) ?: "v$i",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = Format.decimal(live.values[i].toDouble(), 2),
                                    style = MaterialTheme.typography.numeralExtraSmall,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                    LiveState.Waiting -> ListNote(stringResource(R.string.sensor_waiting))
                    LiveState.NoData -> ListNote(stringResource(R.string.sensor_no_data))
                    LiveState.Unavailable -> ListNote(stringResource(R.string.sensor_unavailable))
                }
            }
        },
    )
}

/**
 * Sensörü ekran görünürken dinler. Arayüz saniyede en fazla 5 kez güncellenir;
 * sensörün kendi hızında yeniden çizim yapmak gereksiz pil tüketir.
 */
@Composable
private fun rememberLiveValues(sensor: Sensor): LiveState {
    val context = LocalContext.current
    var state by remember(sensor) { mutableStateOf<LiveState>(LiveState.Waiting) }

    LifecycleStartEffect(sensor) {
        val manager = context.getSystemService(SensorManager::class.java)
        var lastUpdate = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate >= UI_UPDATE_INTERVAL_MS) {
                    lastUpdate = now
                    state = LiveState.Values(event.values.copyOf())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        // Tek seferlik sensörler (ör. önemli hareket) dinleyici ile değil tetikleyici ile çalışır.
        val registered = sensor.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT &&
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        if (!registered) state = LiveState.Unavailable
        onStopOrDispose { manager.unregisterListener(listener) }
    }

    // Kayıt başarılı ama veri gelmiyorsa (izin gerekiyor veya olay bekleniyor) kullanıcıya söyle.
    LaunchedEffect(sensor, state is LiveState.Waiting) {
        if (state is LiveState.Waiting) {
            delay(NO_DATA_TIMEOUT_MS)
            if (state is LiveState.Waiting) state = LiveState.NoData
        }
    }
    return state
}
