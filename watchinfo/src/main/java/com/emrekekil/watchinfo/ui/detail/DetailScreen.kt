package com.emrekekil.watchinfo.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.emrekekil.watchinfo.data.DeviceInfoRepository
import com.emrekekil.watchinfo.model.Category
import com.emrekekil.watchinfo.ui.components.InfoListScreen
import com.emrekekil.watchinfo.ui.components.rememberPolled

@Composable
fun DetailScreen(repository: DeviceInfoRepository, category: Category) {
    val state by rememberPolled(category, category.refreshMillis) { repository.collect(category) }
    InfoListScreen(
        title = stringResource(category.title),
        state = state,
        live = category.refreshMillis != null,
    )
}
