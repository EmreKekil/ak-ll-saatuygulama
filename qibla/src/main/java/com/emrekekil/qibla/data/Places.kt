package com.emrekekil.qibla.data

import com.emrekekil.qibla.geo.LatLon
import com.emrekekil.qibla.geo.Qibla
import java.text.Collator
import java.util.Locale

/** Bir ilçe merkezi ve ona ait önceden hesaplanmış kıble açısı. */
data class District(
    val plate: Int,
    val province: String,
    val name: String,
    val location: LatLon,
    val isProvinceCenter: Boolean,
) {
    /** Gerçek kuzeye göre kıble açısı (derece). Uygulamaya gömülü koordinattan hesaplanır. */
    val qiblaBearing: Double = Qibla.bearing(location)

    /** Ekranda gösterilecek ad: il merkezi için yalnızca il, diğerleri için "İl · İlçe". */
    val displayName: String get() = if (isProvinceCenter) province else "$province · $name"
}

data class Province(val plate: Int, val name: String, val center: District, val districts: List<District>)

/** Uygulamaya gömülü 81 il ve tüm ilçelerin tablosu. */
class PlaceTable(val districts: List<District>) {

    private val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))

    val provinces: List<Province> = districts
        .groupBy { it.plate }
        .map { (plate, list) ->
            val sorted = list.sortedWith(compareBy(collator) { it.name })
            Province(
                plate = plate,
                name = list.first().province,
                center = list.firstOrNull { it.isProvinceCenter } ?: sorted.first(),
                districts = sorted,
            )
        }
        .sortedWith(compareBy(collator) { it.name })

    fun province(plate: Int): Province? = provinces.firstOrNull { it.plate == plate }

    fun find(plate: Int, name: String): District? =
        districts.firstOrNull { it.plate == plate && it.name == name }

    /** Verilen noktaya en yakın ilçe merkezi ve uzaklığı (km). */
    fun nearest(point: LatLon): Pair<District, Double>? =
        districts.asSequence()
            .map { it to Qibla.distanceKm(point, it.location) }
            .minByOrNull { it.second }

    companion object {
        /**
         * "plate,province,district,lat,lon,is_province_center,source" başlıklı CSV'yi okur.
         * Adlarda virgül bulunmadığından basit bölme yeterlidir.
         */
        fun parse(csv: String): PlaceTable {
            val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val header = lines.first().split(',').map { it.trim() }
            fun column(name: String) = header.indexOf(name).also { require(it >= 0) { "CSV sütunu yok: $name" } }
            val plate = column("plate")
            val province = column("province")
            val district = column("district")
            val lat = column("lat")
            val lon = column("lon")
            val center = column("is_province_center")
            val districts = lines.drop(1).map { line ->
                val cells = line.split(',')
                District(
                    plate = cells[plate].trim().toInt(),
                    province = cells[province].trim(),
                    name = cells[district].trim(),
                    location = LatLon(cells[lat].trim().toDouble(), cells[lon].trim().toDouble()),
                    isProvinceCenter = cells[center].trim().let { it == "1" || it.equals("true", ignoreCase = true) },
                )
            }
            return PlaceTable(districts)
        }
    }
}
