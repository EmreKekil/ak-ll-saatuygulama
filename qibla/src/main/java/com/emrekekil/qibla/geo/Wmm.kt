package com.emrekekil.qibla.geo

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Bir noktadaki manyetik alan bileşenleri (nT) ve açıları (derece). */
data class MagneticField(
    /** Kuzey bileşeni. */
    val x: Double,
    /** Doğu bileşeni. */
    val y: Double,
    /** Aşağı bileşeni. */
    val z: Double,
) {
    val horizontal: Double get() = sqrt(x * x + y * y)
    val total: Double get() = sqrt(x * x + y * y + z * z)

    /** Manyetik sapma (deklinasyon): gerçek kuzeyden manyetik kuzeye açı, doğu pozitif. */
    val declination: Double get() = Math.toDegrees(atan2(y, x))

    /** Eğim (inklinasyon): alanın yatayla yaptığı açı, aşağı pozitif. */
    val inclination: Double get() = Math.toDegrees(atan2(z, horizontal))
}

/**
 * Dünya Manyetik Modeli (WMM) küresel harmonik sentezi.
 *
 * Android'in GeomagneticField sınıfı Android 16'da bile WMM2020 katsayılarını kullanıyor; bu yüzden
 * NOAA'nın yayımladığı WMM.COF dosyası uygulamaya gömülür ve alan burada hesaplanır. Algoritma
 * NOAA/NCEI WMM teknik raporundaki denklemleri izler.
 */
class WorldMagneticModel private constructor(
    val epoch: Double,
    val modelName: String,
    private val maxDegree: Int,
    // Schmidt yarı-normalleştirme çarpanları önceden uygulanmış katsayılar: [n][m].
    private val g: Array<DoubleArray>,
    private val h: Array<DoubleArray>,
    private val gDot: Array<DoubleArray>,
    private val hDot: Array<DoubleArray>,
) {

    /** Modelin güvenilir olduğu son yıl (WMM her 5 yılda yenilenir). */
    val validUntil: Double get() = epoch + 5.0

    /**
     * @param latitude jeodezik enlem (derece, WGS84)
     * @param longitude boylam (derece, doğu pozitif)
     * @param altitudeMeters elipsoit üzerindeki yükseklik (m)
     * @param decimalYear ondalık yıl, ör. 2026.79
     */
    fun calculate(latitude: Double, longitude: Double, altitudeMeters: Double, decimalYear: Double): MagneticField {
        // Kutuplarda 1/cos(enlem) tanımsız olduğu için enlem çok az içeride tutulur.
        val lat = latitude.coerceIn(-89.99999, 89.99999)
        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(longitude)
        val hKm = altitudeMeters / 1000.0

        // Jeodezik (WGS84) -> küresel jeosentrik koordinatlar.
        val sinPhi = sin(phi)
        val rc = WGS84_A / sqrt(1 - WGS84_E2 * sinPhi * sinPhi)
        val p = (rc + hKm) * cos(phi)
        val z = (rc * (1 - WGS84_E2) + hKm) * sinPhi
        val r = sqrt(p * p + z * z)
        val phiPrime = atan(z / p)

        val dt = decimalYear - epoch
        val legendre = Legendre(maxDegree, Math.PI / 2 - phiPrime)
        val ratio = REFERENCE_RADIUS_KM / r
        val cosPhiPrime = cos(phiPrime)

        var xPrime = 0.0
        var yPrime = 0.0
        var zPrime = 0.0
        var radiusPower = ratio * ratio // (a/r)^(n+2), n=0 için başlangıç
        for (n in 1..maxDegree) {
            radiusPower *= ratio
            for (m in 0..n) {
                val gnm = g[n][m] + dt * gDot[n][m]
                val hnm = h[n][m] + dt * hDot[n][m]
                val cosM = cos(m * lambda)
                val sinM = sin(m * lambda)
                val cosTerm = gnm * cosM + hnm * sinM
                // Legendre tablosu eş-enlem (theta) cinsinden: d/dφ' = -d/dθ olduğundan
                // raporda X için görülen eksi işaret burada ortadan kalkar.
                xPrime += radiusPower * cosTerm * legendre.dp[n][m]
                yPrime += radiusPower * m * (gnm * sinM - hnm * cosM) * legendre.p[n][m] / cosPhiPrime
                zPrime -= (n + 1) * radiusPower * cosTerm * legendre.p[n][m]
            }
        }

        // Jeosentrik -> jeodezik çerçeveye döndürme.
        val delta = phiPrime - phi
        return MagneticField(
            x = xPrime * cos(delta) - zPrime * sin(delta),
            y = yPrime,
            z = xPrime * sin(delta) + zPrime * cos(delta),
        )
    }

    /** Gauss normalleştirilmiş birleşik Legendre fonksiyonları ve theta'ya göre türevleri. */
    private class Legendre(maxDegree: Int, theta: Double) {
        val p = Array(maxDegree + 1) { DoubleArray(it + 1) }
        val dp = Array(maxDegree + 1) { DoubleArray(it + 1) }

        init {
            val c = cos(theta)
            val s = sin(theta)
            p[0][0] = 1.0
            for (n in 1..maxDegree) {
                for (m in 0..n) {
                    when {
                        n == m -> {
                            p[n][m] = s * p[n - 1][m - 1]
                            dp[n][m] = c * p[n - 1][m - 1] + s * dp[n - 1][m - 1]
                        }
                        n == 1 || m == n - 1 -> {
                            p[n][m] = c * p[n - 1][m]
                            dp[n][m] = -s * p[n - 1][m] + c * dp[n - 1][m]
                        }
                        else -> {
                            val k = ((n - 1.0) * (n - 1) - m * m) / ((2.0 * n - 1) * (2 * n - 3))
                            p[n][m] = c * p[n - 1][m] - k * p[n - 2][m]
                            dp[n][m] = -s * p[n - 1][m] + c * dp[n - 1][m] - k * dp[n - 2][m]
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val WGS84_A = 6378.137
        private const val WGS84_F = 1 / 298.257223563
        private const val WGS84_E2 = WGS84_F * (2 - WGS84_F)
        private const val REFERENCE_RADIUS_KM = 6371.2

        /**
         * NOAA'nın WMM.COF biçimini okur. İlk satır "epoch model tarih", ardından her satırda
         * "n m g h gDot hDot" gelir; dosya 9'larla dolu bir satırla biter.
         */
        fun parse(cof: String): WorldMagneticModel {
            val lines = cof.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val header = lines.first().split(Regex("\\s+"))
            val epoch = header[0].toDouble()
            val name = header.getOrElse(1) { "WMM" }
            val rows = lines.drop(1)
                .takeWhile { !it.startsWith("9999") }
                .map { line -> line.split(Regex("\\s+")) }
            val maxDegree = rows.maxOf { it[0].toInt() }

            val g = Array(maxDegree + 1) { DoubleArray(it + 1) }
            val h = Array(maxDegree + 1) { DoubleArray(it + 1) }
            val gDot = Array(maxDegree + 1) { DoubleArray(it + 1) }
            val hDot = Array(maxDegree + 1) { DoubleArray(it + 1) }
            for (row in rows) {
                val n = row[0].toInt()
                val m = row[1].toInt()
                g[n][m] = row[2].toDouble()
                h[n][m] = row[3].toDouble()
                gDot[n][m] = row[4].toDouble()
                hDot[n][m] = row[5].toDouble()
            }

            // Schmidt yarı-normalleştirmesi (Gauss normalleştirilmiş Legendre tablosuyla birlikte).
            val schmidt = Array(maxDegree + 1) { DoubleArray(it + 1) }
            schmidt[0][0] = 1.0
            for (n in 1..maxDegree) {
                schmidt[n][0] = schmidt[n - 1][0] * (2 * n - 1) / n
                for (m in 1..n) {
                    schmidt[n][m] = schmidt[n][m - 1] * sqrt((n - m + 1.0) * (if (m == 1) 2 else 1) / (n + m))
                }
            }
            for (n in 0..maxDegree) {
                for (m in 0..n) {
                    g[n][m] *= schmidt[n][m]
                    h[n][m] *= schmidt[n][m]
                    gDot[n][m] *= schmidt[n][m]
                    hDot[n][m] *= schmidt[n][m]
                }
            }
            return WorldMagneticModel(epoch, name, maxDegree, g, h, gDot, hDot)
        }
    }
}

/** UTC zaman damgasını WMM'nin beklediği ondalık yıla çevirir (ör. 2026-07-02 ≈ 2026.5). */
fun decimalYear(epochMillis: Long): Double {
    val year = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).year
    val start = OffsetDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    val end = OffsetDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    return year + (epochMillis - start).toDouble() / (end - start)
}
