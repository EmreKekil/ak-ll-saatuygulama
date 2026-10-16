package com.emrekekil.watchinfo.data

import android.content.Context
import android.os.Build
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups

object CpuCollector {

    private const val CPU_DIR = "/sys/devices/system/cpu"

    private data class Core(
        val index: Int,
        val online: Boolean,
        val curKhz: Long?,
        val minKhz: Long?,
        val maxKhz: Long?,
        val name: String?,
    )

    private val cpuInfo: CpuInfoFile by lazy {
        CpuInfoParser.parse(SysFs.read("/proc/cpuinfo").orEmpty())
    }

    private fun cores(): List<Core> {
        val present = SysFs.parseCpuRange(SysFs.read("$CPU_DIR/present"))
            .ifEmpty { (0 until Runtime.getRuntime().availableProcessors()).toList() }
        val online = SysFs.parseCpuRange(SysFs.read("$CPU_DIR/online")).toSet()
        return present.map { i ->
            val freq = "$CPU_DIR/cpu$i/cpufreq"
            Core(
                index = i,
                online = online.isEmpty() || i in online,
                curKhz = SysFs.readLong("$freq/scaling_cur_freq"),
                minKhz = SysFs.readLong("$freq/cpuinfo_min_freq"),
                maxKhz = SysFs.readLong("$freq/cpuinfo_max_freq"),
                name = cpuInfo.coreNames[i],
            )
        }
    }

    private fun socName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val maker = Build.SOC_MANUFACTURER.takeUnless { it == Build.UNKNOWN }
        val model = Build.SOC_MODEL.takeUnless { it == Build.UNKNOWN }
        return listOfNotNull(maker, model).joinToString(" ").ifEmpty { null }
    }

    fun summary(context: Context): String {
        val cores = cores()
        val maxKhz = cores.mapNotNull { it.maxKhz }.maxOrNull()
        val count = context.resources.getQuantityString(R.plurals.core_count, cores.size, cores.size)
        return listOfNotNull(count, maxKhz?.let { Format.frequencyKhz(it) }).joinToString(" · ")
    }

    fun collect(context: Context): List<InfoGroup> {
        val cores = cores()
        // Aynı tür ve aynı azami frekanstaki çekirdekler bir küme (ör. big.LITTLE) oluşturur.
        val clusters = cores.groupBy { it.name to it.maxKhz }.values

        return infoGroups {
            group(context.getString(R.string.group_processor)) {
                item(context.getString(R.string.label_soc), socName())
                item(context.getString(R.string.label_platform), SysFs.getprop("ro.board.platform"))
                item(context.getString(R.string.label_hardware), cpuInfo.hardware ?: Build.HARDWARE)
                item(context.getString(R.string.label_architecture), System.getProperty("os.arch"))
                item(context.getString(R.string.label_abis), Build.SUPPORTED_ABIS.joinToString(", "))
                item(
                    context.getString(R.string.label_cores),
                    context.resources.getQuantityString(R.plurals.core_count, cores.size, cores.size),
                )
                item(
                    context.getString(R.string.label_clusters),
                    clusters.joinToString("\n") { members ->
                        val first = members.first()
                        listOfNotNull(
                            "${members.size}× ${first.name ?: context.getString(R.string.value_unknown_core)}",
                            first.maxKhz?.let { Format.frequencyKhz(it) },
                        ).joinToString(" · ")
                    },
                )
                item(
                    context.getString(R.string.label_governor),
                    SysFs.read("$CPU_DIR/cpu0/cpufreq/scaling_governor"),
                )
            }
            group(context.getString(R.string.group_core_clocks)) {
                cores.forEach { core ->
                    val label = listOfNotNull(
                        context.getString(R.string.label_core_n, core.index),
                        core.name,
                    ).joinToString(" · ")
                    val value = when {
                        !core.online -> context.getString(R.string.value_offline)
                        core.curKhz != null -> Format.frequencyKhz(core.curKhz)
                        else -> context.getString(R.string.value_restricted)
                    }
                    val progress = if (core.online && core.curKhz != null && core.maxKhz != null && core.maxKhz > 0) {
                        core.curKhz.toFloat() / core.maxKhz
                    } else {
                        null
                    }
                    item(label, value, progress)
                }
            }
            group(context.getString(R.string.group_frequency_range)) {
                clusters.forEach { members ->
                    val first = members.first()
                    if (first.minKhz != null && first.maxKhz != null) {
                        item(
                            listOfNotNull(coreRangeLabel(context, members.map { it.index }), first.name)
                                .joinToString(" · "),
                            "${Format.frequencyKhz(first.minKhz)} – ${Format.frequencyKhz(first.maxKhz)}",
                        )
                    }
                }
            }
            group(context.getString(R.string.group_instruction_set)) {
                item(context.getString(R.string.label_features), cpuInfo.features)
            }
        }
    }

    private fun coreRangeLabel(context: Context, indices: List<Int>): String = when {
        indices.size == 1 -> context.getString(R.string.label_core_n, indices.first())
        indices.last() - indices.first() == indices.size - 1 ->
            context.getString(R.string.label_core_range, indices.first(), indices.last())
        else -> indices.joinToString(", ") { context.getString(R.string.label_core_n, it) }
    }
}
