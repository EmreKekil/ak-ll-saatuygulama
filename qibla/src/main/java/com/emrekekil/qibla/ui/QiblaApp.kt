package com.emrekekil.qibla.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import com.emrekekil.qibla.ui.theme.QiblaTheme
import kotlinx.coroutines.launch

private object Routes {
    const val MAIN = "main"
    const val PROVINCES = "provinces"
    const val DISTRICTS = "districts/{plate}"
    const val CALIBRATION = "calibration"

    fun districts(plate: Int) = "districts/$plate"
}

private const val PAGE_COMPASS = 0
private const val PAGE_DETAILS = 1
private const val PAGE_LOCATION = 2

@Composable
fun QiblaApp(vm: QiblaViewModel = viewModel()) {
    val info by vm.qibla.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val manual by vm.manualDistrict.collectAsStateWithLifecycle()
    val gps by vm.gps.collectAsStateWithLifecycle()
    val gpsCheck by vm.gpsCheck.collectAsStateWithLifecycle()
    val flipOverride by vm.flipOverride.collectAsStateWithLifecycle()
    val displayOn = rememberDisplayOn()

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
            // Soğuk GPS başlangıcı dakikalar sürebilir; arama sürerken ekran kapanırsa arama durur.
            Box(Modifier.fillMaxSize().then(if (gps is GpsState.Searching) Modifier.keepScreenOn() else Modifier)) {
                SwipeDismissableNavHost(navController = navController, startDestination = Routes.MAIN) {
                    composable(Routes.MAIN) {
                        val pagerState = rememberPagerState(pageCount = { 3 })
                        val scope = rememberCoroutineScope()
                        val compass = rememberCompassState(
                            active = displayOn && pagerState.currentPage != PAGE_LOCATION,
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
                                            onPickCity = { navController.navigate(Routes.PROVINCES) },
                                            onUseAutomatic = vm::useAutomaticLocation,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    composable(Routes.PROVINCES) {
                        places?.let { table ->
                            ProvinceListScreen(table) { plate -> navController.navigate(Routes.districts(plate)) }
                        }
                    }
                    composable(Routes.DISTRICTS) { entry ->
                        val plate = entry.arguments?.getString("plate")?.toIntOrNull() ?: 0
                        places?.let { table ->
                            DistrictListScreen(table, plate) { district ->
                                vm.selectDistrict(district)
                                navController.popBackStack(Routes.MAIN, inclusive = false)
                            }
                        }
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
