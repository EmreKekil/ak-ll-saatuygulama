package com.emrekekil.watchinfo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text

/** Ortasında değer, altında etiket bulunan küçük halka gösterge. */
@Composable
fun RingGauge(
    fraction: Float,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.size(size),
                colors = ProgressIndicatorDefaults.colors(indicatorColor = color),
                strokeWidth = 5.dp,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
