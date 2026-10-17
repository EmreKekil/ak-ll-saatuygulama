package com.emrekekil.qibla.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class MagneticEnvironmentTest {

    // İstanbul 2026: F ≈ 48,1 µT, eğim ≈ 59°.
    private val env = MagneticEnvironment(expectedFieldMicroTesla = 48.1, expectedDip = 59.0)

    @Test
    fun classifiesFieldAndDipDeviations() {
        assertEquals(MagQuality.GOOD, env.classify(47.0, 61.0))
        assertEquals(MagQuality.SUSPECT, env.classify(57.0, 59.0)) // %18 fazla
        assertEquals(MagQuality.SUSPECT, env.classify(48.0, 67.0)) // eğim 8° farklı
        assertEquals(MagQuality.BAD, env.classify(70.0, 59.0)) // metal yakınında
        assertEquals(MagQuality.BAD, env.classify(48.0, 40.0))
    }

    @Test
    fun lowAccuracyRequestsCalibration() {
        val quality = env.add(MagSample(0L, 48.0, 59.0, accuracy = 1)) // SENSOR_STATUS_ACCURACY_LOW
        assertEquals(MagQuality.NEEDS_CALIBRATION, quality)
    }
}
