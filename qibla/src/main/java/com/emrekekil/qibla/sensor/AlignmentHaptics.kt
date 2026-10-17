package com.emrekekil.qibla.sensor

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.emrekekil.qibla.geo.Angles
import kotlin.math.abs

/**
 * Kıbleye hizalanınca tek bir titreşim verir. Histerezis (3° girişte, 6° çıkışta) ve en az 1,5 sn
 * aralık sayesinde sınırda titreyip durmaz.
 */
class AlignmentHaptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val effect: VibrationEffect =
        if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) == true) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f).compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }

    var aligned = false
        private set
    private var lastBuzz = 0L

    /** Son titreşimin zamanı: titreşim motoru manyetometreyi kısa süre etkileyebilir. */
    var lastBuzzElapsed = 0L
        private set

    /** @param error Kıble ile saat 12 yönü arasındaki fark (derece). */
    fun update(error: Double) {
        val e = abs(Angles.signedDelta(0.0, error))
        val now = SystemClock.elapsedRealtime()
        if (!aligned && e <= ENTER_DEGREES) {
            aligned = true
            if (now - lastBuzz >= MIN_INTERVAL_MS && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(effect)
                lastBuzz = now
                lastBuzzElapsed = now
            }
        } else if (aligned && e >= EXIT_DEGREES) {
            aligned = false
        }
    }

    companion object {
        const val ENTER_DEGREES = 3.0
        const val EXIT_DEGREES = 6.0
        private const val MIN_INTERVAL_MS = 1_500L
    }
}
