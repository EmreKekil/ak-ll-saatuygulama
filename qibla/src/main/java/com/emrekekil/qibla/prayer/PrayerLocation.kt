package com.emrekekil.qibla.prayer

import com.emrekekil.qibla.data.District
import com.emrekekil.qibla.data.PlaceTable
import com.emrekekil.qibla.geo.LatLon
import com.emrekekil.qibla.geo.Qibla

object PrayerLocation {

    /** Ana konumdan bu kadar uzakta değilseniz ana konumun vakitleri kullanılır. */
    const val AWAY_KM = 20.0

    /**
     * Bulunulan konuma göre geçici ilçeyi seçer: ana konumla aynı Diyanet bölgesindeyseniz veya
     * ana konuma [AWAY_KM]'den yakınsanız null (ana konum geçerli).
     */
    fun temporaryFor(home: District, position: LatLon, places: PlaceTable): District? {
        val (nearest, _) = places.nearest(position) ?: return null
        if (nearest.diyanetId == home.diyanetId) return null
        if (Qibla.distanceKm(position, home.location) < AWAY_KM) return null
        return nearest
    }
}
