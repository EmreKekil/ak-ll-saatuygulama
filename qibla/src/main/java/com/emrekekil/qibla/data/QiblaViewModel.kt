package com.emrekekil.qibla.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emrekekil.qibla.geo.Angles
import com.emrekekil.qibla.geo.LatLon
import com.emrekekil.qibla.geo.MagneticField
import com.emrekekil.qibla.geo.Qibla
import com.emrekekil.qibla.geo.WorldMagneticModel
import com.emrekekil.qibla.notify.PrayerAlarms
import com.emrekekil.qibla.notify.PrayerWork
import com.emrekekil.qibla.prayer.AlertSetting
import com.emrekekil.qibla.prayer.DayTimes
import com.emrekekil.qibla.prayer.Prayer
import com.emrekekil.qibla.prayer.PrayerEngine
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

    // ---------------------------------------------------------------- Ezan vakitleri

    private val prayerEngine = PrayerEngine(application)

    private val _prayer = MutableStateFlow<PrayerState?>(null)
    val prayer: StateFlow<PrayerState?> = _prayer.asStateFlow()

    private val _alerts = MutableStateFlow<Map<Prayer, AlertSetting>>(emptyMap())
    val alerts: StateFlow<Map<Prayer, AlertSetting>> = _alerts.asStateFlow()

    init {
        viewModelScope.launch {
            val (table, wmm) = withContext(Dispatchers.IO) {
                val wmm = application.assets.open("WMM.COF").bufferedReader().use { it.readText() }
                PlaceStore.get(application) to WorldMagneticModel.parse(wmm)
            }
            model = wmm
            _places.value = table
            _manual.value = settings.manualDistrict?.let { (plate, name) -> table.find(plate, name) }
            val lastKnown = withContext(Dispatchers.IO) { locations.bestKnown() }
            _lastKnown.value = lastKnown
            _loaded.value = true
            withContext(Dispatchers.IO) {
                lastKnown?.let { prayerEngine.onPosition(it.location) }
                PrayerWork.ensureScheduled(application)
                if (prayerEngine.needsRefresh()) PrayerWork.requestSync(application)
                PrayerAlarms.schedule(application)
            }
            reloadPrayer()
        }
    }

    /** Vakitleri ve etkin konumu yeniden okur (gün değişince, konum ya da veri güncellenince). */
    fun reloadPrayer() {
        viewModelScope.launch {
            _prayer.value = withContext(Dispatchers.IO) {
                val home = prayerEngine.home()
                val active = prayerEngine.active()
                PrayerState(home = home, active = active, days = prayerEngine.daysAround())
            }
            _alerts.value = prayerEngine.settings.alerts()
        }
    }

    private fun onNewPosition(fix: Fix) {
        viewModelScope.launch(Dispatchers.IO) {
            if (prayerEngine.onPosition(fix.location)) {
                if (prayerEngine.needsRefresh()) PrayerWork.requestSync(getApplication())
                PrayerAlarms.schedule(getApplication())
                reloadPrayer()
            }
        }
    }

    fun setHome(district: District) {
        viewModelScope.launch(Dispatchers.IO) {
            prayerEngine.setHome(district)
            _lastKnown.value?.let { prayerEngine.onPosition(it.location) }
            if (prayerEngine.needsRefresh()) PrayerWork.requestSync(getApplication())
            PrayerAlarms.schedule(getApplication())
            reloadPrayer()
        }
    }

    fun setAlert(prayer: Prayer, setting: AlertSetting) {
        prayerEngine.settings.setAlert(prayer, setting)
        _alerts.value = _alerts.value + (prayer to setting)
        viewModelScope.launch(Dispatchers.IO) { PrayerAlarms.schedule(getApplication()) }
    }

    /** Bildirim izni bir kez istenir; reddedilirse tekrar sorulmaz. */
    fun shouldAskNotificationPermission(): Boolean = !prayerEngine.settings.notificationPermissionAsked

    fun onNotificationPermissionAsked() {
        prayerEngine.settings.notificationPermissionAsked = true
    }

    val hasLocationPermission: Boolean get() = locations.hasAnyPermission()

    private val _userSearch = MutableStateFlow(false)

    /** GPS araması kullanıcı tarafından mı başlatıldı? Yalnızca o zaman ekran açık tutulur. */
    val userSearch: StateFlow<Boolean> = _userSearch.asStateFlow()

    /** Saatin GNSS alıcısıyla konum aramaya başlar (zaten sürüyorsa bir şey yapmaz). */
    fun startGps(userInitiated: Boolean = true) {
        if (userInitiated) _userSearch.value = true
        if (gpsJob?.isActive == true) return
        gpsJob = viewModelScope.launch {
            // Otomatik arama kısa tutulur: bulunamazsa son bilinen konumla devam edilir.
            val flow = if (userInitiated) locations.acquire() else locations.acquire(timeoutMs = 60_000, settleMs = 5_000)
            flow.collect { state ->
                _gps.value = state
                if (state is GpsState.Fixed) _lastKnown.value = state.fix
            }
            // Zaman aşımı veya iyi konumla bittiğinde son durum ekranda kalır.
            _userSearch.value = false
            (_gps.value as? GpsState.Fixed)?.let { onNewPosition(it.fix) }
        }
    }

    fun stopGps() {
        gpsJob?.cancel()
        gpsJob = null
        _userSearch.value = false
        val state = _gps.value
        if (state is GpsState.Searching) _gps.value = GpsState.Idle
    }

    /**
     * Uygulama ön plana geldiğinde. Uygulama günde birçok kez açıldığı için GPS her açılışta
     * çalıştırılmaz: önce donanımı çalıştırmayan son bilinen konuma bakılır, GPS yalnızca bilinen
     * konum [GPS_AFTER_MS]'den eskiyse arka planda (ekranı açık tutmadan) kısa süre çalışır.
     */
    fun onForeground() {
        reloadPrayer()
        if (!locations.hasAnyPermission()) return
        viewModelScope.launch {
            val known = withContext(Dispatchers.IO) { locations.bestKnown() }
            if (known != null) {
                _lastKnown.value = known
                onNewPosition(known)
            }
            val fresh = known != null && System.currentTimeMillis() - known.utcMillis < GPS_AFTER_MS
            if (settings.manualDistrict == null && !fresh) startGps(userInitiated = false)
        }
    }

    fun onPermissionResult() {
        locations.bestKnown()?.let {
            _lastKnown.value = it
            onNewPosition(it)
        }
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
        /** Bilinen konum bundan eskiyse açılışta GPS kısa süre çalıştırılır. */
        const val GPS_AFTER_MS = 6 * 60 * 60_000L
    }
}

/** Ezan vakti sayfasının durumu. */
data class PrayerState(val home: District, val active: District, val days: List<DayTimes>) {
    val isTemporary: Boolean get() = active != home
}

/** Elle seçilen ilçe ile GPS konumunun karşılaştırması. */
data class GpsCheck(val distanceKm: Double, val gpsBearing: Double, val tableBearing: Double) {
    val bearingDifference: Double get() = Angles.signedDelta(tableBearing, gpsBearing)
}
