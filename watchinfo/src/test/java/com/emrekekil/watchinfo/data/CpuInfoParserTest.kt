package com.emrekekil.watchinfo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuInfoParserTest {

    private val sample = """
        processor	: 0
        BogoMIPS	: 52.00
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics
        CPU implementer	: 0x41
        CPU architecture: 8
        CPU variant	: 0x2
        CPU part	: 0xd05
        CPU revision	: 0

        processor	: 1
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics
        CPU implementer	: 0x41
        CPU part	: 0xd41
        CPU revision	: 1

        processor	: 2
        CPU implementer	: 0x41
        CPU part	: 0xfff

        Hardware	: Samsung Exynos
    """.trimIndent()

    @Test
    fun parse_readsCoreNamesFeaturesAndHardware() {
        val info = CpuInfoParser.parse(sample)
        assertEquals("Cortex-A55", info.coreNames[0])
        assertEquals("Cortex-A78", info.coreNames[1])
        assertEquals("ARM 0xfff", info.coreNames[2])
        assertEquals("fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics", info.features)
        assertEquals("Samsung Exynos", info.hardware)
    }

    @Test
    fun coreName_handlesOtherVendors() {
        assertEquals("Qualcomm 0x805", CpuInfoParser.coreName("0x51", "0x805"))
        assertEquals(null, CpuInfoParser.coreName(null, "0xd05"))
    }

    @Test
    fun parseCpuRange_expandsRangesAndLists() {
        assertEquals(listOf(0, 1, 2, 3, 4), SysFs.parseCpuRange("0-4"))
        assertEquals(listOf(0, 2, 3), SysFs.parseCpuRange("0,2-3"))
        assertEquals(emptyList<Int>(), SysFs.parseCpuRange(null))
    }
}
