package com.emrekekil.qibla.prayer

import com.emrekekil.qibla.data.PlaceTable
import com.emrekekil.qibla.geo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class PrayerCalculatorTest {

    private val places = PlaceTable.parse(TestData.asset("tr_districts.csv"))

    /** Diyanet'ten indirilen 10 il × 32 günlük vakitler (dosya adı = Diyanet kimliği). */
    private val fixtures = File("src/test/resources/diyanet").listFiles()!!.associate { file ->
        file.nameWithoutExtension.toInt() to DiyanetFormat.parseJson(file.readText())
    }

    @Test
    fun matchesDiyanetWithinOneMinute() {
        assertEquals(10, fixtures.size)
        var exact = 0
        var total = 0
        for ((id, days) in fixtures) {
            val center = places.districts.first { it.diyanetId == id && it.isProvinceCenter }
            for (day in days) {
                val calc = PrayerCalculator.calculate(day.date, center.location.latitude, center.location.longitude)
                for (prayer in Prayer.entries) {
                    val diff = ChronoUnit.MINUTES.between(day.times.getValue(prayer), calc.times.getValue(prayer))
                    assertTrue("${center.province} ${day.date} $prayer: Diyanet ${day.times[prayer]} hesap ${calc.times[prayer]}", abs(diff) <= 1)
                    if (diff == 0L) exact++
                    total++
                }
            }
        }
        // Diyanet'in kullandığı koordinatlar bilinmediği için tam dakika uyumu %100 olamaz.
        assertTrue("tam eşleşme $exact/$total", exact >= total * 0.75)
    }
}

class DiyanetFormatTest {

    @Test
    fun parsesServerResponseWithExtraFields() {
        val json = """[{"HicriTarihKisa":"20.5.1448","HicriTarihKisaIso8601":null,"MiladiTarihKisa":"31.10.2026",
            "GreenwichOrtalamaZamani":3.0,"Aksam":"17:55","Gunes":"07:09","Ikindi":"15:28","Imsak":"05:45",
            "KibleSaati":"11:25","Ogle":"12:37","Yatsi":"19:15"},{"MiladiTarihKisa":"bozuk"}]"""
        val days = DiyanetFormat.parseJson(json)
        assertEquals(1, days.size)
        val day = days.single()
        assertEquals(LocalDate.of(2026, 10, 31), day.date)
        assertEquals(LocalTime.of(5, 45), day.times[Prayer.IMSAK])
        assertEquals(LocalTime.of(19, 15), day.times[Prayer.YATSI])
        assertEquals(LocalTime.of(11, 25), day.qiblaTime)
    }

    @Test
    fun csvRoundTrip() {
        val days = DiyanetFormat.parseJson(File("src/test/resources/diyanet/9206.json").readText())
        assertEquals(days, DiyanetFormat.fromCsv(DiyanetFormat.toCsv(days)))
    }
}

class PrayerScheduleTest {

    private fun day(date: LocalDate) = PrayerCalculator.calculate(date, 39.94583, 32.85611)

    @Test
    fun afterMidnightIsStillPreviousYatsi() {
        val d = LocalDate.of(2026, 11, 2)
        val days = listOf(day(d.minusDays(1)), day(d), day(d.plusDays(1)))
        val moment = PrayerSchedule.moment(days, LocalDateTime.of(d, LocalTime.of(2, 0)))!!
        assertEquals(Prayer.YATSI, moment.current)
        assertEquals(d.minusDays(1), moment.currentStart.toLocalDate())
        assertEquals(Prayer.IMSAK, moment.next)
        assertEquals(d, moment.nextStart.toLocalDate())
    }

    @Test
    fun afternoonIsIkindiUntilAksam() {
        val d = LocalDate.of(2026, 11, 2)
        val days = listOf(day(d), day(d.plusDays(1)))
        val moment = PrayerSchedule.moment(days, LocalDateTime.of(d, LocalTime.of(16, 0)))!!
        assertEquals(Prayer.IKINDI, moment.current)
        assertEquals(Prayer.AKSAM, moment.next)
    }
}
