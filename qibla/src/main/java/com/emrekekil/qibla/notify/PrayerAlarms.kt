package com.emrekekil.qibla.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.emrekekil.qibla.MainActivity
import com.emrekekil.qibla.R
import com.emrekekil.qibla.prayer.PlannedAlert
import com.emrekekil.qibla.prayer.Prayer
import com.emrekekil.qibla.prayer.PrayerEngine
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Ezan vakti bildirimleri. Pil için aynı anda yalnızca bir alarm kuruludur: alarm çalınca bildirim
 * gösterilir ve sıradaki kurulur. Günde en fazla ~10 uyanma olur; arada uygulama hiç çalışmaz.
 */
object PrayerAlarms {

    private const val CHANNEL = "prayer_times"
    private const val EXTRA_PRAYER = "prayer"
    private const val EXTRA_KIND = "kind"
    private const val EXTRA_ENDS = "ends"
    private const val EXTRA_TIME = "time"
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** Bir sonraki bildirimi kurar (öncekinin yerine geçer). Ana iş parçacığında çağrılmamalıdır. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java)
        val alert = PrayerEngine(app).nextAlert()
        val intent = PendingIntent.getBroadcast(
            app, 0,
            Intent(app, PrayerAlarmReceiver::class.java).apply {
                alert?.let {
                    putExtra(EXTRA_PRAYER, it.prayer.name)
                    putExtra(EXTRA_KIND, it.kind.name)
                    putExtra(EXTRA_TIME, it.time.toString())
                    it.endsAt?.let { end -> putExtra(EXTRA_ENDS, end.toString()) }
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (alert == null) {
            manager.cancel(intent)
            return
        }
        val at = alert.time.atZone(PrayerEngine.zone()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            // Tam zamanlı alarm izni yoksa sistemin izin verdiği en yakın zamanda.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    internal fun show(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let(Prayer::valueOf) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let(PlannedAlert.Kind::valueOf) ?: return
        val time = intent.getStringExtra(EXTRA_TIME)?.let(LocalDateTime::parse) ?: return
        val ends = intent.getStringExtra(EXTRA_ENDS)?.let(LocalDateTime::parse)
        // Alarm geç teslim edildiyse (cihaz kapalıydı vb.) eski bildirimi gösterme.
        if (LocalDateTime.now(PrayerEngine.zone()).isAfter(time.plusMinutes(10))) return

        val res = context.resources
        val names = res.getStringArray(R.array.prayer_names)
        val (title, text) = when (kind) {
            PlannedAlert.Kind.ENTRY ->
                res.getString(R.string.notif_entry_title, names[prayer.ordinal]) to
                    res.getString(R.string.notif_entry_text, time.format(clock))
            PlannedAlert.Kind.BEFORE_END -> {
                val minutes = Duration.between(time, ends ?: time).toMinutes().toInt()
                val name = if (prayer == Prayer.IMSAK) res.getString(R.string.prayer_sabah) else names[prayer.ordinal]
                val nextName = names[(prayer.ordinal + 1) % names.size]
                res.getString(R.string.notif_warn_title, name, minutes) to
                    res.getString(R.string.notif_warn_text, nextName, (ends ?: time).format(clock))
            }
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(prayer.ordinal * 2 + kind.ordinal, notification)
        } catch (_: SecurityException) {
            // Bildirim izni verilmemiş.
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }
}

/** Alarm çaldığında bildirimi gösterir ve sıradakini kurar. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                PrayerAlarms.show(context, intent)
                PrayerAlarms.schedule(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Yeniden başlatma, saat/saat dilimi değişikliği ve güncellemeden sonra alarmı yeniden kurar. */
class AlarmResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        Thread {
            try {
                PrayerAlarms.schedule(context)
                PrayerWork.ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
