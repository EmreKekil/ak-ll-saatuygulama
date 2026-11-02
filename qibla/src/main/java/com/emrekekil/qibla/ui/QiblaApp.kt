package com.emrekekil.qibla.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.emrekekil.qibla.data.GpsState
import com.emrekekil.qibla.data.QiblaViewModel
import com.emrekekil.qibla.ui.calibration.CalibrationScreen
import com.emrekekil.qibla.ui.compass.CompassPage
import com.emrekekil.qibla.ui.compass.rememberCompassState
import com.emrekekil.qibla.ui.compass.rememberDisplayOn
import com.emrekekil.qibla.ui.details.DetailsPage
import com.emrekekil.qibla.ui.location.DistrictListScreen
import com.emrekekil.qibla.ui.location.LocationPage
import com.emrekekil.qibla.ui.location.ProvinceListScreen
import com.emrekekil.qibla.ui.prayer.AlertSettingsScreen
import com.emrekekil.qibla.ui.prayer.PrayerPage
import com.emrekekil.qibla.ui.theme.QiblaTheme
import kotlinx.coroutines.launch

private object Routes {
    const val MAIN = "main"
    const val PROVINCES = "provinces/{target}"
    const val DISTRICTS = "districts/{target}/{plate}"
    const val CALIBRATION = "calibration"
    const val ALERTS = "alerts"

    /** İl/ilçe seçiminin amacı: kıble için konum veya ezan vakti ana konumu. */
    const val TARGET_QIBLA = "qibla"
    const val TARGET_HOME = "home"

    fun provinces(target: String) = "provinces/$target"
    fun districts(target: String, plate: Int) = "districts/$target/$plate"
}

// Vakitler açılış sayfasıdır; soldan sağa kaydırınca kıble pusulası gelir.
private const val PAGE_COMPASS = 0
private const val PAGE_PRAYER = 1
private const val PAGE_DETAILS = 2
private const val PAGE_LOCATION = 3
private const val PAGE_COUNT = 4

@Composable
fun QiblaApp(vm: QiblaViewModel = viewModel()) {
    val info by vm.qibla.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val manual by vm.manualDistrict.collectAsStateWithLifecycle()
    val gps by vm.gps.collectAsStateWithLifecycle()
    val gpsCheck by vm.gpsCheck.collectAsStateWithLifecycle()
    val flipOverride by vm.flipOverride.collectAsStateWithLifecycle()
    val prayer by vm.prayer.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val userSearch by vm.userSearch.collectAsStateWithLifecycle()
    val displayOn = rememberDisplayOn()

    // Bildirim izni (Android 13+) ilk açılışta bir kez istenir.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vm.shouldAskNotificationPermission()) {
            vm.onNotificationPermissionAsked()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Uygulama görünür olunca otomatik modda GPS'i tazele; arka plana geçince GNSS'i durdur.
    LifecycleStartEffect(vm) {
        vm.onForeground()
        onStopOrDispose { vm.stopGps() }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }
    val useGps = {
        if (vm.hasLocationPermission) {
            vm.startGps()
        } else {
            // Android 12+ hassas izin isteğinin dikkate alınması için ikisi birlikte istenmeli.
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    QiblaTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            // Kullanıcının başlattığı GPS araması sürerken ekran açık kalır (kapanırsa arama durur).
            // Açılıştaki otomatik arama ekranı açık tutmaz; pil için.
            val keepOn = userSearch && gps is GpsState.Searching
            Box(Modifier.fillMaxSize().then(if (keepOn) Modifier.keepScreenOn() else Modifier)) {
                SwipeDismissableNavHost(navController = navController, startDestination = Routes.MAIN) {
                    composable(Routes.MAIN) {
                        val pagerState = rememberPagerState(initialPage = PAGE_PRAYER, pageCount = { PAGE_COUNT })
                        val scope = rememberCoroutineScope()
                        // Sensörler yalnızca pusula sayfası görünür ve ekran açıkken çalışır.
                        val compass = rememberCompassState(
                            active = displayOn && pagerState.currentPage == PAGE_COMPASS,
                            expectedField = info?.magneticField,
                            flipOverride = flipOverride,
                            alignTarget = info?.magneticBearing,
                        )
                        HorizontalPagerScaffold(pagerState = pagerState) {
                            HorizontalPager(state = pagerState) { page ->
                                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                                    when (page) {
                                        PAGE_COMPASS -> CompassPage(
                                            info = info,
                                            state = compass,
                                            active = displayOn && pagerState.currentPage == PAGE_COMPASS,
                                            onCalibrate = { navController.navigate(Routes.CALIBRATION) },
                                            onOpenLocation = { scope.launch { pagerState.animateScrollToPage(PAGE_LOCATION) } },
                                        )
                                        PAGE_PRAYER -> PrayerPage(
                                            state = prayer,
                                            active = displayOn && pagerState.currentPage == PAGE_PRAYER,
                                            onDayChanged = vm::reloadPrayer,
                                            onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                                        )
                                        PAGE_DETAILS -> DetailsPage(
                                            info = info,
                                            compass = compass,
                                            flipOverride = flipOverride,
                                            onFlipOverride = vm::setFlipOverride,
                                            onCalibrate = { navController.navigate(Routes.CALIBRATION) },
                                        )
                                        else -> LocationPage(
                                            info = info,
                                            manual = manual,
                                            gps = gps,
                                            gpsCheck = gpsCheck,
                                            onUseGps = useGps,
                                            onStopGps = vm::stopGps,
                                            onPickCity = { navController.navigate(Routes.provinces(Routes.TARGET_QIBLA)) },
                                            onUseAutomatic = vm::useAutomaticLocation,
                                            prayer = prayer,
                                            onPickHome = { navController.navigate(Routes.provinces(Routes.TARGET_HOME)) },
                                            onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    composable(Routes.PROVINCES) { entry ->
                        val target = entry.arguments?.getString("target") ?: Routes.TARGET_QIBLA
                        places?.let { table ->
                            ProvinceListScreen(table) { plate -> navController.navigate(Routes.districts(target, plate)) }
                        }
                    }
                    composable(Routes.DISTRICTS) { entry ->
                        val target = entry.arguments?.getString("target") ?: Routes.TARGET_QIBLA
                        val plate = entry.arguments?.getString("plate")?.toIntOrNull() ?: 0
                        places?.let { table ->
                            DistrictListScreen(table, plate) { district ->
                                if (target == Routes.TARGET_HOME) vm.setHome(district) else vm.selectDistrict(district)
                                navController.popBackStack(Routes.MAIN, inclusive = false)
                            }
                        }
                    }
                    composable(Routes.ALERTS) {
                        AlertSettingsScreen(alerts, vm::setAlert)
                    }
                    composable(Routes.CALIBRATION) {
                        val compass = rememberCompassState(
                            active = displayOn,
                            expectedField = info?.magneticField,
                            flipOverride = flipOverride,
                            alignTarget = null,
                        )
                        CalibrationScreen(compass) { navController.popBackStack() }
                    }
                }
            }
        }
    }
}
