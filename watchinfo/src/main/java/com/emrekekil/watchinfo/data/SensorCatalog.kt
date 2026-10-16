package com.emrekekil.watchinfo.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups

/** Saatteki sensörlerin listesi, Türkçe adları ve canlı değer etiketleri. */
object SensorCatalog {

    fun sensors(context: Context): List<Sensor> =
        context.getSystemService(SensorManager::class.java)
            ?.getSensorList(Sensor.TYPE_ALL)
            .orEmpty()

    fun summary(context: Context): String {
        val count = sensors(context).size
        return context.resources.getQuantityString(R.plurals.sensor_count, count, count)
    }

    /** Standart sensör türleri için Türkçe ad; üreticiye özel türlerde stringType döner. */
    fun typeName(context: Context, sensor: Sensor): String {
        val res = when (sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> R.string.sensor_accelerometer
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> R.string.sensor_accelerometer_uncal
            Sensor.TYPE_GYROSCOPE -> R.string.sensor_gyroscope
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> R.string.sensor_gyroscope_uncal
            Sensor.TYPE_MAGNETIC_FIELD -> R.string.sensor_magnetic
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> R.string.sensor_magnetic_uncal
            Sensor.TYPE_LIGHT -> R.string.sensor_light
            Sensor.TYPE_PRESSURE -> R.string.sensor_pressure
            Sensor.TYPE_PROXIMITY -> R.string.sensor_proximity
            Sensor.TYPE_GRAVITY -> R.string.sensor_gravity
            Sensor.TYPE_LINEAR_ACCELERATION -> R.string.sensor_linear_acceleration
            Sensor.TYPE_ROTATION_VECTOR -> R.string.sensor_rotation_vector
            Sensor.TYPE_GAME_ROTATION_VECTOR -> R.string.sensor_game_rotation_vector
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> R.string.sensor_geomagnetic_rotation_vector
            Sensor.TYPE_RELATIVE_HUMIDITY -> R.string.sensor_humidity
            Sensor.TYPE_AMBIENT_TEMPERATURE -> R.string.sensor_ambient_temperature
            Sensor.TYPE_HEART_RATE -> R.string.sensor_heart_rate
            Sensor.TYPE_HEART_BEAT -> R.string.sensor_heart_beat
            Sensor.TYPE_STEP_COUNTER -> R.string.sensor_step_counter
            Sensor.TYPE_STEP_DETECTOR -> R.string.sensor_step_detector
            Sensor.TYPE_SIGNIFICANT_MOTION -> R.string.sensor_significant_motion
            Sensor.TYPE_STATIONARY_DETECT -> R.string.sensor_stationary
            Sensor.TYPE_MOTION_DETECT -> R.string.sensor_motion
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> R.string.sensor_offbody
            Sensor.TYPE_POSE_6DOF -> R.string.sensor_pose
            Sensor.TYPE_HINGE_ANGLE -> R.string.sensor_hinge
            else -> null
        }
        return res?.let { context.getString(it) } ?: sensor.stringType
    }

    /** Canlı değerlerin eksen/birim etiketleri. Bilinmeyen türlerde null döner (v0, v1… kullanılır). */
    fun valueLabels(context: Context, sensor: Sensor): List<String>? = when (sensor.type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION ->
            listOf("X (m/s²)", "Y (m/s²)", "Z (m/s²)")
        Sensor.TYPE_GYROSCOPE -> listOf("X (rad/s)", "Y (rad/s)", "Z (rad/s)")
        Sensor.TYPE_MAGNETIC_FIELD -> listOf("X (µT)", "Y (µT)", "Z (µT)")
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR ->
            listOf("X", "Y", "Z", "W")
        Sensor.TYPE_LIGHT -> listOf("lx")
        Sensor.TYPE_PRESSURE -> listOf("hPa")
        Sensor.TYPE_PROXIMITY -> listOf("cm")
        Sensor.TYPE_RELATIVE_HUMIDITY -> listOf("%")
        Sensor.TYPE_AMBIENT_TEMPERATURE -> listOf("°C")
        Sensor.TYPE_HEART_RATE -> listOf("bpm")
        Sensor.TYPE_STEP_COUNTER -> listOf(context.getString(R.string.unit_steps))
        else -> null
    }

    fun details(context: Context, sensor: Sensor): List<InfoGroup> = infoGroups {
        group(context.getString(R.string.group_sensor_specs)) {
            item(context.getString(R.string.label_sensor_type), "${typeName(context, sensor)} (${sensor.type})")
            item(context.getString(R.string.label_sensor_string_type), sensor.stringType)
            item(context.getString(R.string.label_sensor_vendor), sensor.vendor)
            item(context.getString(R.string.label_sensor_version), sensor.version.toString())
            item(context.getString(R.string.label_sensor_range), Format.decimal(sensor.maximumRange.toDouble(), 2))
            item(context.getString(R.string.label_sensor_resolution), sensor.resolution.toString())
            item(context.getString(R.string.label_sensor_power), "${Format.decimal(sensor.power.toDouble(), 2)} mA")
            if (sensor.minDelay > 0) {
                item(
                    context.getString(R.string.label_sensor_max_rate),
                    "${Format.decimal(1_000_000.0 / sensor.minDelay, 0)} Hz",
                )
            }
            item(context.getString(R.string.label_sensor_reporting), context.getString(reportingName(sensor.reportingMode)))
            item(context.getString(R.string.label_sensor_wakeup), context.yesNo(sensor.isWakeUpSensor))
            item(context.getString(R.string.label_sensor_fifo), sensor.fifoMaxEventCount.takeIf { it > 0 }?.toString())
        }
    }

    private fun reportingName(mode: Int): Int = when (mode) {
        Sensor.REPORTING_MODE_CONTINUOUS -> R.string.value_reporting_continuous
        Sensor.REPORTING_MODE_ON_CHANGE -> R.string.value_reporting_on_change
        Sensor.REPORTING_MODE_ONE_SHOT -> R.string.value_reporting_one_shot
        Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> R.string.value_reporting_special
        else -> R.string.value_unknown
    }
}
