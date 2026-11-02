package com.emrekekil.qibla.prayer

import com.emrekekil.qibla.geo.Solar
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Diyanet yöntemiyle çevrimdışı vakit hesabı: İmsak güneş ufkun 18°, Yatsı 17° altındayken; Güneş ve
 * Akşam üst kenarın ufukta göründüğü an (−0,833°, kırılma dahil); İkindi gölge boyu = cisim boyu +
 * öğle gölgesi. Ardından Diyanet'in temkin dakikaları eklenir.
 *
 * Temkinler ve yuvarlama yönü, Diyanet'in yayımladığı vakitlere uydurularak bulundu (10 il × 32 gün:
 * tüm vakitler en fazla 1 dk farkla, çoğunluğu aynı dakikada).
 */
object PrayerCalculator {

    private const val FAJR_ANGLE = 18.0
    private const val ISHA_ANGLE = 17.0
    private const val HORIZON = -0.833

    /** Türkiye'nin sabit saat dilimi (UTC+3, yaz saati yok). */
    val TURKEY: ZoneOffset = ZoneOffset.ofHours(3)

    fun calculate(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneOffset = TURKEY): DayTimes {
        val dayStartUtc = date.toEpochDay() * DAY_MINUTES // UTC gece yarısı, dakika
        val phi = Math.toRadians(latitude)

        fun ephemeris(minutesUtc: Double) = Solar.ephemeris(((dayStartUtc + minutesUtc) * 60_000).toLong())

        // Güneş öğleni (UTC dakika), zaman denklemiyle birkaç adımda yakınsar.
        var noon = 720 - 4 * longitude
        repeat(3) { noon = 720 - 4 * longitude - ephemeris(noon).equationOfTimeMinutes }

        /** Güneşin [altitude] yüksekliğinde olduğu an; öğleden önce veya sonra. */
        fun timeAtAltitude(altitude: (Double) -> Double, afterNoon: Boolean): Double {
            var t = noon
            repeat(4) {
                val eph = ephemeris(t)
                val localNoon = 720 - 4 * longitude - eph.equationOfTimeMinutes
                val cosH = (sin(Math.toRadians(altitude(eph.declination))) - sin(phi) * sin(eph.declination)) /
                    (cos(phi) * cos(eph.declination))
                val h = Math.toDegrees(acos(cosH.coerceIn(-1.0, 1.0))) * 4
                t = if (afterNoon) localNoon + h else localNoon - h
            }
            return t
        }

        val offset = zone.totalSeconds / 60.0
        fun local(minutesUtc: Double, temkin: Int, roundUp: Boolean): LocalTime {
            val minutes = minutesUtc + offset + temkin
            val m = if (roundUp) ceil(minutes).toInt() else minutes.roundToInt()
            return LocalTime.of(Math.floorMod(m / 60, 24), Math.floorMod(m, 60))
        }

        val times = mapOf(
            Prayer.IMSAK to local(timeAtAltitude({ -FAJR_ANGLE }, false), 0, roundUp = false),
            Prayer.GUNES to local(timeAtAltitude({ HORIZON }, false), -7, roundUp = false),
            Prayer.OGLE to local(noon, 5, roundUp = false),
            Prayer.IKINDI to local(
                timeAtAltitude({ dec -> Math.toDegrees(atan(1 / (1 + tan(abs(phi - dec))))) }, true),
                4,
                roundUp = true,
            ),
            Prayer.AKSAM to local(timeAtAltitude({ HORIZON }, true), 7, roundUp = true),
            Prayer.YATSI to local(timeAtAltitude({ -ISHA_ANGLE }, true), 0, roundUp = true),
        )
        return DayTimes(date, times, qiblaTime = null, source = TimesSource.CALCULATED)
    }

    private const val DAY_MINUTES = 1440L
}
