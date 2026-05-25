package com.otokabul

import android.content.Context

/** Flutter SharedPreferences — license_manager.dart ile aynı anahtarlar. */
object FlutterLicensePrefs {
    private const val PREFS_NAME = "FlutterSharedPreferences"
    private const val PREFIX = "flutter."

    const val KEY_CODE = "${PREFIX}license_code"
    const val KEY_DEVICE_ID = "${PREFIX}license_device_id"
    const val KEY_ACTIVATED_AT = "${PREFIX}license_activated_at"
    const val KEY_EXPIRES_AT = "${PREFIX}license_expires_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCode(context: Context): String? =
        prefs(context).getString(KEY_CODE, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun getDeviceId(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_CODE)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_ACTIVATED_AT)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }
}
