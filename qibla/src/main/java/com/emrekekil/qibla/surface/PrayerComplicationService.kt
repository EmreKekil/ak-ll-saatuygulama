package com.emrekekil.qibla.surface

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.emrekekil.qibla.MainActivity
import com.emrekekil.qibla.R
import com.emrekekil.qibla.prayer.Prayer
import com.emrekekil.qibla.prayer.PrayerWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Saat kadranı komplikasyonu: bir sonraki vakit ve ona kalan süre.
 *
 * Pil için: kalan süreyi kadranın kendisi sayar ([TimeDifferenceComplicationText]); her vakit aralığı
 * önceden bir zaman çizelgesi girişi olarak verilir ve sistem vakit değişince sıradakine kendisi
 * geçer. Periyodik güncelleme yoktur (manifestte UPDATE_PERIOD_SECONDS = 0).
 */
class PrayerComplicationService : SuspendingTimelineComplicationDataSourceService() {

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val data = withContext(Dispatchers.IO) { PrayerSurfaces.load(this@PrayerComplicationService) }
        val windows = data.windows
        if (windows.isEmpty()) return null
        val entries = windows.mapNotNull { window ->
            val content = build(request.complicationType, window, data.zone, data.location.displayName) ?: return@mapNotNull null
            TimelineEntry(
                TimeInterval(window.start.atZone(data.zone).toInstant(), window.end.atZone(data.zone).toInstant()),
                content,
            )
        }
        if (entries.isEmpty()) return null
        return ComplicationDataTimeline(entries.first().complicationData, entries)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val now = LocalDateTime.now()
        val preview = PrayerWindow(Prayer.OGLE, now.minusHours(1), Prayer.IKINDI, now.plusMinutes(83))
        return build(type, preview, ZoneId.systemDefault(), getString(R.string.complication_preview_place))
    }

    private fun build(type: ComplicationType, window: PrayerWindow, zone: ZoneId, place: String): ComplicationData? {
        val names = resources.getStringArray(R.array.prayer_names)
        val nextName = names[window.next.ordinal]
        val end = window.end.atZone(zone).toInstant()
        val countdown = TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_DUAL_UNIT, CountDownTimeReference(end))
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .build()
        val description = plain(getString(R.string.complication_description, nextName, window.end.format(clock)))
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_notification)).build()

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(countdown, description)
                    .setTitle(plain(nextName))
                    .setTapAction(openApp())
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_DUAL_UNIT, CountDownTimeReference(end))
                        .setText(getString(R.string.complication_long, nextName, window.end.format(clock)))
                        .setMinimumTimeUnit(TimeUnit.MINUTES)
                        .build(),
                    description,
                )
                    .setTitle(plain(place))
                    .setMonochromaticImage(icon)
                    .setTapAction(openApp())
                    .build()
            ComplicationType.RANGED_VALUE -> {
                // Şimdiki vaktin geçen kısmı (saniye). Android 13+ kadranlar değeri kendisi hesaplar.
                val total = Duration.between(window.start, window.end).seconds.toFloat()
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val remaining = DynamicInstant.platformTimeWithSecondsPrecision()
                        .durationUntil(DynamicInstant.withSecondsPrecision(end))
                        .toIntSeconds()
                        .asFloat()
                    RangedValueComplicationData.Builder(remaining.times(-1f).plus(total), 0f, 0f, total, description)
                } else {
                    RangedValueComplicationData.Builder(0f, 0f, total, description)
                }
                builder.setText(countdown)
                    .setTitle(plain(nextName))
                    .setMonochromaticImage(icon)
                    .setTapAction(openApp())
                    .build()
            }
            else -> null
        }
    }

    private fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
