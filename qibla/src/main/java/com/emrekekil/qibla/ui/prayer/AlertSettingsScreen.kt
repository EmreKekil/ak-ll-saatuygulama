package com.emrekekil.qibla.ui.prayer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.emrekekil.qibla.R
import com.emrekekil.qibla.prayer.AlertSetting
import com.emrekekil.qibla.prayer.Prayer
import com.emrekekil.qibla.prayer.PrayerSettings
import com.emrekekil.qibla.ui.components.ListPage
import com.emrekekil.qibla.ui.components.subHeader

/** "Çıkmadan önce" uyarısı için seçilebilen süreler; dokundukça sıradakine geçer. */
private val WARNING_STEPS = listOf(15, 30, 45, 60, 90, 120)

/** Her vakit için iki bildirim: vakit girişi ve vakit çıkmadan önce (süresi ayarlanabilir). */
@Composable
fun AlertSettingsScreen(alerts: Map<Prayer, AlertSetting>, onChange: (Prayer, AlertSetting) -> Unit) {
    val names = stringArrayResource(R.array.prayer_names)
    val res = LocalResources.current
    ListPage(stringResource(R.string.alerts_title)) { spec ->
        for (prayer in Prayer.entries) {
            val setting = alerts[prayer] ?: PrayerSettings.DEFAULTS.getValue(prayer)
            subHeader("h_$prayer", if (prayer == Prayer.IMSAK) "${names[0]} · ${res.getString(R.string.prayer_sabah)}" else names[prayer.ordinal], spec)
            item(key = "entry_$prayer") {
                SwitchButton(
                    checked = setting.onEntry,
                    onCheckedChange = { onChange(prayer, setting.copy(onEntry = it)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(res.getString(R.string.alert_entry)) },
                )
            }
            if (prayer in PrayerSettings.WARNABLE) {
                item(key = "warn_$prayer") {
                    // Kapalı → 15 → 30 → … → 120 → Kapalı.
                    FilledTonalButton(
                        onClick = {
                            val next = if (!setting.beforeEnd) {
                                setting.copy(beforeEnd = true, warningMinutes = WARNING_STEPS.first())
                            } else {
                                val i = WARNING_STEPS.indexOf(setting.warningMinutes)
                                if (i < 0 || i == WARNING_STEPS.lastIndex) setting.copy(beforeEnd = false)
                                else setting.copy(warningMinutes = WARNING_STEPS[i + 1])
                            }
                            onChange(prayer, next)
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        label = { Text(res.getString(R.string.alert_before_end)) },
                        secondaryLabel = {
                            Text(
                                if (setting.beforeEnd) res.getString(R.string.alert_minutes, setting.warningMinutes)
                                else res.getString(R.string.alert_off),
                            )
                        },
                    )
                }
            }
        }
    }
}
