package com.emrekekil.watchinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class FormatTest {

    private val tr = Locale.forLanguageTag("tr-TR")

    @Test
    fun bytes_usesBinaryUnitsByDefault() {
        assertEquals("512 B", Format.bytes(512, locale = tr))
        assertEquals("1,5 KB", Format.bytes(1536, locale = tr))
        assertEquals("1,9 GB", Format.bytes(2_000_000_000, locale = tr))
    }

    @Test
    fun bytes_usesDecimalUnitsForStorage() {
        assertEquals("64,0 GB", Format.bytes(64_000_000_000, si = true, locale = tr))
        assertEquals("128 GB", Format.bytes(128_000_000_000, si = true, locale = tr))
    }

    @Test
    fun frequency_switchesBetweenMhzAndGhz() {
        assertEquals("850 MHz", Format.frequencyKhz(850_000, tr))
        assertEquals("1,60 GHz", Format.frequencyKhz(1_600_000, tr))
    }

    @Test
    fun percent_followsLocaleConvention() {
        assertEquals("%85", Format.percent(0.85, tr))
        assertEquals("85%", Format.percent(0.85, Locale.US))
    }

    @Test
    fun current_detectsMicroAndMilliAmps() {
        assertEquals(-245.0, Format.currentToMilliAmps(-245_000)!!, 0.0)
        assertEquals(-245.0, Format.currentToMilliAmps(-245)!!, 0.0)
        assertNull(Format.currentToMilliAmps(Long.MIN_VALUE))
        assertNull(Format.currentToMilliAmps(Int.MIN_VALUE.toLong()))
    }

    @Test
    fun wearOsVersion_mapsKnownApiLevels() {
        assertEquals("3", Format.wearOsVersion(30))
        assertEquals("5.1", Format.wearOsVersion(35))
        assertEquals("6", Format.wearOsVersion(36))
        assertNull(Format.wearOsVersion(31))
    }

    @Test
    fun oneUiVersion_decodesSamsungProperty() {
        assertEquals("8.0", Format.oneUiVersion("80000"))
        assertEquals("6.1.1", Format.oneUiVersion("60101"))
        assertNull(Format.oneUiVersion(""))
        assertNull(Format.oneUiVersion(null))
    }

    @Test
    fun graphicsVersions_decode() {
        assertEquals("1.3.0", Format.vulkanVersion((1 shl 22) or (3 shl 12)))
        assertEquals("1.1.131", Format.vulkanVersion((1 shl 22) or (1 shl 12) or 131))
        assertEquals("3.2", Format.glEsVersion(0x00030002))
    }

    @Test
    fun installedRam_roundsUpToRetailSize() {
        val gib = 1024L * 1024 * 1024
        assertEquals(2 * gib, Format.estimateInstalledRamBytes(1_900_000_000))
        assertEquals(gib + gib / 2, Format.estimateInstalledRamBytes(1_450_000_000))
    }
}
