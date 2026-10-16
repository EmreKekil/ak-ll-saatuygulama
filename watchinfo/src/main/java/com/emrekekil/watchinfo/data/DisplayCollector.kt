package com.emrekekil.watchinfo.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups
import kotlin.math.hypot
import kotlin.math.roundToInt

object DisplayCollector {

    private fun display(context: Context): Display? =
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    fun summary(context: Context): String? {
        val display = display(context) ?: return null
        val mode = display.mode
        return "${mode.physicalWidth}×${mode.physicalHeight} · ${mode.refreshRate.roundToInt()} Hz"
    }

    fun collect(context: Context): List<InfoGroup> {
        val display = display(context) ?: return emptyList()
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        val mode = display.mode
        val width = mode.physicalWidth
        val height = mode.physicalHeight
        // xdpi/ydpi üreticinin bildirdiği değerlerdir; fiziksel boyut bu yüzden tahminidir.
        val diagonalInches = hypot(width / metrics.xdpi, height / metrics.ydpi).toDouble()
        val refreshRates = display.supportedModes
            .map { it.refreshRate.roundToInt() }
            .distinct()
            .sorted()
        val hdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mode.supportedHdrTypes
        } else {
            @Suppress("DEPRECATION")
            display.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
        }
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        val autoBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        val timeout = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)

        return infoGroups {
            group(context.getString(R.string.group_panel)) {
                item(context.getString(R.string.label_resolution), "$width × $height")
                item(
                    context.getString(R.string.label_shape),
                    context.getString(if (config.isScreenRound) R.string.value_round else R.string.value_rectangular),
                )
                item(
                    context.getString(R.string.label_diagonal),
                    "${Format.decimal(diagonalInches, 2)}\" (${Format.decimal(diagonalInches * 25.4, 1)} mm)",
                )
                item(context.getString(R.string.label_pixel_density), "${((metrics.xdpi + metrics.ydpi) / 2).roundToInt()} ppi")
                item(context.getString(R.string.label_refresh_rate), "${Format.decimal(mode.refreshRate.toDouble(), 0)} Hz")
                item(context.getString(R.string.label_supported_rates), refreshRates.joinToString(", ") { "$it Hz" })
                item(context.getString(R.string.label_display_name), display.name)
            }
            group(context.getString(R.string.group_color)) {
                item(context.getString(R.string.label_wide_color), context.supported(display.isWideColorGamut))
                item(
                    context.getString(R.string.label_hdr),
                    if (hdrTypes.isEmpty()) {
                        context.getString(R.string.value_not_supported)
                    } else {
                        hdrTypes.joinToString(", ") { hdrName(it) }
                    },
                )
                item(context.getString(R.string.label_color_space), display.preferredWideGamutColorSpace?.name)
            }
            group(context.getString(R.string.group_interface)) {
                item(context.getString(R.string.label_density), "${metrics.densityDpi} dpi (${densityBucket(metrics.densityDpi)})")
                item(context.getString(R.string.label_logical_size), "${config.screenWidthDp} × ${config.screenHeightDp} dp")
                item(context.getString(R.string.label_font_scale), "${Format.decimal(config.fontScale.toDouble(), 2)}×")
            }
            group(context.getString(R.string.group_settings)) {
                if (brightness >= 0) {
                    val fraction = brightness / 255f
                    item(
                        context.getString(R.string.label_brightness),
                        Format.percent(fraction.toDouble()),
                        progress = fraction,
                    )
                }
                if (autoBrightness >= 0) {
                    item(
                        context.getString(R.string.label_auto_brightness),
                        context.onOff(autoBrightness == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC),
                    )
                }
                if (timeout > 0) {
                    item(context.getString(R.string.label_screen_timeout), context.getString(R.string.fmt_seconds, timeout / 1000))
                }
            }
        }
    }

    private fun hdrName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> "#$type"
    }

    private fun densityBucket(dpi: Int): String = when {
        dpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
        dpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
        dpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
        dpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
        dpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
        else -> "xxxhdpi"
    }
}
