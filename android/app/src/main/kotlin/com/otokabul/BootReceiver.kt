package com.otokabul

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Telefon yeniden başlayınca, kullanıcı servisi açık bıraktıysa ön plan servisini başlatır.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!OtoKabulPrefs.isServiceRunning(context)) return
        ForegroundService.start(context)
    }
}
