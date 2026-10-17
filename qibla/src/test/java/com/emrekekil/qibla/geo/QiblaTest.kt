package com.emrekekil.qibla.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.floor

/**
 * Türkiye'deki 21 noktanın kıble açıları ve kıble saatleri. Beklenen değerler araştırma
 * sırasında Diyanet'in kendi kıble sayfasındaki formülle ve astropy ile hesaplandı; Diyanet'in
 * yayımladığı coğrafi tam sayı açılar ve kıble saatleriyle karşılaştırıldı.
 */
class QiblaTest {

    private val rows = TestData.csv(TestData.resource("qibla_turkey.csv"))
    private val turkey = ZoneOffset.ofHours(3)

    private fun point(row: Map<String, String>) =
        LatLon(row.getValue("lat").toDouble(), row.getValue("lon").toDouble())

    @Test
    fun bearingMatchesDiyanetGreatCircle() {
        assertEquals(21, rows.size)
        for (row in rows) {
            val bearing = Qibla.bearing(point(row))
            val name = row.getValue("name")
            assertEquals(name, row.getValue("qibla_gc_deg").toDouble(), bearing, 0.002)
            // Diyanet'in coğrafi kıble açısı, büyük daire açısının tam sayı kısmıdır.
            assertEquals(name, row.getValue("diyanet_geographic_thirdparty").toDouble(), floor(bearing), 0.0)
        }
    }

    @Test
    fun distanceMatchesHaversine() {
        for (row in rows) {
            assertEquals(row.getValue("name"), row.getValue("dist_haversine_km").toDouble(), Qibla.distanceKm(point(row)), 0.1)
        }
    }

    @Test
    fun qiblaTimeMatchesReferenceAndDiyanet() {
        val date = LocalDate.of(2026, 10, 17)
        for (row in rows) {
            val p = point(row)
            val name = row.getValue("name")
            val millis = requireNotNull(Solar.timeOfAzimuth(date, p.latitude, p.longitude, Qibla.bearing(p))) { name }
            val local = LocalTime.ofSecondOfDay(
                Math.floorMod(millis / 1000 + turkey.totalSeconds, 86_400L),
            )
            val reference = LocalTime.parse(row.getValue("computed_kible_saati_2026_10_17"))
            val diyanet = LocalTime.parse(row.getValue("diyanet_kible_saati_2026_10_17"))
            assertEquals("$name ref", reference.toSecondOfDay().toDouble(), local.toSecondOfDay().toDouble(), 10.0)
            // Diyanet'in ilçe koordinatları bilinmediği için dakika yuvarlamasında ±1 dk fark olabilir.
            val diff = abs(local.toSecondOfDay() - diyanet.toSecondOfDay())
            assert(diff <= 90) { "$name: hesap $local, Diyanet $diyanet" }
        }
    }

    @Test
    fun sunAzimuthAtQiblaTimeEqualsBearing() {
        val p = LatLon(41.0082, 28.9784)
        val bearing = Qibla.bearing(p)
        val millis = Solar.timeOfAzimuth(LocalDate.of(2026, 6, 21), p.latitude, p.longitude, bearing)!!
        assertEquals(bearing, Solar.position(millis, p.latitude, p.longitude).azimuth, 0.01)
    }

    @Test
    fun anglesWrapCorrectly() {
        assertEquals(2.0, Angles.signedDelta(359.0, 1.0), 1e-9)
        assertEquals(-2.0, Angles.signedDelta(1.0, 359.0), 1e-9)
        assertEquals(180.0, Angles.signedDelta(0.0, 180.0), 1e-9)
        assertEquals(350.0, Angles.normalize(-10.0), 1e-9)
    }

    @Test
    fun smootherDoesNotJumpAcrossNorth() {
        val smoother = CircularSmoother(tauSeconds = 0.2)
        var t = 0L
        smoother.update(358.0, t)
        repeat(50) {
            t += 20_000_000
            val v = smoother.update(if (it % 2 == 0) 2.0 else 358.0, t)
            assert(v > 355 || v < 5) { "kaydı: $v" }
        }
    }

    private fun assert(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }
}
