package com.otokabul

import android.content.Context
import io.flutter.plugin.common.MethodChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Teklif logları: kaydet, Flutter'a ilet, tıklama başarısızsa uyar.
 */
object TripLogRelay {

    const val REASON_ACCEPTED = "accepted"
    const val REASON_SKIPPED = "skipped"
    const val REASON_CLICK_FAILED = "click_failed"
    const val REASON_NO_KM = "no_km"

    @Volatile
    var methodChannel: MethodChannel? = null

    fun emit(
        context: Context,
        km: Double,
        accepted: Boolean,
        minKm: Double,
        reason: String,
        time: String = formatTime(),
    ) {
        TripLogStore.append(
            context,
            TripLogStore.Entry(time, km, accepted, minKm, reason),
        )
        val payload = mapOf(
            "time" to time,
            "km" to km,
            "accepted" to accepted,
            "minKm" to minKm,
            "reason" to reason,
        )
        try {
            methodChannel?.invokeMethod("onLog", payload)
        } catch (_: Exception) {
            // Flutter kapalı — kayıt prefs'te kalır
        }
        if (reason == REASON_CLICK_FAILED) {
            TripAlertNotifier.showClickFailed(context, km, minKm)
        }
    }

    fun formatTime(): String =
        SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())
}
