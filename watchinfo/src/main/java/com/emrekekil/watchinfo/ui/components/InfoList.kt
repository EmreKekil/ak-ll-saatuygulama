package com.emrekekil.watchinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
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
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup

/**
 * Başlık + gruplanmış bilgi kartlarından oluşan kaydırılabilir ekran.
 * Detay ve sensör ekranları bu iskeleti paylaşır.
 *
 * @param live true ise başlığın yanında verinin canlı güncellendiğini gösteren bir nokta çizilir.
 * @param extraContent Gruplardan önce eklenecek ek öğeler (ör. sensörün canlı değerleri).
 */
@Composable
fun InfoListScreen(
    title: String,
    state: LoadState<List<InfoGroup>>,
    live: Boolean,
    extraContent: (TransformingLazyColumnScope.(TransformationSpec) -> Unit)? = null,
) {
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
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, textAlign = TextAlign.Center)
                        if (live) {
                            Spacer(Modifier.size(6.dp))
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
            extraContent?.invoke(this, spec)
            when (state) {
                LoadState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                is LoadState.Failed -> item {
                    Text(
                        text = stringResource(R.string.error_load, state.error.message ?: state.error.javaClass.simpleName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                is LoadState.Ready -> state.value.forEachIndexed { groupIndex, group ->
                    group.title?.let { groupTitle ->
                        item(key = "g$groupIndex") {
                            ListSubHeader(
                                modifier = Modifier.transformedHeight(this, spec),
                                transformation = SurfaceTransformation(spec),
                            ) { Text(groupTitle) }
                        }
                    }
                    group.items.forEachIndexed { itemIndex, info ->
                        item(key = "g$groupIndex-$itemIndex") {
                            InfoRow(
                                item = info,
                                modifier = Modifier.transformedHeight(this, spec),
                                transformation = SurfaceTransformation(spec),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Liste içinde merkezlenmiş kısa açıklama metni. */
@Composable
fun ListNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
