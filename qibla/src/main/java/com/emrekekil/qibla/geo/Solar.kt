package com.emrekekil.qibla.geo

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Güneşin gökyüzündeki konumu (derece). Azimut kuzeyden saat yönünde. */
data class SunPosition(val azimuth: Double, val altitude: Double)

/**
 * NOAA güneş hesaplayıcısının denklemleri (Meeus tabanlı). Azimutta tipik hata 0,01°'nin
 * altındadır; kıble doğrulaması için fazlasıyla yeterlidir ve tamamen çevrimdışı çalışır.
 */
object Solar {

    /**
     * @param epochMillis UTC zaman damgası
     * @param latitude derece, kuzey pozitif
     * @param longitude derece, doğu pozitif
     */
    fun position(epochMillis: Long, latitude: Double, longitude: Double): SunPosition {
        val jd = epochMillis / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0

        val l0 = Angles.normalize(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = Math.toRadians(m)
        val center = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289
        val trueLongitude = l0 + center
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparentLongitude = Math.toRadians(trueLongitude - 0.00569 - 0.00478 * sin(omega))
        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(apparentLongitude))

        // Zaman denklemi (dakika).
        val y = tan(obliquity / 2).let { it * it }
        val l0Rad = Math.toRadians(l0)
        val equationOfTime = 4 * Math.toDegrees(
            y * sin(2 * l0Rad) - 2 * e * sin(mRad) + 4 * e * y * sin(mRad) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) - 1.25 * e * e * sin(2 * mRad),
        )

        val minutesUtc = Math.floorMod(epochMillis, 86_400_000L) / 60_000.0
        val trueSolarMinutes = minutesUtc + equationOfTime + 4 * longitude
        val hourAngle = Math.toRadians(trueSolarMinutes / 4 - 180)

        val latRad = Math.toRadians(latitude)
        val cosZenith = (sin(latRad) * sin(declination) + cos(latRad) * cos(declination) * cos(hourAngle))
            .coerceIn(-1.0, 1.0)
        val zenith = acos(cosZenith)
        val azimuth = Angles.normalize(
            Math.toDegrees(atan2(sin(hourAngle), cos(hourAngle) * sin(latRad) - tan(declination) * cos(latRad))) + 180,
        )
        return SunPosition(azimuth = azimuth, altitude = 90 - Math.toDegrees(zenith))
    }

    /**
     * Güneşin azimutunun [bearing] açısına eşit olduğu anı ("kıble saati") bulur.
     *
     * Türkiye enlemlerinde (güneşin hiçbir zaman tam tepeye çıkmadığı yerlerde) güneşin azimutu
     * gece yarısından gece yarısına 0°'den 360°'ye tekdüze artar; bu yüzden güneş öğleninin
     * ±12 saat çevresinde ikiye bölme (bisection) ile kesin çözüm bulunur.
     *
     * @param date Yerel takvim günü.
     * @return Anın UTC milisaniyesi; güneş o anda ufkun altındaysa null.
     */
    fun timeOfAzimuth(date: LocalDate, latitude: Double, longitude: Double, bearing: Double): Long? {
        // Yaklaşık güneş öğleni: o günün UTC 12:00'ı, boylamın her derecesi için 4 dk erken.
        val approxNoon = date.toEpochDay() * DAY_MS + DAY_MS / 2 - (longitude * 4 * 60_000).toLong()
        var lo = approxNoon - DAY_MS / 2
        var hi = approxNoon + DAY_MS / 2
        val target = Angles.normalize(bearing)
        repeat(40) {
            val mid = (lo + hi) / 2
            val azimuth = position(mid, latitude, longitude).azimuth
            if (unwrap(azimuth, mid, approxNoon) < target) lo = mid else hi = mid
        }
        val result = (lo + hi) / 2
        return result.takeIf { position(it, latitude, longitude).altitude > 0 }
    }

    // Öğleden önce azimut 180°'nin altında, sonra üstündedir; gece yarısı civarındaki 0/360
    // sarmalaması, anın öğlene göre konumundan düzeltilir.
    private fun unwrap(azimuth: Double, time: Long, noon: Long): Double = when {
        time < noon && azimuth > 270 -> azimuth - 360
        time > noon && azimuth < 90 -> azimuth + 360
        else -> azimuth
    }

    private const val DAY_MS = 86_400_000L
}
