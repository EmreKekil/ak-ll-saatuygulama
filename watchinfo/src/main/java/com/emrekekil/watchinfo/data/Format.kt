package com.emrekekil.watchinfo.data

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** Saf (Android'e bağımlı olmayan) biçimlendirme yardımcıları; birim testleri bunları doğrular. */
object Format {

    /**
     * Bayt değerini okunur hale getirir.
     *
     * @param si true ise 1000 tabanlı (Android'in depolama ekranındaki gibi), değilse 1024 tabanlı.
     */
    fun bytes(bytes: Long, si: Boolean = false, locale: Locale = Locale.getDefault()): String {
        val unit = if (si) 1000.0 else 1024.0
        if (abs(bytes) < unit) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / unit
        var index = 0
        while (abs(value) >= unit && index < units.lastIndex) {
            value /= unit
            index++
        }
        val digits = if (abs(value) >= 100) 0 else 1
        return "${decimal(value, digits, locale)} ${units[index]}"
    }

    /** kHz cinsinden frekansı MHz veya GHz olarak yazar. */
    fun frequencyKhz(khz: Long, locale: Locale = Locale.getDefault()): String =
        if (khz >= 1_000_000) {
            "${decimal(khz / 1_000_000.0, 2, locale)} GHz"
        } else {
            "${khz / 1000} MHz"
        }

    /** 0..1 arası oranı yerel yüzde biçiminde yazar (Türkçede "%85"). */
    fun percent(fraction: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(fraction)

    fun decimal(value: Double, digits: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)

    /**
     * BatteryManager.BATTERY_PROPERTY_CURRENT_* değerlerini mA'ya çevirir.
     *
     * Standart birim µA'dır, ancak bazı Samsung cihazları mA döndürür. Ekran açıkken bir saatin
     * çektiği akım 1 mA'nın (1000 µA) altına inmediğinden, 1000'in altındaki değerler mA kabul edilir.
     * Desteklenmeyen özellikler Long.MIN_VALUE veya Int.MIN_VALUE döner; bunlar için null verilir.
     */
    fun currentToMilliAmps(raw: Long): Double? = when {
        raw == Long.MIN_VALUE || raw == Int.MIN_VALUE.toLong() -> null
        abs(raw) >= 1000 -> raw / 1000.0
        else -> raw.toDouble()
    }

    /** API düzeyinden Wear OS sürümünü çıkarır; bilinmiyorsa null. */
    fun wearOsVersion(sdkInt: Int): String? = when (sdkInt) {
        30 -> "3"
        33 -> "4"
        34 -> "5"
        35 -> "5.1"
        36 -> "6"
        else -> null
    }

    /**
     * Samsung'un "ro.build.version.oneui" özelliğini (ör. 80000, 60101) sürüm dizesine çevirir.
     * Biçim: ana * 10000 + alt * 100 + yama.
     */
    fun oneUiVersion(raw: String?): String? {
        val code = raw?.trim()?.toIntOrNull()?.takeIf { it >= 10000 } ?: return null
        val major = code / 10000
        val minor = (code / 100) % 100
        val patch = code % 100
        return if (patch > 0) "$major.$minor.$patch" else "$major.$minor"
    }

    /**
     * Vulkan sürüm kodunu (VK_MAKE_API_VERSION) "1.3.0" biçimine çevirir.
     * Kodlama: ana << 22 | alt << 12 | yama.
     */
    fun vulkanVersion(code: Int): String =
        "${(code ushr 22) and 0x7F}.${(code ushr 12) and 0x3FF}.${code and 0xFFF}"

    /** OpenGL ES sürüm kodunu (ör. 0x00030002) "3.2" biçimine çevirir. */
    fun glEsVersion(code: Int): String = "${code ushr 16}.${code and 0xFFFF}"

    /**
     * Çekirdeğin bildirdiği toplam RAM'den, kutuda yazan RAM miktarını tahmin eder.
     * Çekirdek ve donanım ayrılmış alanlar nedeniyle bildirilen toplam her zaman biraz daha azdır.
     */
    fun estimateInstalledRamBytes(totalBytes: Long): Long {
        val gib = 1024L * 1024 * 1024
        val sizes = listOf(512L * 1024 * 1024, 1 * gib, 3 * gib / 2, 2 * gib, 3 * gib, 4 * gib, 6 * gib, 8 * gib, 12 * gib, 16 * gib)
        return sizes.firstOrNull { it >= totalBytes } ?: totalBytes
    }
}
