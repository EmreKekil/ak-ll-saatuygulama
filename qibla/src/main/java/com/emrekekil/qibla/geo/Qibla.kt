package com.emrekekil.qibla.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Enlem/boylam çifti (derece, WGS84). */
data class LatLon(val latitude: Double, val longitude: Double)

object Qibla {

    /** Kâbe'nin koordinatları (WGS84). */
    val KAABA = LatLon(21.4225, 39.8262)

    private const val EARTH_RADIUS_KM = 6371.0088

    /**
     * Kıble açısı: bulunulan noktadan Kâbe'ye giden büyük daire (en kısa yol) yayının başlangıç
     * yönü, gerçek kuzeyden saat yönünde derece.
     */
    fun bearing(from: LatLon): Double = initialBearing(from, KAABA)

    fun initialBearing(from: LatLon, to: LatLon): Double {
        val phi1 = Math.toRadians(from.latitude)
        val phi2 = Math.toRadians(to.latitude)
        val dLambda = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return Angles.normalize(Math.toDegrees(atan2(y, x)))
    }

    /** Büyük daire uzaklığı (haversine), km. */
    fun distanceKm(from: LatLon, to: LatLon = KAABA): Double {
        val phi1 = Math.toRadians(from.latitude)
        val phi2 = Math.toRadians(to.latitude)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
