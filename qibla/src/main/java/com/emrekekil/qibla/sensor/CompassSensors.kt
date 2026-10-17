package com.emrekekil.qibla.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.emrekekil.qibla.geo.Angles
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/** Yön bilgisinin hangi sensör(ler)den hesaplandığı. */
enum class HeadingSource { ROTATION_VECTOR, GEOMAGNETIC_ROTATION_VECTOR, GRAVITY_MAG, ACCEL_MAG }

data class HeadingSample(
    val timestampNanos: Long,
    /** Ekranın saat 12 yönünün MANYETİK kuzeye göre açısı, [0, 360). */
    val magneticAzimuth: Double,
    /** Ekran normali ile "yukarı" arasındaki açı: 0 = yüzü yukarı ve düz, 90 = dik. */
    val screenTilt: Double,
    /** Saat 12 ekseninin ufkun üstündeki açısı; ±90'a yaklaştıkça azimut tanımsızlaşır. */
    val topElevation: Double,
    /** Dönme vektörünün bildirdiği yön doğruluğu (derece, %95 güven); bildirilmiyorsa null. */
    val headingAccuracy: Double?,
)

data class MagSample(
    val timestampNanos: Long,
    /** Kalibre edilmiş manyetik alanın büyüklüğü, µT. */
    val fieldMicroTesla: Double,
    /** Eğim açısı, aşağı pozitif (WMM ile aynı işaret). */
    val dip: Double?,
    /** Manyetometrenin SENSOR_STATUS_* değeri: tek anlamlı kalibrasyon göstergesi. */
    val accuracy: Int,
)

/**
 * Saat pusulasının sensör hattı.
 *
 * Ana kaynak TYPE_ROTATION_VECTOR'dır (ivmeölçer + jiroskop + manyetometre birleşimi). Yedekler:
 * jeomanyetik dönüş vektörü, ardından yerçekimi/ivmeölçer + manyetometre. Oyun dönüş vektörü
 * kuzey referansı olmadığı için tek başına kullanılmaz.
 *
 * TYPE_MAGNETIC_FIELD her zaman ayrıca dinlenir: Android dönüş vektörü olaylarının doğruluğunu
 * her zaman "yüksek" bildirir, yani gerçek kalibrasyon durumu yalnızca manyetometreden okunur;
 * alan büyüklüğü ve eğim de manyetik parazit tespitinde kullanılır.
 */
class CompassSensors(
    private val sensorManager: SensorManager,
    private val onHeading: (HeadingSample) -> Unit,
    private val onMag: (MagSample) -> Unit,
) : SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val geoRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val source: HeadingSource? = when {
        rotationVector != null -> HeadingSource.ROTATION_VECTOR
        geoRotationVector != null -> HeadingSource.GEOMAGNETIC_ROTATION_VECTOR
        gravity != null && magnetometer != null -> HeadingSource.GRAVITY_MAG
        accelerometer != null && magnetometer != null -> HeadingSource.ACCEL_MAG
        else -> null
    }

    private val usesRawMagnetometer get() = source == HeadingSource.GRAVITY_MAG || source == HeadingSource.ACCEL_MAG

    /** Arayüzün sensör çerçevesine göre dönüşü (Surface.ROTATION_*). */
    @Volatile var uiRotation: Int = Surface.ROTATION_0

    private val rotation = FloatArray(9)
    private val rotationUi = FloatArray(9)
    private var hasRotation = false
    private val gravityVec = FloatArray(3)
    private val magVec = FloatArray(3)
    private var hasGravity = false
    private var hasMag = false

    fun start(): Boolean {
        val headingSensor = when (source ?: return false) {
            HeadingSource.ROTATION_VECTOR -> rotationVector
            HeadingSource.GEOMAGNETIC_ROTATION_VECTOR -> geoRotationVector
            HeadingSource.GRAVITY_MAG -> gravity
            HeadingSource.ACCEL_MAG -> accelerometer
        }
        // 50 Hz, toplu iletim (batching) yok: arayüz canlı.
        sensorManager.registerListener(this, headingSensor, 20_000, 0, handler)
        magnetometer?.let {
            sensorManager.registerListener(this, it, if (usesRawMagnetometer) 20_000 else 66_667, 0, handler)
        }
        return true
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        hasRotation = false
        hasGravity = false
        hasMag = false
    }

    // Yalnızca değişiklikte çağrılır ve başlangıç değeri 0 (güvenilmez) olduğundan, güvenilmez
    // başlayan bir sensör hiç bildirilmez. Bu yüzden doğruluk her olaydan okunur.
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        // Android aynı SensorEvent nesnesini yeniden kullanır: değerler kopyalanır, olay saklanmaz.
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                hasRotation = true
                val accuracyRad = if (event.values.size >= 5) event.values[4] else -1f
                // -1 veya 0 "bilinmiyor" demektir.
                val accuracy = if (accuracyRad > 0f && accuracyRad < PI.toFloat()) Math.toDegrees(accuracyRad.toDouble()) else null
                emit(event.timestamp, accuracy)
            }
            Sensor.TYPE_GRAVITY -> {
                System.arraycopy(event.values, 0, gravityVec, 0, 3)
                hasGravity = true
                fuseGravityAndMag(event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(gravityVec, event.values, if (hasGravity) 0.15f else 1f)
                hasGravity = true
                fuseGravityAndMag(event.timestamp)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val field = sqrt(x * x + y * y + z * z)
                // Dünya "yukarı" yönünün cihaz çerçevesindeki karşılığı, dönüş matrisinin üçüncü satırıdır.
                val dip = when {
                    hasRotation -> dip(x, y, z, field, rotation[6], rotation[7], rotation[8])
                    hasGravity -> {
                        val n = sqrt(gravityVec[0] * gravityVec[0] + gravityVec[1] * gravityVec[1] + gravityVec[2] * gravityVec[2])
                        if (n > 0f) dip(x, y, z, field, gravityVec[0] / n, gravityVec[1] / n, gravityVec[2] / n) else null
                    }
                    else -> null
                }
                onMag(MagSample(event.timestamp, field.toDouble(), dip, event.accuracy))
                if (usesRawMagnetometer) {
                    lowPass(magVec, event.values, if (hasMag) 0.2f else 1f)
                    hasMag = true
                    fuseGravityAndMag(event.timestamp)
                }
            }
        }
    }

    private fun fuseGravityAndMag(timestamp: Long) {
        if (!(hasGravity && hasMag)) return
        // Serbest düşüşte veya iki vektör neredeyse paralelse false döner; son matris korunur.
        if (SensorManager.getRotationMatrix(rotation, null, gravityVec, magVec)) {
            hasRotation = true
            emit(timestamp, null)
        }
    }

    private fun emit(timestamp: Long, headingAccuracy: Double?) {
        val r = remapForUi(uiRotation)
        // getOrientation()[0] ile aynı: saat 12 ekseninin yatay düzleme izdüşümünün yönü;
        // bu yüzden eğime karşı kendiliğinden dengelenmiştir.
        val azimuth = Angles.normalize(Math.toDegrees(atan2(r[1], r[4]).toDouble()))
        val tilt = Math.toDegrees(acos(r[8].coerceIn(-1f, 1f)).toDouble())
        val topElevation = Math.toDegrees(asin(r[7].coerceIn(-1f, 1f)).toDouble())
        onHeading(HeadingSample(timestamp, azimuth, tilt, topElevation, headingAccuracy))
    }

    private fun remapForUi(rotation: Int): FloatArray = when (rotation) {
        Surface.ROTATION_90 -> {
            SensorManager.remapCoordinateSystem(this.rotation, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, rotationUi)
            rotationUi
        }
        Surface.ROTATION_180 -> {
            SensorManager.remapCoordinateSystem(this.rotation, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, rotationUi)
            rotationUi
        }
        Surface.ROTATION_270 -> {
            SensorManager.remapCoordinateSystem(this.rotation, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, rotationUi)
            rotationUi
        }
        else -> this.rotation
    }

    private fun lowPass(state: FloatArray, input: FloatArray, alpha: Float) {
        for (i in 0..2) state[i] += alpha * (input[i] - state[i])
    }

    /** Eğim, aşağı pozitif. Dikkat: SensorManager.getInclination() ters işaretlidir. */
    private fun dip(x: Float, y: Float, z: Float, field: Float, upX: Float, upY: Float, upZ: Float): Double? {
        if (field < 1f) return null
        val vertical = x * upX + y * upY + z * upZ
        val horizontal = sqrt((field * field - vertical * vertical).coerceAtLeast(0f))
        return Math.toDegrees(atan2(-vertical, horizontal).toDouble())
    }
}
