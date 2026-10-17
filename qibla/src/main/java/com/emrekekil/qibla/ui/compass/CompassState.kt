package com.emrekekil.qibla.ui.compass

import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.emrekekil.qibla.geo.Angles
import com.emrekekil.qibla.geo.CircularSmoother
import com.emrekekil.qibla.geo.MagneticField
import com.emrekekil.qibla.sensor.AlignmentHaptics
import com.emrekekil.qibla.sensor.CompassSensors
import com.emrekekil.qibla.sensor.DisplayRotationMonitor
import com.emrekekil.qibla.sensor.HeadingSource
import com.emrekekil.qibla.sensor.MagQuality
import com.emrekekil.qibla.sensor.MagneticEnvironment
import kotlin.math.abs

/**
 * Pusulanın anlık durumu. [magneticHeading] saniyede 50 kez değişir; yalnızca çizim aşamasında
 * okunmalıdır (aksi halde her örnekte yeniden oluşturma yapılır).
 */
@Stable
class CompassState {
    /** Yumuşatılmış manyetik yön (derece); henüz veri yoksa veya güvenilmezse NaN. */
    var magneticHeading by mutableDoubleStateOf(Double.NaN)
        internal set

    /** Saat çok eğik tutuluyor (yön hesabı tanımsızlaşıyor). */
    var tooSteep by mutableStateOf(false)
        internal set

    /** Saat düz tutulmuyor; hafif uyarı. */
    var tilted by mutableStateOf(false)
        internal set

    var quality by mutableStateOf(MagQuality.UNKNOWN)
        internal set

    /** Manyetometrenin SENSOR_STATUS_* değeri; bilinmiyorsa -1. */
    var magAccuracy by mutableIntStateOf(-1)
        internal set

    var measuredField by mutableDoubleStateOf(Double.NaN)
        internal set

    var measuredDip by mutableDoubleStateOf(Double.NaN)
        internal set

    var headingAccuracy by mutableStateOf<Double?>(null)
        internal set

    var source by mutableStateOf<HeadingSource?>(null)
        internal set

    var screenRotation by mutableIntStateOf(Surface.ROTATION_0)
        internal set

    /** Cihazda pusula için gereken sensörler var mı? */
    var available by mutableStateOf(true)
        internal set

    /** Kıbleye hizalı mı (histerezisli). */
    var aligned by mutableStateOf(false)
        internal set
}

/**
 * Pusula sensörlerini ekran görünür ve etkinken çalıştırır.
 *
 * @param active false iken (ör. başka sayfadayken veya ekran ortam modundayken) sensörler kapalıdır.
 * @param expectedField Konumdaki beklenen manyetik alan; parazit tespiti için.
 * @param alignTarget Hizalanma titreşimi için kıblenin manyetik açısı; null ise titreşim yok.
 */
@Composable
fun rememberCompassState(
    active: Boolean,
    expectedField: MagneticField?,
    flipOverride: Boolean?,
    alignTarget: Double?,
): CompassState {
    val context = LocalContext.current
    val state = remember { CompassState() }
    val target by rememberUpdatedState(alignTarget)
    val flip by rememberUpdatedState(flipOverride)

    LifecycleResumeEffect(active, expectedField) {
        if (!active) {
            state.magneticHeading = Double.NaN
            return@LifecycleResumeEffect onPauseOrDispose { }
        }
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val smoother = CircularSmoother(tauSeconds = 0.2)
        val haptics = AlignmentHaptics(context)
        val environment = expectedField?.let { MagneticEnvironment(it.total / 1000.0, it.inclination) }
        val sensors = CompassSensors(
            sensorManager = sensorManager,
            onHeading = { sample ->
                val steep = abs(sample.topElevation) > 65 || sample.screenTilt > 75
                state.tooSteep = steep
                state.tilted = sample.screenTilt > 35
                state.headingAccuracy = sample.headingAccuracy
                if (steep) {
                    smoother.reset()
                    state.magneticHeading = Double.NaN
                } else {
                    val heading = smoother.update(sample.magneticAzimuth, sample.timestampNanos)
                    state.magneticHeading = heading
                    target?.let { t ->
                        haptics.update(Angles.signedDelta(heading, t))
                        if (state.aligned != haptics.aligned) state.aligned = haptics.aligned
                    }
                }
            },
            onMag = { sample ->
                state.magAccuracy = sample.accuracy
                // Titreşim motoru manyetometreyi kısa süre etkileyebilir; o anki örnekler atlanır.
                if (environment != null && SystemClock.elapsedRealtime() - haptics.lastBuzzElapsed > 300) {
                    val quality = environment.add(sample)
                    if (state.quality != quality) state.quality = quality
                    state.measuredField = environment.medianField
                    state.measuredDip = environment.medianDip
                } else if (environment == null) {
                    state.measuredField = sample.fieldMicroTesla
                    val quality = if (sample.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                        MagQuality.NEEDS_CALIBRATION
                    } else {
                        MagQuality.UNKNOWN
                    }
                    if (state.quality != quality) state.quality = quality
                }
            },
        )
        state.source = sensors.source
        val rotation = DisplayRotationMonitor(context, { flip }) { r ->
            sensors.uiRotation = r
            state.screenRotation = r
        }
        rotation.start()
        state.available = sensors.start()
        onPauseOrDispose {
            sensors.stop()
            rotation.stop()
            state.magneticHeading = Double.NaN
            state.aligned = false
        }
    }
    return state
}

/**
 * Ekran tam açık mı? Wear OS 6'da uygulama ortam (ambient) modunda da "devam ediyor" durumunda
 * kalabildiği için, sensörleri yalnızca yaşam döngüsüne göre durdurmak yetmez.
 */
@Composable
fun rememberDisplayOn(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val manager = context.getSystemService(DisplayManager::class.java)
        fun read() {
            val display = manager.getDisplay(Display.DEFAULT_DISPLAY)
            on = display == null || display.state == Display.STATE_ON || display.state == Display.STATE_UNKNOWN
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) = read()
        }
        read()
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return on
}
