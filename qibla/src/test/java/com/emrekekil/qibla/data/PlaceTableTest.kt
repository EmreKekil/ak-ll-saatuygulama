package com.emrekekil.qibla.data

import com.emrekekil.qibla.geo.LatLon
import com.emrekekil.qibla.geo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTableTest {

    private val table = PlaceTable.parse(TestData.asset("tr_districts.csv"))

    @Test
    fun containsAllProvincesAndDistricts() {
        assertEquals(81, table.provinces.size)
        assertEquals(973, table.districts.size)
        assertEquals((1..81).toSet(), table.provinces.map { it.plate }.toSet())
        assertTrue(table.provinces.all { p -> p.districts.count { it.isProvinceCenter } == 1 })
        assertEquals(table.districts.size, table.districts.map { it.plate to it.name }.toSet().size)
    }

    @Test
    fun coordinatesAreInsideTurkey() {
        for (d in table.districts) {
            assertTrue(d.displayName, d.location.latitude in 35.8..42.2 && d.location.longitude in 25.6..44.9)
        }
    }

    @Test
    fun turkishNamesAndCentersAreCorrect() {
        assertEquals("İstanbul", table.province(34)!!.name)
        assertEquals("Şanlıurfa", table.province(63)!!.name)
        assertEquals("Merkez", table.province(4)!!.center.name) // Ağrı: büyükşehir olmayan il
        assertEquals("İpekyolu", table.province(65)!!.center.name) // Van: büyükşehir, "Merkez" yok
        // Türkçe alfabetik sıralama: Ç, C'den sonra; İ, I'dan sonra gelir.
        val names = table.provinces.map { it.name }
        assertTrue(names.indexOf("Çanakkale") in (names.indexOf("Bursa") + 1) until names.indexOf("Denizli"))
        assertTrue(names.indexOf("İstanbul") > names.indexOf("Iğdır"))
    }

    @Test
    fun nearestFindsDistrictSeat() {
        val (district, km) = table.nearest(LatLon(38.4985, 43.3730))!! // Van şehir merkezi
        assertEquals("Van", district.province)
        assertTrue("$km km", km < 10)
    }
}
