package com.otokabul

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        const val CHANNEL = "com.otokabul/service"
        const val TEST_CHANNEL = "com.otokabul/test"
    }

    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        TripLogRelay.methodChannel = methodChannel
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> {
                    OtoKabulPrefs.setServiceRunning(applicationContext, true)
                    ForegroundService.start(applicationContext)
                    result.success(true)
                }
                "stop" -> {
                    OtoKabulPrefs.setServiceRunning(applicationContext, false)
                    ForegroundService.stop(applicationContext)
                    result.success(true)
                }
                "setMinKm" -> {
                    val km = call.argument<Double>("km")
                    if (km != null) {
                        OtoKabulPrefs.setMinKm(applicationContext, km)
                        result.success(true)
                    } else {
                        result.error("INVALID", "km gerekli", null)
                    }
                }
                "getMinKm" -> {
                    result.success(OtoKabulPrefs.getMinKm(applicationContext))
                }
                "isServiceRunning" -> {
                    result.success(ForegroundService.isActuallyRunning(applicationContext))
                }
                "isIgnoringBatteryOptimizations" -> {
                    result.success(isIgnoringBatteryOptimizations())
                }
                "requestIgnoreBatteryOptimizations" -> {
                    requestIgnoreBatteryOptimizations()
                    result.success(true)
                }
                "isAccessibilityEnabled" -> {
                    result.success(AccessibilityMonitor.isHealthy(applicationContext))
                }
                "getRecentLogs" -> {
                    val entries = TripLogStore.loadAll(applicationContext)
                    result.success(TripLogStore.toMaps(entries))
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, TEST_CHANNEL)
            .setMethodCallHandler { call, result ->
                val ctx = applicationContext
                try {
                    when (call.method) {
                        "testAccessibility" ->
                            result.success(SelfTestNative.testAccessibility(ctx))
                        "testNotification" ->
                            result.success(SelfTestNative.testNotification(ctx))
                        "testForegroundService" ->
                            result.success(SelfTestNative.testForegroundService(ctx))
                        "testSharedPreferences" ->
                            result.success(SelfTestNative.testSharedPreferences(ctx))
                        "testWakeLock" ->
                            result.success(SelfTestNative.testWakeLockPermission(ctx))
                        "testBitaksiInstalled" ->
                            result.success(SelfTestNative.testBitaksiInstalled(ctx))
                        "testKmParse" ->
                            result.success(SelfTestNative.testKmParse(ctx))
                        "testMinKmComparison" ->
                            result.success(SelfTestNative.testMinKmComparison(ctx))
                        "testRandomDelay" ->
                            result.success(SelfTestNative.testRandomDelay())
                        "testDoubleTapProtection" ->
                            result.success(SelfTestNative.testDoubleTapProtection())
                        else -> result.notImplemented()
                    }
                } catch (e: Exception) {
                    result.error("TEST_ERROR", e.message, null)
                }
            }
    }

    override fun onResume() {
        super.onResume()
        if (OtoKabulPrefs.isServiceRunning(applicationContext)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        TripLogRelay.methodChannel = methodChannel
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    /**
     * Ana ekran tuşu → isFinishing false → servis çalışmaya devam (arka plan).
     * Geri / son görevden silme → isFinishing true → servisi durdur.
     */
    override fun onDestroy() {
        if (isFinishing) {
            TripLogRelay.methodChannel = null
            if (OtoKabulPrefs.isServiceRunning(applicationContext)) {
                ForegroundService.stop(applicationContext)
            }
        }
        super.onDestroy()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations()) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}
