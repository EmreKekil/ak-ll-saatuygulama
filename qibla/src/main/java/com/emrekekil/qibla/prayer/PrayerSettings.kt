package com.emrekekil.qibla.prayer

import android.content.Context
import androidx.core.content.edit

/** Bir vaktin bildirim ayarları. */
data class AlertSetting(
    /** Vakit girince bildir. */
    val onEntry: Boolean,
    /** Vakit çıkmadan [warningMinutes] dakika önce bildir. */
    val beforeEnd: Boolean,
    val warningMinutes: Int,
)

/** Ezan vakti tercihleri: ana konum, geçici konum ve bildirimler. SharedPreferences'ta saklanır. */
class PrayerSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("prayer", Context.MODE_PRIVATE)

    /** Ana konum (plaka, ilçe). Varsayılan: Ankara merkez (Diyanet "ANKARA"). */
    var home: Pair<Int, String>
        get() = (prefs.getInt("home_plate", 6)) to (prefs.getString("home_name", null) ?: "Altındağ")
        set(value) = prefs.edit {
            putInt("home_plate", value.first)
            putString("home_name", value.second)
        }

    /** Ana konumdan uzaktayken geçici olarak kullanılan ilçe; null ise ana konumdasınız. */
    var temporary: Pair<Int, String>?
        get() {
            val plate = prefs.getInt("temp_plate", 0)
            val name = prefs.getString("temp_name", null)
            return if (plate > 0 && name != null) plate to name else null
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove("temp_plate")
                remove("temp_name")
            } else {
                putInt("temp_plate", value.first)
                putString("temp_name", value.second)
            }
        }

    fun alert(prayer: Prayer): AlertSetting {
        val default = DEFAULTS.getValue(prayer)
        return AlertSetting(
            onEntry = prefs.getBoolean("entry_${prayer.name}", default.onEntry),
            beforeEnd = prefs.getBoolean("warn_${prayer.name}", default.beforeEnd),
            warningMinutes = prefs.getInt("warn_min_${prayer.name}", default.warningMinutes),
        )
    }

    fun setAlert(prayer: Prayer, setting: AlertSetting) = prefs.edit {
        putBoolean("entry_${prayer.name}", setting.onEntry)
        putBoolean("warn_${prayer.name}", setting.beforeEnd)
        putInt("warn_min_${prayer.name}", setting.warningMinutes)
    }

    fun alerts(): Map<Prayer, AlertSetting> = Prayer.entries.associateWith { alert(it) }

    /** Bildirim izni bir kez istendi mi (tekrar tekrar sorulmasın). */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean("notif_asked", false)
        set(value) = prefs.edit { putBoolean("notif_asked", value) }

    companion object {
        /** Güneş bir namaz vakti değildir: girişi varsayılan kapalı, "çıkış" uyarısı yoktur. */
        val DEFAULTS: Map<Prayer, AlertSetting> = mapOf(
            Prayer.IMSAK to AlertSetting(onEntry = true, beforeEnd = true, warningMinutes = 60),
            Prayer.GUNES to AlertSetting(onEntry = false, beforeEnd = false, warningMinutes = 60),
            Prayer.OGLE to AlertSetting(onEntry = true, beforeEnd = true, warningMinutes = 60),
            Prayer.IKINDI to AlertSetting(onEntry = true, beforeEnd = true, warningMinutes = 60),
            Prayer.AKSAM to AlertSetting(onEntry = true, beforeEnd = true, warningMinutes = 60),
            Prayer.YATSI to AlertSetting(onEntry = true, beforeEnd = false, warningMinutes = 60),
        )

        /** "Çıkmadan önce" uyarısı olan vakitler (Güneş namaz vakti değildir). */
        val WARNABLE = Prayer.entries - Prayer.GUNES
    }
}
