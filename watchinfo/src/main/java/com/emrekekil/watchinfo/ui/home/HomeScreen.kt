package com.emrekekil.watchinfo.ui.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.emrekekil.watchinfo.BuildConfig
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.data.Dashboard
import com.emrekekil.watchinfo.data.DeviceInfoRepository
import com.emrekekil.watchinfo.data.Format
import com.emrekekil.watchinfo.data.SystemCollector
import com.emrekekil.watchinfo.model.Category
import com.emrekekil.watchinfo.ui.components.LoadState
import com.emrekekil.watchinfo.ui.components.RingGauge
import com.emrekekil.watchinfo.ui.components.rememberPolled

private const val DASHBOARD_REFRESH_MILLIS = 3_000L

@Composable
fun HomeScreen(repository: DeviceInfoRepository, onOpen: (Category) -> Unit) {
    val state by rememberPolled(Unit, DASHBOARD_REFRESH_MILLIS) { repository.dashboard() }
    val dashboard = (state as? LoadState.Ready)?.value
    // Başlık ilk karede hazır olmalı: yüksekliği sonradan değişirse liste kayar.
    val context = LocalContext.current
    val deviceName = remember(context) { SystemCollector.deviceName(context) }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                Header(
                    deviceName = deviceName,
                    model = Build.MODEL,
                    modifier = Modifier.minimumVerticalContentPadding(
                        top = ListHeaderDefaults.minimumTopListContentPadding,
                        bottom = 0.dp,
                    ),
                )
            }
            // Göstergeler veri gelmeden de çizilir; sonradan eklenen bir öğe listeyi kaydırırdı.
            item { Gauges(dashboard) }
            Category.entries.forEach { category ->
                item(key = category.name) {
                    Button(
                        onClick = { onOpen(category) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = { Icon(painterResource(category.icon), contentDescription = null) },
                        // Özet satırı veri gelmeden de yer kaplar, böylece buton yüksekliği sabit kalır.
                        secondaryLabel = {
                            Text(
                                text = dashboard?.summaries?.get(category).orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        label = { Text(stringResource(category.title), maxLines = 1) },
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(deviceName: String, model: String, modifier: Modifier = Modifier) {
    // Yuvarlak ekranın üst kısmı dar olduğu için başlık saatin altına ve içeriye çekilir.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = deviceName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = model,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Gauges(dashboard: Dashboard?) {
    val colors = MaterialTheme.colorScheme
    val battery = dashboard?.battery
    val memory = dashboard?.memory?.fraction
    val storage = dashboard?.storage?.fraction
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        RingGauge(
            fraction = battery?.fraction ?: 0f,
            value = battery?.fraction.asPercent(),
            label = stringResource(R.string.gauge_battery),
            color = if (battery != null && battery.fraction <= 0.15f && !battery.charging) colors.error else colors.primary,
        )
        RingGauge(
            fraction = memory ?: 0f,
            value = memory.asPercent(),
            label = stringResource(R.string.gauge_ram),
            color = if (memory != null && memory >= 0.9f) colors.error else colors.secondary,
        )
        RingGauge(
            fraction = storage ?: 0f,
            value = storage.asPercent(),
            label = stringResource(R.string.gauge_storage),
            color = if (storage != null && storage >= 0.9f) colors.error else colors.tertiary,
        )
    }
}

private fun Float?.asPercent(): String = this?.let { Format.percent(it.toDouble()) } ?: "–"
