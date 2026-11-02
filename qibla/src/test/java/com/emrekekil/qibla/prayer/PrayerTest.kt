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

class AlarmPlannerTest {

    private val d = LocalDate.of(2026, 11, 2)
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private fun day(date: LocalDate) = DayTimes(
        date,
        mapOf(
            Prayer.IMSAK to t(5, 47), Prayer.GUNES to t(7, 11), Prayer.OGLE to t(12, 37),
            Prayer.IKINDI to t(15, 26), Prayer.AKSAM to t(17, 53), Prayer.YATSI to t(19, 13),
        ),
        null,
        TimesSource.DIYANET,
    )
    private val days = listOf(day(d.minusDays(1)), day(d), day(d.plusDays(1)))
    private val defaults = PrayerSettings.DEFAULTS

    @Test
    fun nextIsWarningBeforeIkindi() {
        val next = AlarmPlanner.next(days, defaults, LocalDateTime.of(d, t(13, 0)))!!
        // Öğle 15:26'da çıkar; 60 dk önce 14:26.
        assertEquals(LocalDateTime.of(d, t(14, 26)), next.time)
        assertEquals(Prayer.OGLE, next.prayer)
        assertEquals(PlannedAlert.Kind.BEFORE_END, next.kind)
        assertEquals(LocalDateTime.of(d, t(15, 26)), next.endsAt)
    }

    @Test
    fun sabahWarningIsBeforeSunrise() {
        val next = AlarmPlanner.next(days, defaults, LocalDateTime.of(d, t(5, 50)))!!
        assertEquals(LocalDateTime.of(d, t(6, 11)), next.time)
        assertEquals(Prayer.IMSAK, next.prayer)
    }

    @Test
    fun respectsDisabledAlertsAndCustomMinutes() {
        val alerts = defaults + (Prayer.OGLE to AlertSetting(onEntry = false, beforeEnd = true, warningMinutes = 30)) +
            (Prayer.IKINDI to AlertSetting(onEntry = false, beforeEnd = false, warningMinutes = 60))
        val next = AlarmPlanner.next(days, alerts, LocalDateTime.of(d, t(12, 0)))!!
        assertEquals(LocalDateTime.of(d, t(14, 56)), next.time)
        val after = AlarmPlanner.next(days, alerts, next.time)!!
        // İkindi'nin hem girişi hem uyarısı kapalı: sıradaki bildirim Akşam girişi.
        assertEquals(LocalDateTime.of(d, t(17, 53)), after.time)
        assertEquals(PlannedAlert.Kind.ENTRY, after.kind)
    }

    @Test
    fun yatsiWarningCrossesMidnightWhenEnabled() {
        val alerts = defaults + (Prayer.YATSI to AlertSetting(onEntry = true, beforeEnd = true, warningMinutes = 60))
        val next = AlarmPlanner.next(days, alerts, LocalDateTime.of(d, t(23, 0)))!!
        assertEquals(LocalDateTime.of(d.plusDays(1), t(4, 47)), next.time)
        assertEquals(Prayer.YATSI, next.prayer)
    }
}

class PrayerLocationTest {

    private val places = PlaceTable.parse(TestData.asset("tr_districts.csv"))
    private val ankara = places.find(6, "Altındağ")!!

    @Test
    fun staysHomeInsideAnkaraCity() {
        // Çankaya: aynı Diyanet bölgesi.
        assertEquals(null, PrayerLocation.temporaryFor(ankara, places.find(6, "Çankaya")!!.location, places))
    }

    @Test
    fun switchesToOtherCity() {
        val konya = places.find(42, "Selçuklu")!!.location
        val temp = PrayerLocation.temporaryFor(ankara, konya, places)!!
        assertEquals("Konya", temp.province)
    }
}
