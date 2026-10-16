package com.emrekekil.watchinfo.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * /proc, /sys ve sistem özelliklerini okuma yardımcıları.
 *
 * Android, uygulamaların bu dosyaların bir kısmına erişimini SELinux ile kısıtlar ve kısıtlama
 * cihazdan cihaza değişir. Bu yüzden her okuma başarısız olabilir ve null döner.
 */
object SysFs {

    fun read(path: String): String? =
        try {
            File(path).readText().trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }

    fun readLong(path: String): Long? = read(path)?.toLongOrNull()

    fun listDir(path: String): List<File> =
        try {
            File(path).listFiles()?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

    private val propCache = ConcurrentHashMap<String, String>()

    /**
     * Sistem özelliğini `getprop` ile okur. Yalnızca değişmeyen (ro.*) özellikler için kullanılır,
     * bu yüzden sonuç önbelleğe alınır. Ana iş parçacığında çağrılmamalıdır.
     */
    fun getprop(name: String): String? {
        propCache[name]?.let { return it.ifEmpty { null } }
        val value = try {
            val process = ProcessBuilder("/system/bin/getprop", name).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.waitFor(2, TimeUnit.SECONDS)) output else ""
        } catch (_: Exception) {
            ""
        }
        propCache[name] = value
        return value.ifEmpty { null }
    }

    /** "0-4" veya "0,2-3" gibi CPU aralık listesini çekirdek indekslerine açar. */
    fun parseCpuRange(range: String?): List<Int> =
        range.orEmpty().split(',').filter { it.isNotBlank() }.flatMap { part ->
            val bounds = part.trim().split('-').mapNotNull { it.trim().toIntOrNull() }
            when (bounds.size) {
                1 -> listOf(bounds[0])
                2 -> (bounds[0]..bounds[1]).toList()
                else -> emptyList()
            }
        }
}
