package com.emrekekil.watchinfo.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups

object BatteryCollector {

    data class State(val fraction: Float, val charging: Boolean, val statusText: String)

    /** Sistem, son pil durumunu yapışkan (sticky) yayın olarak tutar; alıcı kaydetmeden okunabilir. */
    private fun batteryIntent(context: Context): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun levelFraction(intent: Intent?): Float? {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level.toFloat() / scale else null
    }

    fun state(context: Context): State? {
        val intent = batteryIntent(context) ?: return null
        val fraction = levelFraction(intent) ?: return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return State(fraction, status == BatteryManager.BATTERY_STATUS_CHARGING, context.getString(statusName(status)))
    }

    fun summary(context: Context): String? =
        state(context)?.let { "${Format.percent(it.fraction.toDouble())} · ${it.statusText}" }

    fun collect(context: Context): List<InfoGroup> {
        val intent = batteryIntent(context)
        val bm = context.getSystemService(BatteryManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val fraction = levelFraction(intent)
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
        val currentMa = Format.currentToMilliAmps(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        val averageMa = Format.currentToMilliAmps(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE))
            ?.takeIf { it != 0.0 }
        val chargeUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0 && it != Int.MIN_VALUE }
        // Kalan şarj / seviye oranı, pilin bugünkü gerçek kapasitesini verir.
        val estimatedFullMah = if (chargeUah != null && fraction != null && fraction >= 0.05f) {
            chargeUah / 1000.0 / fraction
        } else {
            null
        }
        val designMah = designCapacityMah(context)
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else {
            null
        }
        val chargeTimeMs = bm.computeChargeTimeRemaining().takeIf { it > 0 }

        return infoGroups {
            group(context.getString(R.string.group_battery_status)) {
                item(
                    context.getString(R.string.label_battery_level),
                    fraction?.let { Format.percent(it.toDouble()) },
                    progress = fraction,
                    alert = fraction != null && fraction <= 0.15f,
                )
                item(context.getString(R.string.label_battery_status), context.getString(statusName(status)))
                item(context.getString(R.string.label_power_source), context.getString(pluggedName(intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)))
                item(context.getString(R.string.label_battery_health), context.getString(healthName(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1)))
                item(context.getString(R.string.label_time_to_full), chargeTimeMs?.let { context.formatDuration(it) })
                item(context.getString(R.string.label_power_save), context.onOff(pm.isPowerSaveMode))
            }
            group(context.getString(R.string.group_electrical)) {
                item(context.getString(R.string.label_voltage), voltageMv?.let { "${Format.decimal(it / 1000.0, 3)} V" })
                item(context.getString(R.string.label_current), currentMa?.let { "${Format.decimal(it, 0)} mA" })
                item(context.getString(R.string.label_current_average), averageMa?.let { "${Format.decimal(it, 0)} mA" })
                if (voltageMv != null && currentMa != null) {
                    item(context.getString(R.string.label_power), "${Format.decimal(voltageMv / 1000.0 * currentMa / 1000.0, 2)} W")
                }
            }
            group(context.getString(R.string.group_capacity)) {
                item(context.getString(R.string.label_design_capacity), designMah?.let { "${Format.decimal(it, 0)} mAh" })
                item(context.getString(R.string.label_estimated_capacity), estimatedFullMah?.let { "${Format.decimal(it, 0)} mAh" })
                if (designMah != null && estimatedFullMah != null) {
                    val wear = estimatedFullMah / designMah
                    item(context.getString(R.string.label_capacity_health), Format.percent(wear), progress = wear.toFloat())
                }
                item(context.getString(R.string.label_remaining_charge), chargeUah?.let { "${Format.decimal(it / 1000.0, 0)} mAh" })
                item(context.getString(R.string.label_cycle_count), cycles?.toString())
            }
            group(context.getString(R.string.group_other)) {
                item(context.getString(R.string.label_temperature), tempTenths?.let { "${Format.decimal(it / 10.0, 1)} °C" })
                item(context.getString(R.string.label_technology), intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY))
            }
        }
    }

    /**
     * Pilin tasarım kapasitesi yalnızca gizli PowerProfile sınıfında bulunur. Yansıma ile okunur;
     * erişim engellenirse satır gösterilmez.
     */
    @SuppressLint("PrivateApi")
    private fun designCapacityMah(context: Context): Double? =
        try {
            val profileClass = Class.forName("com.android.internal.os.PowerProfile")
            val profile = profileClass.getConstructor(Context::class.java).newInstance(context)
            (profileClass.getMethod("getBatteryCapacity").invoke(profile) as Double).takeIf { it > 0 }
        } catch (_: Throwable) {
            null
        }

    private fun statusName(status: Int): Int = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> R.string.value_charging
        BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.value_discharging
        BatteryManager.BATTERY_STATUS_FULL -> R.string.value_full
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.value_not_charging
        else -> R.string.value_unknown
    }

    private fun pluggedName(plugged: Int): Int = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> R.string.value_plugged_ac
        BatteryManager.BATTERY_PLUGGED_USB -> R.string.value_plugged_usb
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.value_plugged_wireless
        BatteryManager.BATTERY_PLUGGED_DOCK -> R.string.value_plugged_dock
        else -> R.string.value_plugged_battery
    }

    private fun healthName(health: Int): Int = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> R.string.value_health_good
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.value_health_overheat
        BatteryManager.BATTERY_HEALTH_DEAD -> R.string.value_health_dead
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.value_health_over_voltage
        BatteryManager.BATTERY_HEALTH_COLD -> R.string.value_health_cold
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.value_health_failure
        else -> R.string.value_unknown
    }
}
