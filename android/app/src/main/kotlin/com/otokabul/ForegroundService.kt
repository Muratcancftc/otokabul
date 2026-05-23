package com.otokabul

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Uygulamayı arka planda canlı tutar (Samsung dahil).
 * startForeground onCreate'de; PARTIAL_WAKE_LOCK.
 * Arka planda çalışır; yalnızca DURDUR veya uygulama tamamen kapatılınca durur.
 */
class ForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "otokabul_foreground"
        const val ALERT_CHANNEL_ID = "otokabul_alert"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val SCREEN_OFF_NOTIFICATION_ID = 1003

        const val ACTION_STOP = "com.otokabul.STOP"
        const val ACTION_RECOVER = "com.otokabul.RECOVER"

        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val MIN_RECOVERY_INTERVAL_MS = 60_000L
        private const val PARTIAL_WAKE_LOCK_TAG = "OtoKabul:Partial"

        fun start(context: Context) {
            val intent = Intent(context, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        @Volatile
        var running: Boolean = false
            private set

        /** Statik bayrak + prefs + ActivityManager (Samsung ölümü sonrası). */
        fun isActuallyRunning(context: Context): Boolean {
            if (!OtoKabulPrefs.isServiceRunning(context)) return false
            if (running) return true
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (service.service.className == ForegroundService::class.java.name) {
                    return true
                }
            }
            return false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var stopRequested = false
    private var lastRecoveryAttemptAt = 0L
    private var isRecovering = false
    private var screenReceiverRegistered = false
    private var partialWakeLock: PowerManager.WakeLock? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenTurnedOff()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onScreenTurnedOn()
            }
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!OtoKabulPrefs.isServiceRunning(applicationContext) || stopRequested) return
            renewPartialWakeLock()
            ScreenWakeManager.renewIfNeeded(applicationContext)
            checkScreenState()
            checkAccessibilityAndRecover()
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Samsung: 5 sn içinde startForeground zorunlu — onCreate'de çağır
        // running/prefs yalnızca onStartCommand (BAŞLAT) ile set edilir
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            stopWatchdog()
            unregisterScreenReceiver()
            ScreenWakeManager.release(applicationContext)
            releasePartialWakeLock()
            cancelScreenOffNotification()
            OtoKabulPrefs.setServiceRunning(applicationContext, false)
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        stopRequested = false
        OtoKabulPrefs.setServiceRunning(applicationContext, true)
        running = true
        acquirePartialWakeLock()
        ScreenWakeManager.acquireKeepScreenOn(applicationContext)
        registerScreenReceiver()
        startWatchdog()
        checkScreenState()

        if (intent?.action == ACTION_RECOVER) {
            checkAccessibilityAndRecover()
        }

        return START_STICKY
    }

    /**
     * Görev listesinden silinse bile servis ayakta kalsın (BiTaksi'ye geçiş).
     * Durdurmak için uygulamada DURDUR kullanılır.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!OtoKabulPrefs.isServiceRunning(applicationContext)) return
        start(applicationContext)
    }

    override fun onDestroy() {
        running = false
        stopWatchdog()
        unregisterScreenReceiver()
        ScreenWakeManager.release(applicationContext)
        releasePartialWakeLock()
        cancelScreenOffNotification()
        if (stopRequested) {
            OtoKabulPrefs.setServiceRunning(applicationContext, false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquirePartialWakeLock() {
        if (partialWakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, PARTIAL_WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun renewPartialWakeLock() {
        if (partialWakeLock?.isHeld != true) acquirePartialWakeLock()
    }

    private fun releasePartialWakeLock() {
        partialWakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                } catch (_: RuntimeException) {
                }
            }
        }
        partialWakeLock = null
    }

    private fun startWatchdog() {
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        handler.removeCallbacks(watchdogRunnable)
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
        }
        screenReceiverRegistered = false
    }

    private fun checkScreenState() {
        if (!OtoKabulPrefs.isServiceRunning(applicationContext)) return
        if (ScreenWakeManager.isScreenInteractive(this)) {
            cancelScreenOffNotification()
            return
        }
        onScreenTurnedOff()
    }

    private fun onScreenTurnedOff() {
        if (!OtoKabulPrefs.isServiceRunning(applicationContext) || stopRequested) return
        showScreenOffNotification()
        ScreenWakeManager.pulseScreenOn(applicationContext)
        updateForegroundNotification(
            getString(R.string.notification_title),
            getString(R.string.notification_screen_off_short),
        )
    }

    private fun onScreenTurnedOn() {
        cancelScreenOffNotification()
        if (OtoKabulPrefs.isServiceRunning(applicationContext) && !stopRequested) {
            updateForegroundNotification(
                getString(R.string.notification_title),
                getString(R.string.notification_text),
            )
        }
    }

    private fun showScreenOffNotification() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_screen_off_title))
            .setContentText(getString(R.string.notification_screen_off))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java)
            ?.notify(SCREEN_OFF_NOTIFICATION_ID, notification)
    }

    private fun cancelScreenOffNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(SCREEN_OFF_NOTIFICATION_ID)
    }

    private fun checkAccessibilityAndRecover() {
        if (!OtoKabulPrefs.isServiceRunning(applicationContext)) return

        val enabledInSettings = AccessibilityMonitor.isEnabledInSettings(this)
        val connected = AccessibilityMonitor.isConnected()

        if (!enabledInSettings) {
            showAccessibilityLostNotification()
            sendAccessibilityBroadcast(AccessibilityMonitor.ACTION_ACCESSIBILITY_LOST)
            updateForegroundNotification(
                getString(R.string.notification_title),
                getString(R.string.notification_accessibility_off),
            )
            return
        }

        cancelAlertNotification()
        updateForegroundNotification(
            getString(R.string.notification_title),
            getString(R.string.notification_text),
        )

        if (connected) return

        val now = System.currentTimeMillis()
        if (isRecovering || now - lastRecoveryAttemptAt < MIN_RECOVERY_INTERVAL_MS) return

        isRecovering = true
        lastRecoveryAttemptAt = now
        updateForegroundNotification(
            getString(R.string.notification_title),
            getString(R.string.notification_recovering),
        )

        AccessibilityMonitor.restartAccessibilityService(this) { success ->
            isRecovering = false
            if (success) {
                sendAccessibilityBroadcast(AccessibilityMonitor.ACTION_ACCESSIBILITY_RECOVERED)
                updateForegroundNotification(
                    getString(R.string.notification_title),
                    getString(R.string.notification_text),
                )
            } else {
                updateForegroundNotification(
                    getString(R.string.notification_title),
                    getString(R.string.notification_recover_failed),
                )
            }
        }
    }

    private fun sendAccessibilityBroadcast(action: String) {
        sendBroadcast(Intent(action).apply { setPackage(packageName) })
    }

    private fun updateForegroundNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showAccessibilityLostNotification() {
        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_alert_title))
            .setContentText(getString(R.string.notification_accessibility_off))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun cancelAlertNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(ALERT_NOTIFICATION_ID)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val foreground = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }

        val alert = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_alert_channel_desc)
        }

        manager.createNotificationChannel(foreground)
        manager.createNotificationChannel(alert)
    }

    private fun buildNotification(
        title: String = getString(R.string.notification_title),
        text: String = getString(R.string.notification_text),
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
