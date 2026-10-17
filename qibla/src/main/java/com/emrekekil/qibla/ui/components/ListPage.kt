package com.emrekekil.qibla.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/** Başlıklı, kaydırılabilir Wear listesi. Ayrıntılar, konum ve il/ilçe ekranları bunu paylaşır. */
@Composable
fun ListPage(title: String, content: TransformingLazyColumnScope.(TransformationSpec) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumVerticalContentPadding(top = ListHeaderDefaults.minimumTopListContentPadding, bottom = 0.dp)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(title, textAlign = TextAlign.Center) }
            }
            content(spec)
        }
    }
}

fun TransformingLazyColumnScope.subHeader(key: String, title: String, spec: TransformationSpec) {
    item(key = key) {
        ListSubHeader(
            modifier = Modifier.transformedHeight(this, spec),
            transformation = SurfaceTransformation(spec),
        ) { Text(title) }
    }
}

/** Etiket + değer kartı; [note] verilirse altında küçük açıklama gösterilir. */
fun TransformingLazyColumnScope.infoItem(
    key: String,
    label: String,
    value: String?,
    spec: TransformationSpec,
    note: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    if (value.isNullOrBlank()) return
    item(key = key) { InfoCard(label, value, spec, note, valueColor) }
}

@Composable
fun TransformingLazyColumnItemScope.InfoCard(
    label: String,
    value: String,
    spec: TransformationSpec,
    note: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
        )
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Liste içinde ortalanmış kısa açıklama. */
fun TransformingLazyColumnScope.note(key: String, text: String) {
    item(key = key) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
