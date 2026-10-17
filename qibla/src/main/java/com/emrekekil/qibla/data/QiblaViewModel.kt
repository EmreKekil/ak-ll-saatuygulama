package com.emrekekil.qibla.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emrekekil.qibla.geo.Angles
import com.emrekekil.qibla.geo.LatLon
import com.emrekekil.qibla.geo.MagneticField
import com.emrekekil.qibla.geo.Qibla
import com.emrekekil.qibla.geo.WorldMagneticModel
import com.emrekekil.qibla.geo.decimalYear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kıble hesabının dayandığı konumun kaynağı. */
sealed interface PositionSource {
    /** Bu oturumda saatin GNSS alıcısından alınan konum. */
    data class Gps(val fix: Fix) : PositionSource

    /** Önceki bir oturumdan veya sistemden kalan son konum. */
    data class LastKnown(val fix: Fix) : PositionSource

    /** Gömülü tablodan elle seçilen ilçe merkezi. */
    data class Table(val district: District) : PositionSource
}

/** Ekranların ihtiyaç duyduğu tüm kıble bilgisi; tamamen cihaz üzerinde hesaplanır. */
data class QiblaInfo(
    val position: LatLon,
    val source: PositionSource,
    /** Gerçek kuzeye göre kıble açısı. */
    val bearing: Double,
    val distanceKm: Double,
    /** Konumdaki beklenen manyetik alan (WMM2025). */
    val magneticField: MagneticField,
    val modelName: String,
    val modelExpired: Boolean,
    val nearest: District?,
    val nearestKm: Double?,
) {
    /** Manyetik sapma, doğu pozitif: gerçek yön = manyetik yön + sapma. */
    val declination: Double get() = magneticField.declination

    /** Manyetik kuzeye göre kıble açısı (sapma düzeltilmemiş bir pusulada okunacak değer). */
    val magneticBearing: Double get() = Angles.normalize(bearing - declination)
}

class QiblaViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = QiblaSettings(application)
    private val locations = LocationRepository(application)

    private val _places = MutableStateFlow<PlaceTable?>(null)
    val places: StateFlow<PlaceTable?> = _places.asStateFlow()

    private var model: WorldMagneticModel? = null

    private val _manual = MutableStateFlow<District?>(null)
    val manualDistrict: StateFlow<District?> = _manual.asStateFlow()

    private val _lastKnown = MutableStateFlow<Fix?>(null)
    private val _gps = MutableStateFlow<GpsState>(GpsState.Idle)
    val gps: StateFlow<GpsState> = _gps.asStateFlow()

    private val _flipOverride = MutableStateFlow(settings.flipOverride)
    val flipOverride: StateFlow<Boolean?> = _flipOverride.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** Seçili/elde edilen konuma göre kıble bilgisi; konum yoksa null. */
    val qibla: StateFlow<QiblaInfo?> =
        combine(_places, _manual, _lastKnown, _gps, _loaded) { places, manual, lastKnown, gps, loaded ->
            if (!loaded) return@combine null
            val source: PositionSource = when {
                manual != null -> PositionSource.Table(manual)
                gps is GpsState.Fixed -> PositionSource.Gps(gps.fix)
                lastKnown != null -> PositionSource.LastKnown(lastKnown)
                else -> return@combine null
            }
            compute(source, places)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** GPS ile seçili ilçe arasındaki karşılaştırma (doğrulama). */
    val gpsCheck: StateFlow<GpsCheck?> =
        combine(_gps, _manual) { gps, manual ->
            if (gps !is GpsState.Fixed || manual == null) return@combine null
            val gpsBearing = Qibla.bearing(gps.fix.location)
            GpsCheck(
                distanceKm = Qibla.distanceKm(gps.fix.location, manual.location),
                gpsBearing = gpsBearing,
                tableBearing = manual.qiblaBearing,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var gpsJob: Job? = null

    init {
        viewModelScope.launch {
            val (table, wmm) = withContext(Dispatchers.IO) {
                val assets = application.assets
                val table = PlaceTable.parse(assets.open("tr_districts.csv").bufferedReader().use { it.readText() })
                val wmm = WorldMagneticModel.parse(assets.open("WMM.COF").bufferedReader().use { it.readText() })
                table to wmm
            }
            model = wmm
            _places.value = table
            _manual.value = settings.manualDistrict?.let { (plate, name) -> table.find(plate, name) }
            _lastKnown.value = withContext(Dispatchers.IO) { locations.bestKnown() }
            _loaded.value = true
        }
    }

    val hasLocationPermission: Boolean get() = locations.hasAnyPermission()

    /** Saatin GNSS alıcısıyla konum aramaya başlar (zaten sürüyorsa bir şey yapmaz). */
    fun startGps() {
        if (gpsJob?.isActive == true) return
        gpsJob = viewModelScope.launch {
            locations.acquire().collect { state ->
                _gps.value = state
                if (state is GpsState.Fixed) _lastKnown.value = state.fix
            }
            // Zaman aşımı veya iyi konumla bittiğinde son durum ekranda kalır.
        }
    }

    fun stopGps() {
        gpsJob?.cancel()
        gpsJob = null
        val state = _gps.value
        if (state is GpsState.Searching) _gps.value = GpsState.Idle
    }

    /**
     * Uygulama ön plana geldiğinde: otomatik modda ve izin varsa konumu tazele. Ayarlar doğrudan
     * okunur, çünkü bu çağrı tablo yüklenmeden önce gelebilir.
     */
    fun onForeground() {
        if (settings.manualDistrict != null || !locations.hasAnyPermission()) return
        val fixed = _gps.value as? GpsState.Fixed
        if (fixed == null || System.currentTimeMillis() - fixed.fix.utcMillis > REFRESH_AFTER_MS) startGps()
    }

    fun onPermissionResult() {
        _lastKnown.value = locations.bestKnown()
        startGps()
    }

    fun selectDistrict(district: District) {
        settings.manualDistrict = district.plate to district.name
        _manual.value = district
    }

    fun useAutomaticLocation() {
        settings.manualDistrict = null
        _manual.value = null
        onForeground()
    }

    fun setFlipOverride(value: Boolean?) {
        settings.flipOverride = value
        _flipOverride.value = value
    }

    private fun compute(source: PositionSource, places: PlaceTable?): QiblaInfo? {
        val wmm = model ?: return null
        val (position, altitude) = when (source) {
            is PositionSource.Gps -> source.fix.location to (source.fix.altitudeM ?: 0.0)
            is PositionSource.LastKnown -> source.fix.location to (source.fix.altitudeM ?: 0.0)
            is PositionSource.Table -> source.district.location to 0.0
        }
        val year = decimalYear(System.currentTimeMillis())
        val nearest = places?.nearest(position)
        return QiblaInfo(
            position = position,
            source = source,
            bearing = Qibla.bearing(position),
            distanceKm = Qibla.distanceKm(position),
            magneticField = wmm.calculate(position.latitude, position.longitude, altitude, year),
            modelName = wmm.modelName,
            modelExpired = year >= wmm.validUntil,
            nearest = nearest?.first,
            nearestKm = nearest?.second,
        )
    }

    private companion object {
        /** Bu süreden eski bir GPS konumu, uygulama yeniden açıldığında tazelenir. */
        const val REFRESH_AFTER_MS = 15 * 60_000L
    }
}

/** Elle seçilen ilçe ile GPS konumunun karşılaştırması. */
data class GpsCheck(val distanceKm: Double, val gpsBearing: Double, val tableBearing: Double) {
    val bearingDifference: Double get() = Angles.signedDelta(tableBearing, gpsBearing)
}
