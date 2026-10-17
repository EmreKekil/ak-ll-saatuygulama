package com.emrekekil.qibla.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.emrekekil.qibla.geo.LatLon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Kalıcı olarak saklanabilen bir konum. Saat dilimi yerine gerçek UTC zamanı taşır. */
data class Fix(
    val location: LatLon,
    /** WGS84 elipsoidi üzerindeki yükseklik (WMM'nin beklediği referans). */
    val altitudeM: Double?,
    val accuracyM: Float?,
    /** "gps" = saatin kendi GNSS alıcısı; diğerleri (fused, network…) telefon kaynaklı olabilir. */
    val provider: String,
    val utcMillis: Long,
    val fromCache: Boolean = false,
) {
    val isWatchGps: Boolean get() = provider == LocationManager.GPS_PROVIDER
}

data class Satellites(val inView: Int, val usedInFix: Int)

sealed interface GpsState {
    data object Idle : GpsState
    data object NoPermission : GpsState
    data object LocationOff : GpsState
    data object NoHardware : GpsState
    data class Searching(val satellites: Satellites?, val elapsedMs: Long) : GpsState
    data class Fixed(val fix: Fix, val satellites: Satellites?) : GpsState
    data class TimedOut(val satellites: Satellites?) : GpsState
}

/**
 * Saatin kendi GNSS alıcısından konum alır. İnternet veya telefon gerekmez.
 *
 * Fused sağlayıcı yerine GPS_PROVIDER kullanılır: Wear OS'ta fused konum bağlı telefondan gelebilir,
 * bu da "saatin kendisi hesaplasın" amacını ve GPS doğrulamasını boşa çıkarır. getCurrentLocation()
 * da 30 sn ile sınırlı olduğundan, yardım verisi olmayan soğuk başlangıç için sürekli güncelleme
 * istenir ve kendi zaman aşımımız uygulanır.
 */
class LocationRepository(context: Context) {

    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)
    private val prefs = app.getSharedPreferences("last_fix", Context.MODE_PRIVATE)
    private val mainExecutor = ContextCompat.getMainExecutor(app)

    fun hasFinePermission() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasAnyPermission() = hasFinePermission() || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    fun hasGnss(): Boolean =
        app.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) &&
            LocationManagerCompat.hasProvider(manager, LocationManager.GPS_PROVIDER)

    fun loadCached(): Fix? {
        if (!prefs.contains("lat")) return null
        return Fix(
            location = LatLon(Double.fromBits(prefs.getLong("lat", 0)), Double.fromBits(prefs.getLong("lon", 0))),
            altitudeM = if (prefs.contains("alt")) Double.fromBits(prefs.getLong("alt", 0)) else null,
            accuracyM = if (prefs.contains("acc")) prefs.getFloat("acc", 0f) else null,
            provider = prefs.getString("provider", null) ?: "?",
            utcMillis = prefs.getLong("time", 0),
            fromCache = true,
        )
    }

    private fun save(fix: Fix) {
        prefs.edit {
            putLong("lat", fix.location.latitude.toBits())
            putLong("lon", fix.location.longitude.toBits())
            if (fix.altitudeM != null) putLong("alt", fix.altitudeM.toBits()) else remove("alt")
            if (fix.accuracyM != null) putFloat("acc", fix.accuracyM) else remove("acc")
            putString("provider", fix.provider)
            putLong("time", fix.utcMillis)
        }
    }

    /**
     * Donanımı çalıştırmadan bilinen en yeni konum: uygulamanın kendi önbelleği ve sistemin son
     * bilinen konumları. Konum zamanla değil, yer değiştirdikçe eskir; bu yüzden süre sınırı yoktur.
     */
    @SuppressLint("MissingPermission")
    fun bestKnown(): Fix? {
        val system = if (hasAnyPermission()) {
            listOf(LocationManager.GPS_PROVIDER, FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { LocationManagerCompat.hasProvider(manager, it) }
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .filterNot { LocationCompat.isMock(it) }
                .map { it.toFix(fromCache = true) }
        } else {
            emptyList()
        }
        return (system + listOfNotNull(loadCached())).maxByOrNull { it.utcMillis }
    }

    /**
     * Toplandığı sürece saatin GNSS alıcısını çalıştırır; iyi bir konum gelince veya zaman aşımında
     * kendiliğinden biter. Toplama iptal edilirse (ekran kapanınca) donanım hemen durur.
     */
    @SuppressLint("MissingPermission")
    fun acquire(
        timeoutMs: Long = 180_000,
        goodAccuracyM: Float = 50f,
        settleMs: Long = 10_000,
    ): Flow<GpsState> = callbackFlow {
        when {
            !hasAnyPermission() -> {
                trySend(GpsState.NoPermission)
                close()
                return@callbackFlow
            }
            !LocationManagerCompat.isLocationEnabled(manager) -> {
                trySend(GpsState.LocationOff)
                close()
                return@callbackFlow
            }
            !hasGnss() -> {
                trySend(GpsState.NoHardware)
                close()
                return@callbackFlow
            }
        }

        val start = SystemClock.elapsedRealtime()
        var satellites: Satellites? = null
        var best: Fix? = null
        var firstFixAt = 0L
        trySend(GpsState.Searching(null, 0))

        // Uydu sayıları ilerleme göstergesi olarak kullanılır (yalnızca hassas konum izniyle).
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                satellites = Satellites(status.satelliteCount, used)
                if (best == null) trySend(GpsState.Searching(satellites, SystemClock.elapsedRealtime() - start))
            }
        }
        if (hasFinePermission()) runCatching { manager.registerGnssStatusCallback(mainExecutor, gnssCallback) }

        val listener = object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                if (LocationCompat.isMock(location)) return
                val fix = location.toFix(fromCache = false)
                if (best == null) firstFixAt = SystemClock.elapsedRealtime()
                val current = best
                if (current == null || (fix.accuracyM ?: Float.MAX_VALUE) <= (current.accuracyM ?: Float.MAX_VALUE)) best = fix
                val chosen = best ?: return
                trySend(GpsState.Fixed(chosen, satellites))
                val settled = SystemClock.elapsedRealtime() - firstFixAt >= settleMs
                if ((chosen.accuracyM ?: Float.MAX_VALUE) <= goodAccuracyM || settled) {
                    save(chosen)
                    close()
                }
            }

            override fun onProviderDisabled(provider: String) {
                trySend(GpsState.LocationOff)
                close()
            }
        }
        val request = LocationRequestCompat.Builder(1_000L)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(0)
            .setDurationMillis(timeoutMs)
            .build()
        try {
            LocationManagerCompat.requestLocationUpdates(manager, LocationManager.GPS_PROVIDER, request, mainExecutor, listener)
        } catch (_: SecurityException) {
            // Android 11'de yalnızca yaklaşık konum izniyle GPS sağlayıcısı istenemez.
            trySend(GpsState.NoPermission)
            close()
        }

        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            val fix = best
            if (fix == null) {
                trySend(GpsState.TimedOut(satellites))
            } else {
                save(fix)
            }
            close()
        }
        handler.postDelayed(timeout, timeoutMs)

        awaitClose {
            handler.removeCallbacks(timeout)
            LocationManagerCompat.removeUpdates(manager, listener)
            runCatching { manager.unregisterGnssStatusCallback(gnssCallback) }
        }
    }

    private fun Location.toFix(fromCache: Boolean) = Fix(
        location = LatLon(latitude, longitude),
        altitudeM = if (hasAltitude()) altitude else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        provider = provider ?: "?",
        utcMillis = time,
        fromCache = fromCache,
    )

    private companion object {
        // LocationManager.FUSED_PROVIDER API 31'de eklendi; değer eski sürümlerde de aynıdır.
        const val FUSED_PROVIDER = "fused"
    }
}
