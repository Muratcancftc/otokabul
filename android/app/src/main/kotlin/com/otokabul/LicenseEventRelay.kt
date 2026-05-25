package com.otokabul

import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.MethodChannel

/** Lisans iptali — native broadcast + Flutter MethodChannel. */
object LicenseEventRelay {

    const val ACTION_LICENSE_REVOKED = "com.otokabul.LICENSE_REVOKED"

    @Volatile
    var methodChannel: MethodChannel? = null

    fun notifyRevoked(context: Context) {
        val app = context.applicationContext
        val intent = Intent(ACTION_LICENSE_REVOKED).apply {
            setPackage(app.packageName)
        }
        app.sendBroadcast(intent)
        try {
            methodChannel?.invokeMethod("onLicenseRevoked", null)
        } catch (_: Exception) {
            // Flutter kapalı — açılışta checkLicense yakalar
        }
    }
}
