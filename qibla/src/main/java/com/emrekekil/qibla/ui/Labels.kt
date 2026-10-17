package com.emrekekil.qibla.ui

import android.content.res.Resources
import android.text.format.DateUtils
import com.emrekekil.qibla.R
import com.emrekekil.qibla.data.Fix
import com.emrekekil.qibla.data.PositionSource
import com.emrekekil.qibla.data.QiblaInfo
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ekranlarda ortak kullanılan biçimlendirme yardımcıları. */
object Labels {

    fun degrees(value: Double, digits: Int = 0): String =
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value) + "°"

    /** "+6,1° D" / "-2,0° B": doğu pozitif sapma. */
    fun declination(res: Resources, value: Double): String {
        val side = res.getString(if (value >= 0) R.string.east_short else R.string.west_short)
        return "${degrees(abs(value), 1)} $side"
    }

    fun distanceKm(value: Double): String =
        NumberFormat.getIntegerInstance().format(value.roundToInt()) + " km"

    fun relativeTime(utcMillis: Long): String =
        DateUtils.getRelativeTimeSpanString(utcMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    /** Konumun kısa açıklaması: "İstanbul · Fatih" veya "GPS ±8 m". */
    fun source(res: Resources, info: QiblaInfo): String = when (val s = info.source) {
        is PositionSource.Table -> s.district.displayName
        is PositionSource.Gps -> gps(res, s.fix)
        is PositionSource.LastKnown -> info.nearest?.let { res.getString(R.string.source_near, it.displayName) }
            ?: res.getString(R.string.source_last_known)
    }

    fun gps(res: Resources, fix: Fix): String =
        fix.accuracyM?.let { res.getString(R.string.source_gps_accuracy, it.roundToInt()) }
            ?: res.getString(R.string.source_gps)
}
