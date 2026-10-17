package com.emrekekil.qibla.sensor

import android.content.Context
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Surface

/**
 * Ekranın sensör çerçevesine göre dönüşünü izler.
 *
 * Saat sağ bilekte ve düğmeler solda takıldığında Wear OS ekranı 180° çevirir; sensör eksenleri ise
 * donanıma bağlı kalır. Bu yüzden saat 12 yönü değişir ve pusula hesabı yeniden eşlenmelidir.
 * Bazı cihazlar bunu Display.getRotation() ile, bazıları yalnızca "wear_wrist_orientation_mode"
 * ayarıyla bildirir; ikisine de bakılır. Kullanıcı ayrıca elle zorlayabilir.
 */
class DisplayRotationMonitor(
    private val context: Context,
    private val manualFlip: () -> Boolean?,
    private val onChanged: (Int) -> Unit,
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = onChanged(current())
    }
    private val settingObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = onChanged(current())
    }

    fun current(): Int {
        manualFlip()?.let { return if (it) Surface.ROTATION_180 else Surface.ROTATION_0 }
        val rotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        if (rotation != Surface.ROTATION_0) return rotation
        // Değerler: 0 sol bilek, 1 sol bilek 180°, 2 sağ bilek, 3 sağ bilek 180°.
        val mode = try {
            Settings.Global.getInt(context.contentResolver, WRIST_ORIENTATION_MODE, 0)
        } catch (_: Exception) {
            0
        }
        return if (mode == 1 || mode == 3) Surface.ROTATION_180 else Surface.ROTATION_0
    }

    fun start() {
        displayManager.registerDisplayListener(displayListener, handler)
        try {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(WRIST_ORIENTATION_MODE), false, settingObserver,
            )
        } catch (_: Exception) {
            // Ayar okunamıyorsa yalnızca Display.getRotation() kullanılır.
        }
        onChanged(current())
    }

    fun stop() {
        displayManager.unregisterDisplayListener(displayListener)
        context.contentResolver.unregisterContentObserver(settingObserver)
    }

    companion object {
        const val WRIST_ORIENTATION_MODE = "wear_wrist_orientation_mode"
    }
}
