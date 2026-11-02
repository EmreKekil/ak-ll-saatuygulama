package com.emrekekil.qibla.prayer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Diyanet'in günlük altı vakti. */
enum class Prayer { IMSAK, GUNES, OGLE, IKINDI, AKSAM, YATSI }

/** Vaktin nereden geldiği. */
enum class TimesSource { DIYANET, CALCULATED }

/** Bir günün vakitleri. */
data class DayTimes(
    val date: LocalDate,
    val times: Map<Prayer, LocalTime>,
    val qiblaTime: LocalTime?,
    val source: TimesSource,
) {
    fun at(prayer: Prayer): LocalDateTime = date.atTime(times.getValue(prayer))
}

/** Belirli bir andaki durum: şu anki vakit, bir sonraki vakit ve aralarındaki süre. */
data class PrayerMoment(
    val current: Prayer,
    val currentStart: LocalDateTime,
    val next: Prayer,
    val nextStart: LocalDateTime,
)

/** Bir vaktin girişinden bir sonraki vaktin girişine kadar süren aralık. */
data class PrayerWindow(
    val current: Prayer,
    val start: LocalDateTime,
    val next: Prayer,
    val end: LocalDateTime,
)

object PrayerSchedule {

    /**
     * [from]–[until] arasına değen tüm vakit aralıkları, sırayla. Komplikasyon ve Tile bunları zaman
     * çizelgesi olarak verir; sistem vakit değişince uygulamayı uyandırmadan sıradakine geçer.
     */
    fun windows(days: List<DayTimes>, from: LocalDateTime, until: LocalDateTime): List<PrayerWindow> {
        val starts = days.sortedBy { it.date }.flatMap { day -> Prayer.entries.map { it to day.at(it) } }
        return starts.zipWithNext { (current, start), (next, end) -> PrayerWindow(current, start, next, end) }
            .filter { it.end.isAfter(from) && it.start.isBefore(until) }
    }

    /**
     * [now] anında hangi vaktin içinde olunduğunu bulur. Gece yarısından İmsak'a kadar olan süre
     * önceki günün Yatsı vaktidir.
     *
     * @param days Ardışık günler; [now]'un gününü ve bir sonraki günü kapsamalı.
     */
    fun moment(days: List<DayTimes>, now: LocalDateTime): PrayerMoment? {
        val starts = days.sortedBy { it.date }.flatMap { day -> Prayer.entries.map { it to day.at(it) } }
        val index = starts.indexOfLast { !it.second.isAfter(now) }
        if (index < 0 || index + 1 >= starts.size) return null
        val (current, currentStart) = starts[index]
        val (next, nextStart) = starts[index + 1]
        return PrayerMoment(current, currentStart, next, nextStart)
    }
}
