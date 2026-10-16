package com.emrekekil.watchinfo.data

/** /proc/cpuinfo içeriğinin ihtiyaç duyulan kısmı. */
data class CpuInfoFile(
    val hardware: String?,
    val features: String?,
    /** İşlemci indeksi -> çekirdek adı (ör. "Cortex-A55"). */
    val coreNames: Map<Int, String>,
)

object CpuInfoParser {

    fun parse(text: String): CpuInfoFile {
        var hardware: String? = null
        var features: String? = null
        val coreNames = mutableMapOf<Int, String>()
        var processor: Int? = null
        var implementer: String? = null
        var part: String? = null

        fun flush() {
            val index = processor ?: return
            coreName(implementer, part)?.let { coreNames[index] = it }
        }

        text.lineSequence().forEach { line ->
            val key = line.substringBefore(':').trim()
            val value = line.substringAfter(':', "").trim()
            when (key) {
                "processor" -> {
                    flush()
                    processor = value.toIntOrNull()
                    implementer = null
                    part = null
                }
                "CPU implementer" -> implementer = value
                "CPU part" -> part = value
                "Hardware" -> hardware = value.ifEmpty { null }
                "Features" -> if (features == null) features = value.ifEmpty { null }
            }
        }
        flush()
        return CpuInfoFile(hardware, features, coreNames)
    }

    /** ARM "CPU implementer" ve "CPU part" kodlarını çekirdek adına çevirir. */
    fun coreName(implementer: String?, part: String?): String? {
        val impl = implementer?.removePrefix("0x")?.toIntOrNull(16) ?: return null
        val partCode = part?.removePrefix("0x")?.toIntOrNull(16) ?: return null
        if (impl != 0x41) {
            val vendor = vendors[impl] ?: "0x%02x".format(impl)
            return "$vendor 0x%03x".format(partCode)
        }
        return armParts[partCode] ?: "ARM 0x%03x".format(partCode)
    }

    private val vendors = mapOf(0x51 to "Qualcomm", 0x53 to "Samsung", 0x48 to "HiSilicon", 0x4e to "NVIDIA")

    private val armParts = mapOf(
        0xd03 to "Cortex-A53",
        0xd04 to "Cortex-A35",
        0xd05 to "Cortex-A55",
        0xd07 to "Cortex-A57",
        0xd08 to "Cortex-A72",
        0xd09 to "Cortex-A73",
        0xd0a to "Cortex-A75",
        0xd0b to "Cortex-A76",
        0xd0d to "Cortex-A77",
        0xd41 to "Cortex-A78",
        0xd44 to "Cortex-X1",
        0xd46 to "Cortex-A510",
        0xd47 to "Cortex-A710",
        0xd48 to "Cortex-X2",
        0xd4d to "Cortex-A715",
        0xd4e to "Cortex-X3",
        0xd80 to "Cortex-A520",
        0xd81 to "Cortex-A720",
        0xd82 to "Cortex-X4",
        0xd85 to "Cortex-X925",
        0xd87 to "Cortex-A725",
    )
}
