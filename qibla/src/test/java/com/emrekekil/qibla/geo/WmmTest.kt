package com.emrekekil.qibla.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WmmTest {

    private val model = WorldMagneticModel.parse(TestData.asset("WMM.COF"))

    @Test
    fun parsesHeader() {
        assertEquals(2025.0, model.epoch, 0.0)
        assertEquals(2030.0, model.validUntil, 0.0)
    }

    /** NOAA'nın WMM2025 ile yayımladığı 112 resmi test noktası (D, I 0,01° yuvarlanmış). */
    @Test
    fun reproducesOfficialTestValues() {
        val rows = TestData.csv(TestData.resource("wmm2025_official.csv"))
        assertEquals(112, rows.size)
        for (row in rows) {
            val field = model.calculate(
                latitude = row.getValue("lat").toDouble(),
                longitude = row.getValue("lon").toDouble(),
                altitudeMeters = row.getValue("alt_km").toDouble() * 1000,
                decimalYear = row.getValue("decimal_year").toDouble(),
            )
            val where = "${row["lat"]},${row["lon"]},${row["alt_km"]}km,${row["decimal_year"]}"
            assertEquals("D $where", row.getValue("D_deg").toDouble(), field.declination, 0.0051)
            assertEquals("I $where", row.getValue("I_deg").toDouble(), field.inclination, 0.0051)
            assertEquals("F $where", row.getValue("F_nT").toDouble(), field.total, 0.11)
        }
    }

    @Test
    fun matchesReferenceValuesForTurkey() {
        val rows = TestData.csv(TestData.resource("wmm2025_turkey.csv"))
        assertTrue(rows.size >= 20)
        for (row in rows) {
            val field = model.calculate(
                latitude = row.getValue("lat").toDouble(),
                longitude = row.getValue("lon").toDouble(),
                altitudeMeters = 0.0,
                decimalYear = row.getValue("decimal_year").toDouble(),
            )
            val city = row.getValue("city")
            assertEquals("D $city", row.getValue("D_wmm2025_deg").toDouble(), field.declination, 0.001)
            assertEquals("I $city", row.getValue("I_wmm2025_deg").toDouble(), field.inclination, 0.001)
            assertEquals("F $city", row.getValue("F_wmm2025_nT").toDouble(), field.total, 0.1)
        }
    }
}

class DecimalYearTest {
    @Test
    fun convertsUtcTimestamps() {
        assertEquals(2026.0, decimalYear(java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()), 1e-9)
        // 2026 artık yıl değil: 1 Temmuz 00:00 = 181 gün / 365.
        assertEquals(2026 + 181.0 / 365, decimalYear(java.time.Instant.parse("2026-07-01T00:00:00Z").toEpochMilli()), 1e-9)
    }
}
