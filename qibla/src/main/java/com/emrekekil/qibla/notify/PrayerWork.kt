package com.emrekekil.qibla.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.emrekekil.qibla.data.LocationRepository
import com.emrekekil.qibla.prayer.PrayerEngine
import java.util.concurrent.TimeUnit

/**
 * Günde bir kez çalışan tek arka plan işi: son bilinen konuma bakar (GPS çalıştırmaz, ek izin
 * istemez), gerekirse geçici konuma geçer ve alarmı yeniden kurar. Veri azaldıysa indirmeyi, ağ
 * olduğunda çalışacak ayrı bir işe bırakır.
 */
class PrayerDailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = PrayerEngine(applicationContext)
        // Arka planda yalnızca sistemin son bilinen konumu okunur; bulunamazsa mevcut durum korunur.
        LocationRepository(applicationContext).bestKnown()?.let { engine.onPosition(it.location) }
        if (engine.needsRefresh()) PrayerWork.requestSync(applicationContext)
        PrayerAlarms.schedule(applicationContext)
        return Result.success()
    }
}

/** Ağ bağlantısı varken (telefon üzerinden Bluetooth ya da Wi-Fi) Diyanet verisini indirir. */
class PrayerSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = PrayerEngine(applicationContext)
        engine.refresh()
        PrayerAlarms.schedule(applicationContext)
        // İndirme başarısız olsa da yeniden deneme yok: vakitler hesapla gösterilir, ertesi gün tekrar denenir.
        return Result.success()
    }
}

object PrayerWork {
    private const val DAILY = "prayer_daily"
    private const val SYNC = "prayer_sync"

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PrayerDailyWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun requestSync(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PrayerSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}
