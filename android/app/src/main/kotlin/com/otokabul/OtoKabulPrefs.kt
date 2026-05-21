package com.otokabul

import android.content.Context

/**
 * Flutter ve Kotlin arasında paylaşılan yerel ayarlar.
 * Sunucu/proxy yok; tüm veriler telefonda kalır.
 */
object OtoKabulPrefs {
    const val PREFS_NAME = "otokabul_prefs"
    const val KEY_MIN_KM = "min_km"
    const val KEY_SERVICE_RUNNING = "service_running"
    const val DEFAULT_MIN_KM = 5.0

    fun getMinKm(context: Context): Double {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_MIN_KM, DEFAULT_MIN_KM.toFloat()).toDouble()
    }

    fun setMinKm(context: Context, km: Double) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_MIN_KM, km.toFloat())
            .apply()
    }

    fun isServiceRunning(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_RUNNING, false)
    }

    fun setServiceRunning(context: Context, running: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply()
    }
}
