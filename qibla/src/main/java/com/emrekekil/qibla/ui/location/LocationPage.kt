package com.emrekekil.qibla.ui.location

import android.content.res.Resources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.emrekekil.qibla.R
import com.emrekekil.qibla.data.District
import com.emrekekil.qibla.data.GpsCheck
import com.emrekekil.qibla.data.GpsState
import com.emrekekil.qibla.data.QiblaInfo
import com.emrekekil.qibla.ui.Labels
import com.emrekekil.qibla.ui.components.ListPage
import com.emrekekil.qibla.ui.components.infoItem
import com.emrekekil.qibla.ui.components.note
import kotlin.math.abs

/** GPS'in bu uzaklığın ötesindeki farkı, seçilen ilçenin yanlış olduğunu düşündürür. */
private const val SUGGEST_GPS_KM = 15.0

@Composable
fun LocationPage(
    info: QiblaInfo?,
    manual: District?,
    gps: GpsState,
    gpsCheck: GpsCheck?,
    onUseGps: () -> Unit,
    onStopGps: () -> Unit,
    onPickCity: () -> Unit,
    onUseAutomatic: () -> Unit,
) {
    val res = LocalResources.current
    val errorColor = MaterialTheme.colorScheme.error
    val okColor = MaterialTheme.colorScheme.primary
    ListPage(stringResource(R.string.page_location)) { spec ->
        infoItem(
            "current", res.getString(R.string.label_current_location),
            info?.let { Labels.source(res, it) } ?: res.getString(R.string.value_none), spec,
            note = res.getString(if (manual != null) R.string.note_mode_manual else R.string.note_mode_auto),
        )

        val (status, statusNote) = gpsStatus(res, gps)
        infoItem("gps", res.getString(R.string.label_gps), status, spec, note = statusNote)

        if (gpsCheck != null) {
            val far = gpsCheck.distanceKm > SUGGEST_GPS_KM
            infoItem(
                "check", res.getString(R.string.label_gps_check),
                res.getString(
                    R.string.value_gps_check,
                    Labels.distanceKm(gpsCheck.distanceKm),
                    Labels.degrees(abs(gpsCheck.bearingDifference), 1),
                ),
                spec,
                note = res.getString(if (far) R.string.note_gps_check_far else R.string.note_gps_check_ok),
                valueColor = if (far) errorColor else okColor,
            )
        }

        item(key = "gps_button") {
            val searching = gps is GpsState.Searching
            FilledTonalButton(
                onClick = if (searching) onStopGps else onUseGps,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                label = { Text(stringResource(if (searching) R.string.stop_gps else R.string.use_gps)) },
            )
        }
        item(key = "pick") {
            FilledTonalButton(
                onClick = onPickCity,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.filledTonalButtonColors(),
                label = { Text(stringResource(R.string.pick_city)) },
                secondaryLabel = manual?.let { { Text(it.displayName) } },
            )
        }
        if (manual != null) {
            item(key = "auto") {
                OutlinedButton(
                    onClick = onUseAutomatic,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.use_automatic)) },
                )
            }
        }
        note("tip", res.getString(R.string.note_gps_tip))
    }
}

private fun gpsStatus(res: Resources, gps: GpsState): Pair<String, String?> = when (gps) {
    GpsState.Idle -> res.getString(R.string.gps_idle) to null
    GpsState.NoPermission -> res.getString(R.string.gps_no_permission) to res.getString(R.string.note_gps_permission)
    GpsState.LocationOff -> res.getString(R.string.gps_off) to res.getString(R.string.note_gps_off)
    GpsState.NoHardware -> res.getString(R.string.gps_no_hardware) to null
    is GpsState.Searching -> res.getString(R.string.gps_searching, gps.elapsedMs / 1000) to
        gps.satellites?.let { res.getString(R.string.note_satellites, it.usedInFix, it.inView) }
    is GpsState.Fixed -> Labels.gps(res, gps.fix) to
        gps.satellites?.let { res.getString(R.string.note_satellites, it.usedInFix, it.inView) }
    is GpsState.TimedOut -> res.getString(R.string.gps_timeout) to res.getString(R.string.note_gps_timeout)
}
