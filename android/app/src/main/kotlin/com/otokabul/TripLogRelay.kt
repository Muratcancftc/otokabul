package com.otokabul

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioThread = HandlerThread("OtoKabulLogIO").apply { start() }
    private val ioHandler = Handler(ioThread.looper)

    fun emit(
        context: Context,
        km: Double,
        accepted: Boolean,
        minKm: Double,
        reason: String,
        earningMin: Int? = null,
        earningMax: Int? = null,
        time: String = formatTime(),
    ) {
        val entry = TripLogStore.Entry(
            time = time,
            km = km,
            accepted = accepted,
            minKm = minKm,
            reason = reason,
            earningMin = earningMin,
            earningMax = earningMax,
        )
        val payload = mapOf(
            "time" to time,
            "km" to km,
            "accepted" to accepted,
            "minKm" to minKm,
            "reason" to reason,
            "earning_min" to earningMin,
            "earning_max" to earningMax,
        )
        ioHandler.post {
            TripLogStore.append(context.applicationContext, entry)
        }
        mainHandler.post {
            try {
                methodChannel?.invokeMethod("onLog", payload)
            } catch (_: Exception) {
                // Flutter kapalı — kayıt prefs'te kalır
            }
            when (reason) {
                REASON_CLICK_FAILED -> TripAlertNotifier.showClickFailed(context, km, minKm)
                REASON_NO_KM -> TripAlertNotifier.showNoKm(context)
                REASON_ACCEPTED -> AcceptSoundPlayer.playDing(context)
                REASON_SKIPPED -> AcceptSoundPlayer.playSkipTick(context)
            }
        }
    }

    fun formatTime(): String =
        SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())
}
