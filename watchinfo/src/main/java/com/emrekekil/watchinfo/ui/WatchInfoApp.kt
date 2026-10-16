package com.emrekekil.watchinfo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.emrekekil.watchinfo.data.DeviceInfoRepository
import com.emrekekil.watchinfo.model.Category
import com.emrekekil.watchinfo.ui.detail.DetailScreen
import com.emrekekil.watchinfo.ui.home.HomeScreen
import com.emrekekil.watchinfo.ui.sensors.SensorDetailScreen
import com.emrekekil.watchinfo.ui.sensors.SensorListScreen
import com.emrekekil.watchinfo.ui.theme.WatchInfoTheme

private object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{category}"
    const val SENSORS = "sensors"
    const val SENSOR = "sensor/{index}"

    fun detail(category: Category) = "detail/${category.name}"
    fun sensor(index: Int) = "sensor/$index"
}

@Composable
fun WatchInfoApp() {
    val context = LocalContext.current
    val repository = remember(context) { DeviceInfoRepository(context) }

    WatchInfoTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(repository) { category ->
                        navController.navigate(
                            if (category == Category.SENSORS) Routes.SENSORS else Routes.detail(category),
                        )
                    }
                }
                composable(Routes.DETAIL) { entry ->
                    val category = entry.arguments?.getString("category")
                        ?.let { name -> Category.entries.firstOrNull { it.name == name } }
                        ?: Category.SYSTEM
                    DetailScreen(repository, category)
                }
                composable(Routes.SENSORS) {
                    SensorListScreen { index -> navController.navigate(Routes.sensor(index)) }
                }
                composable(Routes.SENSOR) { entry ->
                    SensorDetailScreen(entry.arguments?.getString("index")?.toIntOrNull() ?: -1)
                }
            }
        }
    }
}
