package com.emrekekil.watchinfo.data

import android.content.Context
import com.emrekekil.watchinfo.R

/** Süreyi "2 g 4 sa 12 dk" biçiminde yazar. */
fun Context.formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> getString(R.string.fmt_duration_dhm, days, hours, minutes)
        hours > 0 -> getString(R.string.fmt_duration_hm, hours, minutes)
        else -> getString(R.string.fmt_duration_m, minutes)
    }
}

fun Context.yesNo(value: Boolean): String = getString(if (value) R.string.value_yes else R.string.value_no)

fun Context.supported(value: Boolean): String =
    getString(if (value) R.string.value_supported else R.string.value_not_supported)

fun Context.onOff(value: Boolean): String = getString(if (value) R.string.value_on else R.string.value_off)
