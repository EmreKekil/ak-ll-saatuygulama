package com.emrekekil.qibla.geo

import kotlin.math.abs
import kotlin.math.exp

/** Açı yardımcıları. Tüm açılar derece; pusula açıları kuzeyden saat yönünde ölçülür. */
object Angles {

    /** Açıyı [0, 360) aralığına getirir. */
    fun normalize(degrees: Double): Double {
        val r = degrees % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /**
     * [from] açısından [to] açısına en kısa dönüş: (-180, 180]. Pozitif = saat yönünde (sağa).
     * 359°→1° gibi sarmalama durumlarında doğru sonucu verir.
     */
    fun signedDelta(from: Double, to: Double): Double {
        val d = normalize(to - from)
        return if (d > 180.0) d - 360.0 else d
    }
}

/**
 * Açılar için zaman tabanlı üstel süzgeç. Her adımda en kısa işaretli fark kadar ilerlediği için
 * 359° ile 1° arasında gidip gelen bir değer 180°'ye "kaymaz". Kazanç olayların zaman damgasından
 * hesaplanır; böylece yumuşatma sensörün örnekleme hızından bağımsızdır.
 *
 * @param tauSeconds Zaman sabiti: büyük değer daha sakin ama daha gecikmeli.
 * @param fastTauSeconds Ani büyük dönüşlerde (> [jumpDegrees]) kullanılan kısa zaman sabiti.
 */
class CircularSmoother(
    private val tauSeconds: Double,
    private val fastTauSeconds: Double = 0.06,
    private val jumpDegrees: Double = 20.0,
) {
    private var value = 0.0
    private var lastNanos = 0L
    private var initialized = false

    fun update(degrees: Double, timestampNanos: Long): Double {
        if (!initialized) {
            value = Angles.normalize(degrees)
            lastNanos = timestampNanos
            initialized = true
            return value
        }
        val dt = ((timestampNanos - lastNanos) / 1e9).coerceIn(0.0, 1.0)
        lastNanos = timestampNanos
        val delta = Angles.signedDelta(value, degrees)
        val tau = if (abs(delta) > jumpDegrees) fastTauSeconds else tauSeconds
        val alpha = 1 - exp(-dt / tau)
        value = Angles.normalize(value + alpha * delta)
        return value
    }

    fun reset() {
        initialized = false
    }
}
