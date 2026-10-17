package com.emrekekil.qibla.sensor

import android.hardware.SensorManager
import kotlin.math.abs

enum class MagQuality { UNKNOWN, GOOD, SUSPECT, BAD, NEEDS_CALIBRATION }

/**
 * Ölçülen manyetik alanı, Dünya Manyetik Modeli'nin konum için beklediği değerlerle karşılaştırır.
 * Yakındaki metal, mıknatıs veya elektronik cihazlar alanın büyüklüğünü ve eğimini bozar; bu da
 * pusulanın yanlış yönü gösterdiğinin en güvenilir işaretidir.
 *
 * Eşikler başlangıç değerleridir (Türkiye'de 2026: F ≈ 46,8–49,7 µT, eğim ≈ 54–61°).
 */
class MagneticEnvironment(
    private val expectedFieldMicroTesla: Double,
    private val expectedDip: Double,
) {
    private val window = ArrayDeque<MagSample>()

    /** Son bir saniyenin ortanca değerleri (gösterim için). */
    var medianField: Double = Double.NaN
        private set
    var medianDip: Double = Double.NaN
        private set

    fun add(sample: MagSample): MagQuality {
        window.addLast(sample)
        while (window.size > 1 && sample.timestampNanos - window.first().timestampNanos > 1_000_000_000L) {
            window.removeFirst()
        }
        medianField = window.map { it.fieldMicroTesla }.sorted()[window.size / 2]
        val dips = window.mapNotNull { it.dip }.sorted()
        medianDip = if (dips.isEmpty()) Double.NaN else dips[dips.size / 2]
        if (sample.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) return MagQuality.NEEDS_CALIBRATION
        return classify(medianField, medianDip)
    }

    fun classify(field: Double, dip: Double): MagQuality {
        val fieldDeviation = abs(field - expectedFieldMicroTesla) / expectedFieldMicroTesla
        val dipDeviation = if (dip.isNaN()) 0.0 else abs(dip - expectedDip)
        return when {
            fieldDeviation > BAD_FIELD_RATIO || dipDeviation > BAD_DIP -> MagQuality.BAD
            fieldDeviation > GOOD_FIELD_RATIO || dipDeviation > GOOD_DIP -> MagQuality.SUSPECT
            else -> MagQuality.GOOD
        }
    }

    private companion object {
        const val GOOD_FIELD_RATIO = 0.15
        const val BAD_FIELD_RATIO = 0.30
        const val GOOD_DIP = 6.0
        const val BAD_DIP = 12.0
    }
}
