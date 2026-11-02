package com.emrekekil.qibla.surface

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.row
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeInterval
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import com.emrekekil.qibla.MainActivity
import com.emrekekil.qibla.R
import com.emrekekil.qibla.prayer.Prayer
import com.emrekekil.qibla.prayer.PrayerWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * "Ezan Vakti" Tile'ı: konum, bir sonraki vakit ve kalan süre, günün altı vakti.
 *
 * Pil için: her vakit aralığı ayrı bir zaman çizelgesi girişidir, sistem vakit değişince sıradakine
 * kendisi geçer; kalan süreyi Tile'ın dinamik ifadesi sayar. Uygulama yalnızca vakitler değişince
 * (günlük iş, indirme, konum değişimi) yeni çizelge üretir.
 */
class PrayerTileService : Material3TileService() {

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val data = withContext(Dispatchers.IO) { PrayerSurfaces.load(this@PrayerTileService) }
        val entries = data.windows.map { window ->
            val start = window.start.atZone(data.zone).toInstant().toEpochMilli()
            val end = window.end.atZone(data.zone).toInstant().toEpochMilli()
            timelineEntry(layout(window, data), timeInterval(start.milliseconds, end.milliseconds))
        }
        return tile(timeline = timeline(*entries.toTypedArray()), freshness = 24.hours)
    }

    private fun MaterialScope.layout(window: PrayerWindow, data: SurfaceData): LayoutElement {
        val names = resources.getStringArray(R.array.prayer_names)
        val place = if (data.isTemporary) getString(R.string.prayer_temporary, data.location.displayName) else data.location.displayName
        return primaryLayout(
            titleSlot = { text(LayoutString(place), typography = Typography.LABEL_SMALL, color = colorScheme.onSurfaceVariant) },
            mainSlot = {
                column(
                    text(
                        LayoutString(getString(R.string.prayer_until, names[window.next.ordinal])),
                        typography = Typography.LABEL_MEDIUM,
                        color = colorScheme.onSurfaceVariant,
                    ),
                    text(countdown(window, data.zone), typography = Typography.NUMERAL_SMALL, color = colorScheme.onSurface),
                    spacer(height = dp(4f)),
                    timesGrid(window, data, names),
                )
            },
            onClick = clickable(launchAction(ComponentName(this@PrayerTileService, MainActivity::class.java))),
        )
    }

    /** "1:23" biçiminde kalan süre; görüntüleyici desteklemiyorsa vaktin saati gösterilir. */
    private fun countdown(window: PrayerWindow, zone: ZoneId): LayoutString {
        val remaining = DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(window.end.atZone(zone).toInstant()))
        val twoDigits = DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()
        val dynamic = remaining.toIntHours().format()
            .concat(DynamicString.constant(":"))
            .concat(remaining.minutesPart.format(twoDigits))
        return LayoutString(
            window.end.format(clock),
            dynamic,
            StringLayoutConstraint.Builder("00:00").build(),
        )
    }

    private fun MaterialScope.timesGrid(window: PrayerWindow, data: SurfaceData, names: Array<String>): LayoutElement {
        val day = data.days.firstOrNull { it.date == window.start.toLocalDate() } ?: data.days.first()
        fun cell(prayer: Prayer): LayoutElement {
            val color = if (prayer == window.current) colorScheme.primary else colorScheme.onSurfaceVariant
            return column(
                text(LayoutString(names[prayer.ordinal]), typography = Typography.LABEL_SMALL, color = color),
                text(LayoutString(day.times.getValue(prayer).format(clock)), typography = Typography.BODY_SMALL, color = color),
                width = dp(46f),
            )
        }
        val rows = Prayer.entries.chunked(3).map { chunk -> row(*chunk.map { cell(it) }.toTypedArray()) }
        return column(*rows.toTypedArray())
    }
}
