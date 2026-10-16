package com.emrekekil.watchinfo.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.emrekekil.watchinfo.R

/**
 * Ana ekrandaki bilgi kategorileri.
 *
 * @param refreshMillis Detay ekranı açıkken verinin yenilenme aralığı; null ise bir kez okunur.
 */
enum class Category(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    val refreshMillis: Long?,
) {
    SYSTEM(R.string.cat_system, R.drawable.ic_system, 1_000),
    CPU(R.string.cat_cpu, R.drawable.ic_cpu, 1_000),
    GPU(R.string.cat_gpu, R.drawable.ic_gpu, null),
    MEMORY(R.string.cat_memory, R.drawable.ic_memory, 2_000),
    STORAGE(R.string.cat_storage, R.drawable.ic_storage, null),
    DISPLAY(R.string.cat_display, R.drawable.ic_display, 3_000),
    BATTERY(R.string.cat_battery, R.drawable.ic_battery, 2_000),
    THERMAL(R.string.cat_thermal, R.drawable.ic_thermal, 2_000),
    CONNECTIVITY(R.string.cat_connectivity, R.drawable.ic_connectivity, 3_000),
    SENSORS(R.string.cat_sensors, R.drawable.ic_sensors, null),
}
