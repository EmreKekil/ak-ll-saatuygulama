package com.emrekekil.watchinfo.ui.sensors

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.data.SensorCatalog
import com.emrekekil.watchinfo.ui.components.ListNote

/** Tüm sensörlerin listesi. [onOpen], sensörün SensorManager listesindeki sırasını verir. */
@Composable
fun SensorListScreen(onOpen: (Int) -> Unit) {
    val context = LocalContext.current
    // Türe göre sıralı gösterilir, ancak gezinmede SensorManager'daki asıl sıra kullanılır.
    val sensors = remember(context) {
        SensorCatalog.sensors(context)
            .withIndex()
            .sortedWith(compareBy({ it.value.type }, { it.value.name }))
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumVerticalContentPadding(top = ListHeaderDefaults.minimumTopListContentPadding, bottom = 0.dp)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.cat_sensors)) }
            }
            item {
                ListNote(pluralStringResource(R.plurals.sensor_count, sensors.size, sensors.size))
            }
            items(sensors, key = { it.index }) { entry ->
                val sensor = entry.value
                Button(
                    onClick = { onOpen(entry.index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    secondaryLabel = { Text(sensor.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    label = {
                        Text(SensorCatalog.typeName(context, sensor), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
    }
}
