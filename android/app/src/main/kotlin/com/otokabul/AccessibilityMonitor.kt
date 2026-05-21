package com.otokabul

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/**
 * Erişilebilirlik servisinin ayarlarda açık ve çalışır (bağlı) olup olmadığını izler.
 */
object AccessibilityMonitor {

    const val ACTION_ACCESSIBILITY_RECOVERED = "com.otokabul.ACCESSIBILITY_RECOVERED"
    const val ACTION_ACCESSIBILITY_LOST = "com.otokabul.ACCESSIBILITY_LOST"

    /** Ayarlarda OtoKabul erişilebilirlik servisi etkin mi? */
    fun isEnabledInSettings(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!am.isEnabled) return false
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        val expected =
            "${context.packageName}/${AutoAcceptAccessibilityService::class.java.canonicalName}"
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    /** Sistem servisi bağlandı mı? (onServiceConnected çalıştı mı) */
    fun isConnected(): Boolean = AutoAcceptAccessibilityService.instance != null

    /** Hem ayarlarda açık hem bağlı. */
    fun isHealthy(context: Context): Boolean =
        isEnabledInSettings(context) && isConnected()

    /**
     * Servis ölmüş ama ayarlarda hâlâ açıksa yeniden bağlanmayı dener.
     * Bileşeni kısa süre devre dışı bırakıp tekrar açar (aynı uygulama paketi).
     */
    fun restartAccessibilityService(context: Context, onDone: (Boolean) -> Unit) {
        if (!isEnabledInSettings(context)) {
            onDone(false)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        Thread {
            var ok = false
            try {
                val pm = context.packageManager
                val component = ComponentName(context, AutoAcceptAccessibilityService::class.java)
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
                Thread.sleep(400)
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                Thread.sleep(600)
                ok = isConnected()
            } catch (_: Exception) {
                ok = false
            }
            handler.post { onDone(ok) }
        }.start()
    }
}
