package com.emrekekil.qibla.surface

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.emrekekil.qibla.data.District
import com.emrekekil.qibla.prayer.DayTimes
import com.emrekekil.qibla.prayer.PrayerEngine
import com.emrekekil.qibla.prayer.PrayerSchedule
import com.emrekekil.qibla.prayer.PrayerWindow
import java.time.LocalDateTime
import java.time.ZoneId

/** Komplikasyon ve Tile'ın ortak verisi: önümüzdeki 24 saatin vakit aralıkları. */
data class SurfaceData(
    val location: District,
    val isTemporary: Boolean,
    val windows: List<PrayerWindow>,
    val days: List<DayTimes>,
    val zone: ZoneId,
)

object PrayerSurfaces {

    /** Zaman çizelgesinin kapsadığı süre; günlük iş her gün yeniler. */
    private const val HORIZON_HOURS = 30L

    /** Dosya okur; ana iş parçacığında çağrılmamalıdır. */
    fun load(context: Context, now: LocalDateTime = LocalDateTime.now(PrayerEngine.zone())): SurfaceData {
        val engine = PrayerEngine(context)
        val home = engine.home()
        val active = engine.active()
        val days = engine.daysAround(now.toLocalDate())
        val windows = PrayerSchedule.windows(days, now, now.plusHours(HORIZON_HOURS))
        return SurfaceData(active, active != home, windows, days, PrayerEngine.zone())
    }

    /**
     * Komplikasyon ve Tile'a yeni zaman çizelgesi üretmelerini söyler. Yalnızca vakitler değişince
     * (günlük iş, indirme, konum değişimi) çağrılır; arada sistem çizelgeyi kendisi ilerletir.
     */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        ComplicationDataSourceUpdateRequester
            .create(app, ComponentName(app, PrayerComplicationService::class.java))
            .requestUpdateAll()
        TileService.getUpdater(app).requestUpdate(PrayerTileService::class.java)
    }
}
