package com.emrekekil.watchinfo.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups

object MemoryCollector {

    /** Ana ekrandaki gösterge için kullanılan RAM oranı. */
    data class Usage(val used: Long, val total: Long) {
        val fraction: Float get() = if (total > 0) used.toFloat() / total else 0f
    }

    private fun memoryInfo(context: Context): ActivityManager.MemoryInfo =
        ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }

    fun usage(context: Context): Usage {
        val info = memoryInfo(context)
        return Usage(info.totalMem - info.availMem, info.totalMem)
    }

    fun summary(context: Context): String {
        val usage = usage(context)
        return "${Format.bytes(usage.used)} / ${Format.bytes(usage.total)}"
    }

    /** /proc/meminfo satırlarını kB -> bayt olarak okur. */
    private fun procMeminfo(): Map<String, Long> =
        SysFs.read("/proc/meminfo").orEmpty().lineSequence().mapNotNull { line ->
            val key = line.substringBefore(':').trim()
            val kb = line.substringAfter(':', "").trim().substringBefore(' ').toLongOrNull()
            if (key.isEmpty() || kb == null) null else key to kb * 1024
        }.toMap()

    fun collect(context: Context): List<InfoGroup> {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = memoryInfo(context)
        val used = info.totalMem - info.availMem
        val usedFraction = if (info.totalMem > 0) used.toDouble() / info.totalMem else 0.0
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && info.advertisedMem > 0) {
            info.advertisedMem
        } else {
            Format.estimateInstalledRamBytes(info.totalMem)
        }
        val meminfo = procMeminfo()
        val swapTotal = meminfo["SwapTotal"]
        val swapFree = meminfo["SwapFree"]

        return infoGroups {
            group(context.getString(R.string.group_ram)) {
                item(context.getString(R.string.label_ram_installed), Format.bytes(installed))
                item(context.getString(R.string.label_ram_total), Format.bytes(info.totalMem))
                item(
                    context.getString(R.string.label_ram_used),
                    "${Format.bytes(used)} (${Format.percent(usedFraction)})",
                    progress = usedFraction.toFloat(),
                    alert = usedFraction >= 0.9,
                )
                item(context.getString(R.string.label_ram_available), Format.bytes(info.availMem))
                item(context.getString(R.string.label_ram_threshold), Format.bytes(info.threshold))
                item(context.getString(R.string.label_low_memory), context.yesNo(info.lowMemory))
                item(context.getString(R.string.label_low_ram_device), context.yesNo(am.isLowRamDevice))
            }
            if (swapTotal != null && swapTotal > 0 && swapFree != null) {
                group(context.getString(R.string.group_swap)) {
                    val swapUsed = swapTotal - swapFree
                    val fraction = swapUsed.toDouble() / swapTotal
                    item(context.getString(R.string.label_swap_total), Format.bytes(swapTotal))
                    item(
                        context.getString(R.string.label_swap_used),
                        "${Format.bytes(swapUsed)} (${Format.percent(fraction)})",
                        progress = fraction.toFloat(),
                    )
                }
            }
            group(context.getString(R.string.group_kernel_memory)) {
                meminfo["MemFree"]?.let { item(context.getString(R.string.label_mem_free), Format.bytes(it)) }
                meminfo["Cached"]?.let { item(context.getString(R.string.label_mem_cached), Format.bytes(it)) }
                meminfo["Buffers"]?.let { item(context.getString(R.string.label_mem_buffers), Format.bytes(it)) }
                meminfo["Shmem"]?.let { item(context.getString(R.string.label_mem_shared), Format.bytes(it)) }
            }
            group(context.getString(R.string.group_app_heap)) {
                item(context.getString(R.string.label_heap_standard), "${am.memoryClass} MB")
                item(context.getString(R.string.label_heap_large), "${am.largeMemoryClass} MB")
                item(context.getString(R.string.label_heap_max), Format.bytes(Runtime.getRuntime().maxMemory()))
            }
        }
    }
}
