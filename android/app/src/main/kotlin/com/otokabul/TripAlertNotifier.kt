package com.otokabul

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Locale

/** Tıklama başarısız veya kritik uyarı bildirimleri. */
object TripAlertNotifier {

    private const val CLICK_FAILED_ID = 1004
    private const val NO_KM_ID = 1005

    fun showNoKm(context: Context) {
        showSimpleAlert(
            context,
            NO_KM_ID,
            context.getString(R.string.notification_no_km_title),
            context.getString(R.string.notification_no_km_text),
        )
    }

    fun showClickFailed(context: Context, km: Double, minKm: Double) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: return
        val pending = PendingIntent.getActivity(
            context,
            2,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val kmStr = String.format(Locale("tr", "TR"), "%.2f", km).replace('.', ',')
        val notification = NotificationCompat.Builder(context, ForegroundService.ALERT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_click_failed_title))
            .setContentText(
                context.getString(
                    R.string.notification_click_failed_text,
                    kmStr,
                    minKm.toInt(),
                ),
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(CLICK_FAILED_ID, notification)
    }

    private fun showSimpleAlert(
        context: Context,
        id: Int,
        title: String,
        text: String,
    ) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: return
        val pending = PendingIntent.getActivity(
            context,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ForegroundService.ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }
}
