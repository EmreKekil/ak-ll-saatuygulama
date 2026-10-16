package com.emrekekil.watchinfo.data

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.InfoItemsBuilder
import com.emrekekil.watchinfo.model.infoGroups

object ConnectivityCollector {

    // PackageManager.FEATURE_UWB yalnızca API 34+ SDK'sında tanımlı; değer eski sürümlerde de geçerli.
    private const val FEATURE_UWB = "android.hardware.uwb"

    fun summary(context: Context): String {
        val pm = context.packageManager
        return listOfNotNull(
            "Wi-Fi".takeIf { pm.hasSystemFeature(PackageManager.FEATURE_WIFI) },
            "BT".takeIf { pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) },
            "NFC".takeIf { pm.hasSystemFeature(PackageManager.FEATURE_NFC) },
            "LTE".takeIf { pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) },
            "GPS".takeIf { pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) },
            "UWB".takeIf { pm.hasSystemFeature(FEATURE_UWB) },
        ).joinToString(" · ")
    }

    fun collect(context: Context): List<InfoGroup> {
        val pm = context.packageManager
        return infoGroups {
            group(context.getString(R.string.group_network)) { network(context) }
            if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
                group(context.getString(R.string.group_wifi)) { wifi(context) }
            }
            if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                group(context.getString(R.string.group_bluetooth)) { bluetooth(context) }
            }
            if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                group(context.getString(R.string.group_cellular)) { cellular(context) }
            }
            group(context.getString(R.string.group_location)) { location(context) }
            group(context.getString(R.string.group_other_hardware)) { otherHardware(context) }
        }
    }

    private fun InfoItemsBuilder.network(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (caps == null) {
            item(context.getString(R.string.label_active_network), context.getString(R.string.value_no_connection))
            return
        }
        val transports = listOfNotNull(
            context.getString(R.string.value_transport_wifi).takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
            context.getString(R.string.value_transport_cellular).takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
            context.getString(R.string.value_transport_bluetooth).takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) },
            context.getString(R.string.value_transport_ethernet).takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) },
            "VPN".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) },
        )
        item(context.getString(R.string.label_active_network), transports.joinToString(", "))
        item(
            context.getString(R.string.label_internet),
            context.yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)),
        )
        item(
            context.getString(R.string.label_metered),
            context.yesNo(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)),
        )
        item(context.getString(R.string.label_downstream), formatKbps(caps.linkDownstreamBandwidthKbps))
        item(context.getString(R.string.label_upstream), formatKbps(caps.linkUpstreamBandwidthKbps))
    }

    private fun InfoItemsBuilder.wifi(context: Context) {
        val wm = context.getSystemService(WifiManager::class.java) ?: return
        item(context.getString(R.string.label_status), context.onOff(wm.isWifiEnabled))
        item(context.getString(R.string.label_band_5ghz), context.supported(wm.is5GHzBandSupported))
        item(context.getString(R.string.label_band_6ghz), context.supported(wm.is6GHzBandSupported))
        val standards = buildList {
            if (wm.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N)) add("Wi-Fi 4")
            if (wm.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC)) add("Wi-Fi 5")
            if (wm.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX)) add("Wi-Fi 6")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                wm.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE)
            ) {
                add("Wi-Fi 7")
            }
        }
        item(context.getString(R.string.label_wifi_standards), standards.joinToString(", "))
        item(
            context.getString(R.string.label_wifi_direct),
            context.supported(context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)),
        )
    }

    private fun InfoItemsBuilder.bluetooth(context: Context) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        // Bu çağrılar Android 12+ hedefleyen uygulamalarda izin gerektirmez; yine de
        // üretici farklılıklarına karşı SecurityException yakalanır.
        fun <T> safe(block: () -> T): T? = try { block() } catch (_: SecurityException) { null }

        safe { adapter.isEnabled }?.let { item(context.getString(R.string.label_status), context.onOff(it)) }
        item(
            context.getString(R.string.label_ble),
            context.supported(context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)),
        )
        safe { adapter.isLe2MPhySupported }?.let { item(context.getString(R.string.label_le_2m_phy), context.supported(it)) }
        safe { adapter.isLeCodedPhySupported }?.let { item(context.getString(R.string.label_le_coded_phy), context.supported(it)) }
        safe { adapter.isLeExtendedAdvertisingSupported }?.let {
            item(context.getString(R.string.label_le_extended_adv), context.supported(it))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            safe { adapter.isLeAudioSupported }?.let {
                item(context.getString(R.string.label_le_audio), context.supported(it == BluetoothStatusCodes.FEATURE_SUPPORTED))
            }
        }
    }

    private fun InfoItemsBuilder.cellular(context: Context) {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        item(context.getString(R.string.label_sim_state), context.getString(simStateName(tm.simState)))
        item(context.getString(R.string.label_operator), tm.networkOperatorName)
        item(
            context.getString(R.string.label_esim),
            context.getSystemService(EuiccManager::class.java)?.let { context.supported(it.isEnabled) },
        )
    }

    private fun InfoItemsBuilder.location(context: Context) {
        val pm = context.packageManager
        val lm = context.getSystemService(LocationManager::class.java)
        item(context.getString(R.string.label_gps), context.supported(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)))
        lm?.let {
            item(context.getString(R.string.label_location_service), context.onOff(it.isLocationEnabled))
            item(context.getString(R.string.label_gnss_model), it.gnssHardwareModelName)
            item(context.getString(R.string.label_gnss_year), it.gnssYearOfHardware.takeIf { year -> year > 0 }?.toString())
        }
    }

    private fun InfoItemsBuilder.otherHardware(context: Context) {
        val pm = context.packageManager
        val nfc = NfcAdapter.getDefaultAdapter(context)
        item(
            context.getString(R.string.label_nfc),
            if (nfc == null) context.getString(R.string.value_not_supported) else context.onOff(nfc.isEnabled),
        )
        item(context.getString(R.string.label_uwb), context.supported(pm.hasSystemFeature(FEATURE_UWB)))
        item(context.getString(R.string.label_speaker), context.supported(pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)))
        item(context.getString(R.string.label_microphone), context.supported(pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)))
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator != null) {
            item(
                context.getString(R.string.label_vibration),
                when {
                    !vibrator.hasVibrator() -> context.getString(R.string.value_not_supported)
                    vibrator.hasAmplitudeControl() -> context.getString(R.string.value_vibration_amplitude)
                    else -> context.getString(R.string.value_supported)
                },
            )
        }
    }

    private fun formatKbps(kbps: Int): String? = when {
        kbps <= 0 -> null
        kbps >= 1000 -> "${Format.decimal(kbps / 1000.0, 1)} Mbps"
        else -> "$kbps kbps"
    }

    private fun simStateName(state: Int): Int = when (state) {
        TelephonyManager.SIM_STATE_READY -> R.string.value_sim_ready
        TelephonyManager.SIM_STATE_ABSENT -> R.string.value_sim_absent
        TelephonyManager.SIM_STATE_PIN_REQUIRED,
        TelephonyManager.SIM_STATE_PUK_REQUIRED,
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> R.string.value_sim_locked
        TelephonyManager.SIM_STATE_NOT_READY -> R.string.value_sim_not_ready
        else -> R.string.value_unknown
    }
}
