package com.emrekekil.qibla.data

import android.content.Context
import androidx.core.content.edit

/** Kullanıcı tercihleri: elle seçilen ilçe ve ekran yönü zorlaması. */
class QiblaSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Elle seçilmiş ilçe (plaka, ad); null ise konum otomatik (GPS / son konum) belirlenir. */
    var manualDistrict: Pair<Int, String>?
        get() {
            val plate = prefs.getInt("district_plate", 0)
            val name = prefs.getString("district_name", null)
            return if (plate > 0 && name != null) plate to name else null
        }
        set(value) {
            prefs.edit {
                if (value == null) {
                    remove("district_plate")
                    remove("district_name")
                } else {
                    putInt("district_plate", value.first)
                    putString("district_name", value.second)
                }
            }
        }

    /** Ekranın 180° çevrik olduğunu elle bildirmek için: null = otomatik algıla. */
    var flipOverride: Boolean?
        get() = when (prefs.getInt("flip", -1)) {
            0 -> false
            1 -> true
            else -> null
        }
        set(value) {
            prefs.edit { putInt("flip", if (value == null) -1 else if (value) 1 else 0) }
        }
}
