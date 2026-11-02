package com.emrekekil.qibla.ui.prayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.emrekekil.qibla.R
import com.emrekekil.qibla.data.PrayerState
import com.emrekekil.qibla.prayer.Prayer
import com.emrekekil.qibla.prayer.PrayerSchedule
import com.emrekekil.qibla.prayer.TimesSource
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Ana ekran: bir sonraki vakte kalan süre ortada, dış halkada şimdiki vaktin ne kadarının geçtiği,
 * altta günün altı vakti.
 *
 * @param active Sayfa görünür ve ekran açıkken true; geri sayım yalnızca o zaman saniyede bir
 *   güncellenir, aksi halde hiçbir zamanlayıcı çalışmaz.
 */
@Composable
fun PrayerPage(
    state: PrayerState?,
    active: Boolean,
    onDayChanged: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(active) {
        while (active) {
            now = LocalDateTime.now()
            // Bir sonraki tam saniyeye hizalanır; böylece saniye sayacı atlamaz.
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }

    ScreenScaffold {
        if (state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.prayer_loading), style = MaterialTheme.typography.bodySmall)
            }
            return@ScreenScaffold
        }
        val moment = remember(state, now.toLocalDate(), now.hour, now.minute) { PrayerSchedule.moment(state.days, now) }
        // Elimizdeki günlerin dışına çıkıldıysa (gece yarısı geçti) vakitler yeniden okunur.
        LaunchedEffect(moment == null, now.toLocalDate()) {
            if (moment == null || state.days.none { it.date == now.toLocalDate().plusDays(1) }) onDayChanged()
        }
        val names = stringArrayResource(R.array.prayer_names)
        val primary = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceContainerHigh

        Box(Modifier.fillMaxSize()) {
            // Dış halka: şimdiki vaktin geçen kısmı.
            if (moment != null) {
                val total = Duration.between(moment.currentStart, moment.nextStart).seconds.toFloat()
                val elapsed = Duration.between(moment.currentStart, now).seconds.toFloat()
                val fraction = (elapsed / total).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(primary, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 34.dp, bottom = 26.dp, start = 22.dp, end = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                val today = state.days.firstOrNull { it.date == now.toLocalDate() }
                val source = stringResource(
                    if (today?.source == TimesSource.DIYANET) R.string.prayer_source_diyanet else R.string.prayer_source_calculated,
                )
                Text(
                    text = "${locationLabel(state)} · $source",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isTemporary) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onOpenAlerts),
                ) {
                    if (moment != null) {
                        Text(
                            stringResource(R.string.prayer_until, names[moment.next.ordinal]),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            countdown(Duration.between(now, moment.nextStart)),
                            style = MaterialTheme.typography.numeralSmall,
                        )
                    }
                }

                TimesGrid(state, now, moment?.current, names)
            }
        }
    }
}

@Composable
private fun TimesGrid(state: PrayerState, now: LocalDateTime, current: Prayer?, names: Array<String>) {
    // Gece yarısından İmsak'a kadar hâlâ önceki günün Yatsı'sındayız; ama gösterilen gün bugündür.
    val today = state.days.firstOrNull { it.date == now.toLocalDate() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in Prayer.entries.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (prayer in row) {
                    val highlight = prayer == current
                    Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            names[prayer.ordinal],
                            style = MaterialTheme.typography.labelSmall,
                            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            today.times.getValue(prayer).format(clock),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun locationLabel(state: PrayerState): String {
    val name = state.active.displayName
    return if (state.isTemporary) stringResource(R.string.prayer_temporary, name) else name
}

/** "1:23:45" veya bir saatin altında "23:45". */
private fun countdown(duration: Duration): String {
    val total = duration.seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
