package com.emrekekil.qibla.prayer

import java.time.LocalDateTime

/** Kurulacak tek bir bildirim. */
data class PlannedAlert(
    val time: LocalDateTime,
    val prayer: Prayer,
    val kind: Kind,
    /** Uyarıda vaktin çıktığı an (bir sonraki vaktin girişi). */
    val endsAt: LocalDateTime?,
) {
    enum class Kind { ENTRY, BEFORE_END }
}

/**
 * Bir sonraki bildirimi seçer. Pil için aynı anda yalnızca tek bir alarm kurulur; alarm çalınca
 * bildirim gösterilir ve bu fonksiyon yeniden çağrılarak sıradaki kurulur.
 */
object AlarmPlanner {

    /** Vaktin çıktığı an = bir sonraki vaktin girişi (İmsak → Güneş, Yatsı → ertesi İmsak). */
    fun next(days: List<DayTimes>, alerts: Map<Prayer, AlertSetting>, now: LocalDateTime): PlannedAlert? {
        val starts = days.sortedBy { it.date }.flatMap { day -> Prayer.entries.map { it to day.at(it) } }
        val candidates = mutableListOf<PlannedAlert>()
        starts.forEachIndexed { i, (prayer, start) ->
            val setting = alerts.getValue(prayer)
            if (setting.onEntry) candidates += PlannedAlert(start, prayer, PlannedAlert.Kind.ENTRY, null)
            val end = starts.getOrNull(i + 1)?.second
            if (end != null && setting.beforeEnd && prayer in PrayerSettings.WARNABLE) {
                val warnAt = end.minusMinutes(setting.warningMinutes.toLong())
                // Uyarı vaktin girişinden önceye düşerse (çok kısa vakit) anlamsızdır.
                if (warnAt.isAfter(start)) candidates += PlannedAlert(warnAt, prayer, PlannedAlert.Kind.BEFORE_END, end)
            }
        }
        return candidates.filter { it.time.isAfter(now) }.minByOrNull { it.time }
    }
}
