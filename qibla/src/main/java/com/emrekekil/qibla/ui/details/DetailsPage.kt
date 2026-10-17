package com.emrekekil.qibla.ui.details

import android.hardware.SensorManager
import android.text.format.DateFormat
import android.view.Surface
import android.content.res.Resources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.emrekekil.qibla.R
import com.emrekekil.qibla.data.PositionSource
import com.emrekekil.qibla.data.QiblaInfo
import com.emrekekil.qibla.geo.Angles
import com.emrekekil.qibla.geo.Solar
import com.emrekekil.qibla.geo.SunPosition
import com.emrekekil.qibla.sensor.HeadingSource
import com.emrekekil.qibla.sensor.MagQuality
import com.emrekekil.qibla.ui.Labels
import com.emrekekil.qibla.ui.compass.CompassState
import com.emrekekil.qibla.ui.components.ListPage
import com.emrekekil.qibla.ui.components.infoItem
import com.emrekekil.qibla.ui.components.note
import com.emrekekil.qibla.ui.components.subHeader
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Dakikada bir yeniden hesaplanan güneş bilgileri. */
private data class SunNow(val position: SunPosition, val qiblaTimeMillis: Long?)

@Composable
fun DetailsPage(
    info: QiblaInfo?,
    compass: CompassState,
    flipOverride: Boolean?,
    onFlipOverride: (Boolean?) -> Unit,
    onCalibrate: () -> Unit,
) {
    val res = LocalResources.current
    val sun by produceState<SunNow?>(null, info?.position) {
        val p = info?.position ?: return@produceState
        while (true) {
            val now = System.currentTimeMillis()
            value = SunNow(
                position = Solar.position(now, p.latitude, p.longitude),
                qiblaTimeMillis = Solar.timeOfAzimuth(LocalDate.now(), p.latitude, p.longitude, info.bearing),
            )
            delay(60_000)
        }
    }
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)

    ListPage(stringResource(R.string.page_details)) { spec ->
        if (info == null) {
            note("none", res.getString(R.string.no_location_body))
            return@ListPage
        }

        subHeader("h_qibla", res.getString(R.string.group_qibla), spec)
        infoItem(
            "bearing", res.getString(R.string.label_true_bearing), Labels.degrees(info.bearing, 1), spec,
            note = res.getString(R.string.note_true_bearing),
        )
        infoItem(
            "magnetic", res.getString(R.string.label_magnetic_bearing), Labels.degrees(info.magneticBearing, 1), spec,
            note = res.getString(R.string.note_magnetic_bearing),
        )
        infoItem("distance", res.getString(R.string.label_distance), Labels.distanceKm(info.distanceKm), spec)

        subHeader("h_location", res.getString(R.string.group_location), spec)
        infoItem("source", res.getString(R.string.label_source), sourceText(res, info), spec)
        infoItem(
            "coords", res.getString(R.string.label_coordinates),
            "%.4f, %.4f".format(info.position.latitude, info.position.longitude), spec,
        )
        val nearest = info.nearest
        if (nearest != null && info.source !is PositionSource.Table) {
            val difference = Angles.signedDelta(nearest.qiblaBearing, info.bearing)
            infoItem(
                "nearest", res.getString(R.string.label_nearest_district),
                "${nearest.displayName} · ${Labels.distanceKm(info.nearestKm ?: 0.0)}", spec,
                note = res.getString(
                    R.string.note_table_difference,
                    Labels.degrees(nearest.qiblaBearing, 1),
                    Labels.degrees(abs(difference), 1),
                ),
            )
        }

        subHeader("h_sun", res.getString(R.string.group_sun), spec)
        sun?.let { s ->
            infoItem(
                "qibla_time", res.getString(R.string.label_qibla_time),
                s.qiblaTimeMillis?.let { timeFormat.format(Date(it)) } ?: res.getString(R.string.value_none_today), spec,
                note = res.getString(R.string.note_qibla_time),
            )
            if (s.position.altitude > 0) {
                val offset = Angles.signedDelta(s.position.azimuth, info.bearing)
                infoItem(
                    "sun_now", res.getString(R.string.label_sun_now),
                    res.getString(
                        if (offset >= 0) R.string.value_qibla_right_of_sun else R.string.value_qibla_left_of_sun,
                        abs(offset).roundToInt(),
                    ),
                    spec,
                    note = res.getString(R.string.note_sun_now, Labels.degrees(s.position.azimuth)),
                )
            } else {
                infoItem("sun_now", res.getString(R.string.label_sun_now), res.getString(R.string.value_sun_below), spec)
            }
        }

        subHeader("h_compass", res.getString(R.string.group_compass), spec)
        infoItem(
            "declination", res.getString(R.string.label_declination),
            Labels.declination(res, info.declination), spec,
            note = res.getString(
                if (info.modelExpired) R.string.note_model_expired else R.string.note_model,
                info.modelName,
            ),
        )
        infoItem(
            "calibration", res.getString(R.string.label_calibration),
            res.getString(accuracyName(compass.magAccuracy)), spec,
        )
        infoItem(
            "environment", res.getString(R.string.label_environment),
            res.getString(qualityName(compass.quality)), spec,
            note = if (!compass.measuredField.isNaN()) {
                res.getString(
                    R.string.note_field,
                    "%.1f".format(compass.measuredField),
                    "%.1f".format(info.magneticField.total / 1000),
                )
            } else {
                null
            },
        )
        compass.headingAccuracy?.let {
            infoItem("heading_acc", res.getString(R.string.label_heading_accuracy), "±${it.roundToInt()}°", spec)
        }
        infoItem("sensor", res.getString(R.string.label_sensor), compass.source?.let { res.getString(sourceName(it)) }, spec)
        item(key = "calibrate") {
            FilledTonalButton(
                onClick = onCalibrate,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                label = { Text(stringResource(R.string.calibrate)) },
            )
        }

        subHeader("h_screen", res.getString(R.string.group_screen), spec)
        val flipped = compass.screenRotation == Surface.ROTATION_180
        item(key = "flip") {
            // Otomatik -> 180° çevrik -> normal -> otomatik.
            FilledTonalButton(
                onClick = {
                    onFlipOverride(
                        when (flipOverride) {
                            null -> true
                            true -> false
                            false -> null
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.filledTonalButtonColors(),
                label = { Text(stringResource(R.string.label_screen_orientation)) },
                secondaryLabel = {
                    Text(
                        stringResource(
                            when (flipOverride) {
                                null -> if (flipped) R.string.value_flip_auto_flipped else R.string.value_flip_auto_normal
                                true -> R.string.value_flip_forced
                                false -> R.string.value_flip_normal
                            },
                        ),
                    )
                },
            )
        }
        note("flip_note", res.getString(R.string.note_flip))

        subHeader("h_about", res.getString(R.string.group_about), spec)
        note("attribution", res.getString(R.string.attribution))
    }
}

private fun sourceText(res: Resources, info: QiblaInfo): String = when (val s = info.source) {
    is PositionSource.Table -> res.getString(R.string.source_table, s.district.displayName)
    is PositionSource.Gps -> Labels.gps(res, s.fix)
    is PositionSource.LastKnown -> res.getString(
        R.string.source_last_known_age,
        Labels.relativeTime(s.fix.utcMillis),
        if (s.fix.isWatchGps) "GPS" else s.fix.provider,
    )
}

private fun accuracyName(accuracy: Int): Int = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.value_accuracy_high
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.value_accuracy_medium
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.value_accuracy_low
    SensorManager.SENSOR_STATUS_UNRELIABLE -> R.string.value_accuracy_unreliable
    else -> R.string.value_unknown
}

private fun qualityName(quality: MagQuality): Int = when (quality) {
    MagQuality.GOOD -> R.string.value_quality_good
    MagQuality.SUSPECT -> R.string.value_quality_suspect
    MagQuality.BAD -> R.string.value_quality_bad
    MagQuality.NEEDS_CALIBRATION -> R.string.value_quality_calibrate
    MagQuality.UNKNOWN -> R.string.value_unknown
}

private fun sourceName(source: HeadingSource): Int = when (source) {
    HeadingSource.ROTATION_VECTOR -> R.string.value_sensor_rotation_vector
    HeadingSource.GEOMAGNETIC_ROTATION_VECTOR -> R.string.value_sensor_geomagnetic
    HeadingSource.GRAVITY_MAG, HeadingSource.ACCEL_MAG -> R.string.value_sensor_accel_mag
}
