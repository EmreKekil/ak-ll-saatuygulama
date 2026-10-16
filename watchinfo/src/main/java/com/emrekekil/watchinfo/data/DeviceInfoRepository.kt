package com.emrekekil.watchinfo.data

import android.content.Context
import com.emrekekil.watchinfo.model.Category
import com.emrekekil.watchinfo.model.InfoGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ana ekrandaki halka göstergeleri için anlık değerler. */
data class Dashboard(
    val battery: BatteryCollector.State?,
    val memory: MemoryCollector.Usage,
    val storage: StorageCollector.Usage,
    val summaries: Map<Category, String?>,
)

/**
 * Kategorilere göre bilgi toplayan tek giriş noktası. Okumaların bir kısmı dosya sistemi ve
 * süreç çağrısı içerdiği için hepsi IO iş parçacığında çalışır.
 */
class DeviceInfoRepository(context: Context) {

    private val context = context.applicationContext

    suspend fun collect(category: Category): List<InfoGroup> = withContext(Dispatchers.IO) {
        when (category) {
            Category.SYSTEM -> SystemCollector.collect(context)
            Category.CPU -> CpuCollector.collect(context)
            Category.GPU -> GpuCollector.collect(context)
            Category.MEMORY -> MemoryCollector.collect(context)
            Category.STORAGE -> StorageCollector.collect(context)
            Category.DISPLAY -> DisplayCollector.collect(context)
            Category.BATTERY -> BatteryCollector.collect(context)
            Category.THERMAL -> ThermalCollector.collect(context)
            Category.CONNECTIVITY -> ConnectivityCollector.collect(context)
            Category.SENSORS -> emptyList()
        }
    }

    suspend fun dashboard(): Dashboard = withContext(Dispatchers.IO) {
        Dashboard(
            battery = BatteryCollector.state(context),
            memory = MemoryCollector.usage(context),
            storage = StorageCollector.usage(),
            summaries = Category.entries.associateWith { summary(it) },
        )
    }

    private fun summary(category: Category): String? =
        try {
            when (category) {
                Category.SYSTEM -> SystemCollector.summary(context)
                Category.CPU -> CpuCollector.summary(context)
                Category.GPU -> GpuCollector.summary()
                Category.MEMORY -> MemoryCollector.summary(context)
                Category.STORAGE -> StorageCollector.summary()
                Category.DISPLAY -> DisplayCollector.summary(context)
                Category.BATTERY -> BatteryCollector.summary(context)
                Category.THERMAL -> ThermalCollector.summary(context)
                Category.CONNECTIVITY -> ConnectivityCollector.summary(context)
                Category.SENSORS -> SensorCatalog.summary(context)
            }
        } catch (_: Exception) {
            // Tek bir kategorideki beklenmedik hata ana ekranın tamamını düşürmemeli.
            null
        }
}
