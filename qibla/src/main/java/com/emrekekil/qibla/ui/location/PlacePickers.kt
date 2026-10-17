package com.emrekekil.qibla.ui.location

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.emrekekil.qibla.R
import com.emrekekil.qibla.data.District
import com.emrekekil.qibla.data.PlaceTable
import com.emrekekil.qibla.ui.Labels
import com.emrekekil.qibla.ui.components.ListPage

/** 81 ilin listesi; her birinin yanında il merkezinin kıble açısı. */
@Composable
fun ProvinceListScreen(places: PlaceTable, onSelect: (Int) -> Unit) {
    ListPage(stringResource(R.string.pick_province)) { spec ->
        items(places.provinces, key = { it.plate }) { province ->
            Button(
                onClick = { onSelect(province.plate) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.filledTonalButtonColors(),
                label = { Text(province.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(stringResource(R.string.qibla_bearing_short, Labels.degrees(province.center.qiblaBearing)))
                },
            )
        }
    }
}

/** Bir ilin ilçeleri; il merkezi en üstte. */
@Composable
fun DistrictListScreen(places: PlaceTable, plate: Int, onSelect: (District) -> Unit) {
    val province = places.province(plate) ?: return
    val districts = listOf(province.center) + province.districts.filterNot { it == province.center }
    ListPage(province.name) { spec ->
        items(districts, key = { it.name }) { district ->
            Button(
                onClick = { onSelect(district) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = if (district.isProvinceCenter) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                label = { Text(district.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(stringResource(R.string.qibla_bearing_short, Labels.degrees(district.qiblaBearing, 1)))
                },
            )
        }
    }
}
