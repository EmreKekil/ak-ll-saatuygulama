package com.emrekekil.watchinfo.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SystemCollector {

    /** Kullanıcının saate verdiği ad (ör. "Galaxy Watch Ultra (1A2B)"). */
    fun deviceName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.titlecase() }} ${Build.MODEL}"

    fun summary(context: Context): String =
        listOfNotNull(
            Format.wearOsVersion(Build.VERSION.SDK_INT)?.let { context.getString(R.string.fmt_wear_os, it) },
            context.getString(R.string.fmt_android, Build.VERSION.RELEASE),
        ).joinToString(" · ")

    fun collect(context: Context): List<InfoGroup> {
        val elapsed = SystemClock.elapsedRealtime()
        val awake = SystemClock.uptimeMillis()
        val deepSleep = if (elapsed > 0) 1.0 - awake.toDouble() / elapsed else null

        return infoGroups {
            group(context.getString(R.string.group_device)) {
                item(context.getString(R.string.label_device_name), deviceName(context))
                item(context.getString(R.string.label_manufacturer), Build.MANUFACTURER.replaceFirstChar { it.titlecase() })
                item(context.getString(R.string.label_brand), Build.BRAND)
                item(context.getString(R.string.label_model), Build.MODEL)
                item(context.getString(R.string.label_codename), Build.DEVICE)
                item(context.getString(R.string.label_product), Build.PRODUCT)
                item(context.getString(R.string.label_board), Build.BOARD)
                item(context.getString(R.string.label_hardware), Build.HARDWARE)
            }
            group(context.getString(R.string.group_software)) {
                item(context.getString(R.string.label_wear_os), Format.wearOsVersion(Build.VERSION.SDK_INT))
                item(context.getString(R.string.label_one_ui), Format.oneUiVersion(SysFs.getprop("ro.build.version.oneui")))
                item(context.getString(R.string.label_android), Build.VERSION.RELEASE_OR_CODENAME)
                item(context.getString(R.string.label_api_level), Build.VERSION.SDK_INT.toString())
                item(context.getString(R.string.label_security_patch), formatPatchDate(Build.VERSION.SECURITY_PATCH))
                item(context.getString(R.string.label_build_number), Build.DISPLAY)
                item(
                    context.getString(R.string.label_build_date),
                    DateFormat.getDateInstance(DateFormat.LONG).format(Date(Build.TIME)),
                )
                item(context.getString(R.string.label_build_type), "${Build.TYPE} / ${Build.TAGS}")
                item(context.getString(R.string.label_kernel), System.getProperty("os.version"))
                item(context.getString(R.string.label_runtime), System.getProperty("java.vm.version")?.let { "ART $it" })
                item(context.getString(R.string.label_bootloader), Build.BOOTLOADER.takeUnless { it == Build.UNKNOWN })
                item(context.getString(R.string.label_baseband), Build.getRadioVersion())
                item(context.getString(R.string.label_fingerprint), Build.FINGERPRINT)
            }
            group(context.getString(R.string.group_runtime)) {
                item(context.getString(R.string.label_uptime), context.formatDuration(elapsed))
                item(context.getString(R.string.label_awake_time), context.formatDuration(awake))
                item(
                    context.getString(R.string.label_deep_sleep),
                    deepSleep?.let { Format.percent(it) },
                    progress = deepSleep?.toFloat(),
                )
                item(context.getString(R.string.label_language), Locale.getDefault().displayName)
                item(context.getString(R.string.label_time_zone), TimeZone.getDefault().id)
            }
            group(context.getString(R.string.group_platform)) {
                item(context.getString(R.string.label_treble), SysFs.getprop("ro.treble.enabled")?.let { context.yesNo(it == "true") })
                item(
                    context.getString(R.string.label_feature_count),
                    context.packageManager.systemAvailableFeatures.count { it.name != null }.toString(),
                )
            }
        }
    }

    /** "2026-09-01" -> yerel uzun tarih ("1 Eylül 2026"). */
    private fun formatPatchDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw) ?: return raw
            DateFormat.getDateInstance(DateFormat.LONG).format(date)
        } catch (_: Exception) {
            raw
        }
    }
}
