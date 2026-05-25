package com.otokabul

import android.content.Context
import com.otokabul.ForegroundService

/** Servis gerçekten çalışıyor mu? (prefs + foreground uyumu) */
object ServiceState {
    fun isActive(context: Context): Boolean {
        val ctx = context.applicationContext
        if (OtoKabulPrefs.isServiceRunning(ctx)) return true
        return ForegroundService.isActuallyRunning(ctx)
    }
}
