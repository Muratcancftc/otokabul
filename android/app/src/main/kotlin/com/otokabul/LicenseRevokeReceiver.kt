package com.otokabul

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** ForegroundService → Flutter (uygulama açıkken). */
class LicenseRevokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != LicenseEventRelay.ACTION_LICENSE_REVOKED) return
        try {
            LicenseEventRelay.methodChannel?.invokeMethod("onLicenseRevoked", null)
        } catch (_: Exception) {
        }
    }
}
