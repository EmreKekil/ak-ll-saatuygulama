package com.emrekekil.qibla.prayer

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Diyanet vakit verisinin okunması ve saatte saklanması.
 *
 * Sunucu, her günü düz (iç içe olmayan) bir JSON nesnesi olarak döndürür; bu yüzden küçük bir
 * anahtar/değer ayrıştırıcısı yeterlidir ve ek kütüphane gerekmez. Saatte veri, satır başına bir gün
 * olan sade bir CSV olarak tutulur.
 */
object DiyanetFormat {

    private val objectPattern = Regex("\\{[^{}]*\\}")
    private val pairPattern = Regex("\"(\\w+)\"\\s*:\\s*(?:\"([^\"]*)\"|null|[-\\d.]+)")
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private val keys = mapOf(
        Prayer.IMSAK to "Imsak",
        Prayer.GUNES to "Gunes",
        Prayer.OGLE to "Ogle",
        Prayer.IKINDI to "Ikindi",
        Prayer.AKSAM to "Aksam",
        Prayer.YATSI to "Yatsi",
    )

    /** ezanvakti.emushaf.net/vakitler/{id} yanıtını okur; bozuk günleri atlar. */
    fun parseJson(json: String): List<DayTimes> =
        objectPattern.findAll(json).mapNotNull { match ->
            val fields = pairPattern.findAll(match.value).associate { it.groupValues[1] to it.groupValues[2] }
            runCatching {
                DayTimes(
                    date = LocalDate.parse(fields.getValue("MiladiTarihKisa"), dateFormat),
                    times = keys.mapValues { (_, key) -> LocalTime.parse(fields.getValue(key)) },
                    qiblaTime = fields["KibleSaati"]?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                    source = TimesSource.DIYANET,
                )
            }.getOrNull()
        }.sortedBy { it.date }.toList()

    /** Saklama biçimi: "2026-11-02,05:47,07:11,12:37,15:26,17:53,19:13,11:23" (kıble saati boş olabilir). */
    fun toCsv(days: List<DayTimes>): String = buildString {
        for (day in days) {
            append(day.date)
            Prayer.entries.forEach { append(',').append(day.times.getValue(it)) }
            append(',').append(day.qiblaTime ?: "")
            append('\n')
        }
    }

    fun fromCsv(csv: String): List<DayTimes> =
        csv.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val cells = line.split(',')
            runCatching {
                DayTimes(
                    date = LocalDate.parse(cells[0]),
                    times = Prayer.entries.withIndex().associate { (i, p) -> p to LocalTime.parse(cells[i + 1]) },
                    qiblaTime = cells.getOrNull(7)?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                    source = TimesSource.DIYANET,
                )
            }.getOrNull()
        }.toList()
}
