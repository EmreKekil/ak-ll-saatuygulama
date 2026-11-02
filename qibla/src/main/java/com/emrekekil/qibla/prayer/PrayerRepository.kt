package com.emrekekil.qibla.prayer

import android.content.Context
import com.emrekekil.qibla.data.District
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Diyanet vakitlerinin saatte saklanması ve indirilmesi.
 *
 * Veri Diyanet yer kimliği başına bir dosyada tutulur. Kayıtlı veri olmayan günler için vakitler
 * cihazda hesaplanır, bu yüzden internet hiç olmasa da uygulama çalışır.
 */
class PrayerRepository(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "prayer").apply { mkdirs() }

    private fun file(diyanetId: Int) = File(dir, "$diyanetId.csv")

    fun stored(diyanetId: Int): List<DayTimes> =
        file(diyanetId).takeIf { it.exists() }?.let { DiyanetFormat.fromCsv(it.readText()) }.orEmpty()

    /** Kayıtlı verinin bugünden sonra kaç gün daha yettiği. */
    fun daysAhead(diyanetId: Int, today: LocalDate): Long {
        val last = stored(diyanetId).maxOfOrNull { it.date } ?: return 0
        return (last.toEpochDay() - today.toEpochDay()).coerceAtLeast(0)
    }

    /** [from] gününden itibaren [count] gün; kayıtlı Diyanet verisi yoksa hesaplanmış vakitler. */
    fun days(district: District, from: LocalDate, count: Int): List<DayTimes> {
        val stored = stored(district.diyanetId).associateBy { it.date }
        return (0 until count).map { i ->
            val date = from.plusDays(i.toLong())
            stored[date] ?: PrayerCalculator.calculate(date, district.location.latitude, district.location.longitude)
        }
    }

    /**
     * Diyanet'ten 32 günlük veriyi indirir ve eskisiyle birleştirir. Ağ yoksa veya sunucu yanıt
     * vermezse false döner; çağıran taraf hesaplanmış vakitlerle devam eder. Ana iş parçacığında
     * çağrılmamalıdır.
     */
    fun download(diyanetId: Int, today: LocalDate): Boolean {
        val json = try {
            val connection = URL("$API/vakitler/$diyanetId").openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // Sunucu bazı varsayılan istemci kimliklerini reddediyor.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            return false
        }
        val fresh = DiyanetFormat.parseJson(json)
        if (fresh.isEmpty()) return false
        // Eski günler atılır; yeni veri aynı günlerin üzerine yazılır.
        val merged = (stored(diyanetId).associateBy { it.date } + fresh.associateBy { it.date })
            .values.filter { !it.date.isBefore(today.minusDays(1)) }.sortedBy { it.date }
        val target = file(diyanetId)
        val temp = File(dir, "$diyanetId.tmp")
        temp.writeText(DiyanetFormat.toCsv(merged))
        return temp.renameTo(target)
    }

    /** Artık kullanılmayan konumların dosyalarını siler. */
    fun keepOnly(diyanetIds: Set<Int>) {
        dir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension.toIntOrNull() !in diyanetIds) f.delete()
        }
    }

    companion object {
        private const val API = "https://ezanvakti.emushaf.net"
        private const val USER_AGENT = "EzanVaktiKible/1.0 (Wear OS)"
        private const val TIMEOUT_MS = 15_000

        /** Kayıtlı veri bu kadar günden az kalınca yenilenir (indirme 32 gün getirir). */
        const val REFRESH_BELOW_DAYS = 10
    }
}
