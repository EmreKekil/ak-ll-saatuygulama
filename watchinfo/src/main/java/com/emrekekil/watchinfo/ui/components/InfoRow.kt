package com.emrekekil.watchinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import com.emrekekil.watchinfo.model.InfoItem

/** Etiket + değer kartı. İlerleme verilmişse altında ince bir doluluk çubuğu gösterir. */
@Composable
fun InfoRow(
    item: InfoItem,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = item.value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.alert) colors.error else colors.onSurface,
        )
        item.progress?.let { progress ->
            UsageBar(
                fraction = progress,
                color = if (item.alert) colors.error else colors.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun UsageBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(color),
        )
    }
}
