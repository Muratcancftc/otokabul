package com.otokabul

import android.content.Context
import android.os.PowerManager

/**
 * Ekranı açık tutar; kapalıyken erişilebilirlik ekranı okuyamaz.
 */
object ScreenWakeManager {

    private const val WAKE_LOCK_TAG = "OtoKabul:ScreenWake"
    private const val PULSE_TAG = "OtoKabul:Pulse"
    private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L // 6 saat, watchdog yeniler

    private var screenWakeLock: PowerManager.WakeLock? = null

    /** Servis başlayınca ekranı açık tut (PowerManager). */
    @Suppress("DEPRECATION")
    fun acquireKeepScreenOn(context: Context) {
        release(context)
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        screenWakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG,
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    /** Süresi dolmuşsa yeniden al. */
    fun renewIfNeeded(context: Context) {
        val lock = screenWakeLock
        if (lock == null || !lock.isHeld) {
            acquireKeepScreenOn(context)
        }
    }

    fun release(context: Context) {
        screenWakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                } catch (_: RuntimeException) {
                    // Zaten serbest bırakılmış olabilir
                }
            }
        }
        screenWakeLock = null
    }

    fun isScreenInteractive(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    /** Ekran kapalıysa kısa süre uyandırmayı dener. */
    @Suppress("DEPRECATION")
    fun pulseScreenOn(context: Context) {
        if (isScreenInteractive(context)) return
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val pulse = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            PULSE_TAG,
        )
        pulse.acquire(4_000L)
    }
}
