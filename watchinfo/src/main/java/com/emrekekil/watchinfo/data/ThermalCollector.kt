package com.emrekekil.watchinfo.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups

object ThermalCollector {

    private const val THERMAL_DIR = "/sys/class/thermal"

    // getThermalHeadroom saniyede birden sık çağrılırsa NaN döner; son geçerli değer saklanır.
    @Volatile private var lastHeadroom: Float? = null

    private fun batteryTempCelsius(context: Context): Double? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { it / 10.0 }

    fun summary(context: Context): String {
        val status = context.getString(statusName(context.getSystemService(PowerManager::class.java).currentThermalStatus))
        val temp = batteryTempCelsius(context)?.let { "${Format.decimal(it, 1)} °C" }
        return listOfNotNull(status, temp).joinToString(" · ")
    }

    /** Okunabilen termal bölgeler (ad -> °C). Çoğu cihazda SELinux bu dosyaları gizler. */
    private fun thermalZones(): List<Pair<String, Double>> =
        SysFs.listDir(THERMAL_DIR)
            .filter { it.name.startsWith("thermal_zone") }
            .sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { zone ->
                val raw = SysFs.readLong("${zone.path}/temp") ?: return@mapNotNull null
                val type = SysFs.read("${zone.path}/type") ?: zone.name
                // Değer çoğunlukla mili-°C'dir; bazı sürücüler doğrudan °C verir.
                val celsius = if (raw > 1000 || raw < -1000) raw / 1000.0 else raw.toDouble()
                if (celsius in -40.0..150.0) type to celsius else null
            }

    fun collect(context: Context): List<InfoGroup> {
        val pm = context.getSystemService(PowerManager::class.java)
        val headroom = pm.getThermalHeadroom(10).takeUnless { it.isNaN() }?.also { lastHeadroom = it }
            ?: lastHeadroom
        val zones = thermalZones()

        return infoGroups {
            group(context.getString(R.string.group_thermal_state)) {
                val status = pm.currentThermalStatus
                item(
                    context.getString(R.string.label_thermal_status),
                    context.getString(statusName(status)),
                    alert = status >= PowerManager.THERMAL_STATUS_SEVERE,
                )
                // 1,0 değeri ağır kısıtlamanın (SEVERE) başladığı noktadır.
                item(
                    context.getString(R.string.label_thermal_headroom),
                    headroom?.let { Format.percent(it.toDouble()) },
                    progress = headroom,
                    alert = headroom != null && headroom >= 0.9f,
                )
                item(
                    context.getString(R.string.label_battery_temperature),
                    batteryTempCelsius(context)?.let { "${Format.decimal(it, 1)} °C" },
                )
            }
            group(context.getString(R.string.group_thermal_zones)) {
                if (zones.isEmpty()) {
                    item(context.getString(R.string.label_thermal_zones), context.getString(R.string.value_restricted))
                } else {
                    zones.forEach { (name, celsius) -> item(name, "${Format.decimal(celsius, 1)} °C") }
                }
            }
        }
    }

    private fun statusName(status: Int): Int = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> R.string.value_thermal_none
        PowerManager.THERMAL_STATUS_LIGHT -> R.string.value_thermal_light
        PowerManager.THERMAL_STATUS_MODERATE -> R.string.value_thermal_moderate
        PowerManager.THERMAL_STATUS_SEVERE -> R.string.value_thermal_severe
        PowerManager.THERMAL_STATUS_CRITICAL -> R.string.value_thermal_critical
        PowerManager.THERMAL_STATUS_EMERGENCY -> R.string.value_thermal_emergency
        PowerManager.THERMAL_STATUS_SHUTDOWN -> R.string.value_thermal_shutdown
        else -> R.string.value_unknown
    }
}
