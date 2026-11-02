package com.emrekekil.qibla.prayer

import android.content.Context
import com.emrekekil.qibla.data.District
import com.emrekekil.qibla.data.PlaceStore
import com.emrekekil.qibla.geo.LatLon
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ezan vakitlerinin iş mantığı: hangi konumun vakitlerinin kullanılacağı, verinin tazelenmesi ve
 * bir sonraki bildirimin kurulması. Arayüz, günlük iş ve alarm alıcıları aynı mantığı paylaşır.
 * Fonksiyonlar dosya okuduğu için ana iş parçacığında çağrılmamalıdır.
 */
class PrayerEngine(context: Context) {

    private val app = context.applicationContext
    val settings = PrayerSettings(app)
    val repository = PrayerRepository(app)
    private val places get() = PlaceStore.get(app)

    fun home(): District {
        val (plate, name) = settings.home
        return places.find(plate, name) ?: places.province(plate)?.center ?: places.province(6)!!.center
    }

    fun temporary(): District? = settings.temporary?.let { (plate, name) -> places.find(plate, name) }

    /** Vakitleri kullanılan konum: uzaktaysanız geçici konum, değilse ana konum. */
    fun active(): District = temporary() ?: home()

    /**
     * Yeni bir konum bilgisine göre geçici konumu günceller.
     * @return Etkin konum değiştiyse true.
     */
    fun onPosition(position: LatLon): Boolean {
        val before = active()
        val temp = PrayerLocation.temporaryFor(home(), position, places)
        settings.temporary = temp?.let { it.plate to it.name }
        return active() != before
    }

    fun setHome(district: District) {
        settings.home = district.plate to district.name
        settings.temporary = null
    }

    /** Dünden yarından sonraya kadar günler: anı vakit, gece yarısı geçişi ve alarm planı için. */
    fun daysAround(today: LocalDate = today()): List<DayTimes> =
        repository.days(active(), today.minusDays(1), 4)

    /**
     * Gerekiyorsa Diyanet verisini indirir (yalnızca ana ve etkin konum, veri azaldıysa).
     * @return Yeni veri indirildiyse true.
     */
    fun refresh(force: Boolean = false): Boolean {
        val today = today()
        val ids = setOf(home().diyanetId, active().diyanetId)
        var changed = false
        for (id in ids) {
            if (force || repository.daysAhead(id, today) < PrayerRepository.REFRESH_BELOW_DAYS) {
                changed = repository.download(id, today) || changed
            }
        }
        repository.keepOnly(ids)
        return changed
    }

    fun needsRefresh(): Boolean {
        val today = today()
        return setOf(home().diyanetId, active().diyanetId)
            .any { repository.daysAhead(it, today) < PrayerRepository.REFRESH_BELOW_DAYS }
    }

    fun nextAlert(now: LocalDateTime = LocalDateTime.now(zone())): PlannedAlert? =
        AlarmPlanner.next(daysAround(now.toLocalDate()), settings.alerts(), now)

    companion object {
        fun zone(): ZoneId = ZoneId.systemDefault()
        fun today(): LocalDate = LocalDate.now(zone())
    }
}
