package com.otokabul

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Cihaz üzerinde çalışan native self-test kontrolleri.
 */
object SelfTestNative {

    fun testAccessibility(context: Context): Boolean =
        AccessibilityMonitor.isHealthy(context)

    fun testNotification(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun testForegroundService(context: Context): Boolean {
        if (ForegroundService.isActuallyRunning(context)) return true
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (service.service.className == ForegroundService::class.java.name) {
                return true
            }
        }
        return false
    }

    fun testSharedPreferences(context: Context): Boolean {
        val testKey = "self_test_km"
        val prefs = context.applicationContext
            .getSharedPreferences(OtoKabulPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(testKey, 5.0f).apply()
        val read = prefs.getFloat(testKey, -1f)
        prefs.edit().remove(testKey).apply()
        return read == 5.0f
    }

    fun testWakeLockPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WAKE_LOCK,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun testBitaksiInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    AutoAcceptAccessibilityService.BITAKSI_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    AutoAcceptAccessibilityService.BITAKSI_PACKAGE,
                    0,
                )
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * TEST 5: Parse senaryoları (mock metinler) + gerçek minKm gösterimi.
     * minKm SharedPreferences'tan okunur (otokabul_prefs).
     */
    fun testKmParse(context: Context): Map<String, Any> {
        val minKm = OtoKabulPrefs.getMinKm(context)
        val offerTexts = listOf(
            "7 dk • 2,56 km",
            "8 dk • 2,24 km",
            "Kabul et",
            "Toplam kazanç",
            "₺170 - 215",
        )
        val metersTexts = listOf(
            "1 dk • 509 m",
            "7 dk • 3,33 km",
            "Kabul et",
            "₺185 - 230",
        )

        val parse2_56 = OtoKabulLogic.parseKm("2,56 km") == 2.56
        val parse2_56_dot = OtoKabulLogic.parseKm("2.56 km") == 2.56
        val parse10_5 = OtoKabulLogic.parseKm("10,5 km") == 10.5
        val parseAbc = OtoKabulLogic.parseKm("abc km") == null
        val journeyKm = OtoKabulLogic.journeyKmFromTexts(offerTexts) == 2.24
        val ignoresPickupRow = OtoKabulLogic.journeyKmFromTexts(offerTexts) != 2.56
        val tripOfferOk = OtoKabulLogic.isTripOfferScreen(offerTexts)
        val otherScreenOk = !OtoKabulLogic.isTripOfferScreen(
            listOf("Kabul et", "Ayarlar"),
        )
        val metersJourney = OtoKabulLogic.journeyKmFromTexts(metersTexts) == 3.33
        val earnings = OtoKabulLogic.earningsFromTexts(offerTexts)
        val earningsOk = earnings?.min == 170 && earnings.max == 215

        val allPass = parse2_56 && parse2_56_dot && parse10_5 && parseAbc &&
            journeyKm && ignoresPickupRow && tripOfferOk && otherScreenOk &&
            metersJourney && earningsOk

        return mapOf(
            "minKm" to minKm,
            "allPass" to allPass,
            "2,56 km" to parse2_56,
            "2.56 km" to parse2_56_dot,
            "10,5 km" to parse10_5,
            "abc km" to parseAbc,
            "journey_row_km" to journeyKm,
            "ignores_pickup_km" to ignoresPickupRow,
            "trip_offer_screen" to tripOfferOk,
            "other_screen_rejected" to otherScreenOk,
            "meters_pickup_journey_3_33" to metersJourney,
            "earnings_170_215" to earningsOk,
        )
    }

    /**
     * TEST 6: Gerçek minKm ile karşılaştırma (SharedPreferences).
     * minKm-1 → ATLA, minKm+2 → KABUL, minKm (eşit) → KABUL
     */
    fun testMinKmComparison(context: Context): Map<String, Any> {
        val minKm = OtoKabulPrefs.getMinKm(context)
        val atlaKm = (minKm - 1.0).coerceAtLeast(0.0)
        val kabulKm = minKm + 2.0
        val esitKm = minKm

        val atlaOk = !OtoKabulLogic.shouldAccept(atlaKm, minKm)
        val kabulPlusOk = OtoKabulLogic.shouldAccept(kabulKm, minKm)
        val esitOk = OtoKabulLogic.shouldAccept(esitKm, minKm)

        val allPass = atlaOk && kabulPlusOk && esitOk

        return mapOf(
            "minKm" to minKm,
            "allPass" to allPass,
            "atla_km" to atlaKm,
            "atla_ok" to atlaOk,
            "kabul_km" to kabulKm,
            "kabul_plus_ok" to kabulPlusOk,
            "esit_km" to esitKm,
            "esit_ok" to esitOk,
        )
    }

    fun testRandomDelay(): Boolean {
        val random = java.util.Random(42)
        repeat(10) {
            if (!OtoKabulLogic.isDelayInRange(OtoKabulLogic.randomDelayMs(random))) {
                return false
            }
        }
        return true
    }

    fun testDoubleTapProtection(): Boolean = OtoKabulLogic.testDoubleTapProtection()

    /** TEST 11: GitHub active alanı + uzaktan iptal kontrolü */
    fun testLicenseRemoteRevoke(context: Context): Map<String, Any> =
        LicenseRemoteChecker.testRemoteActive(context)
}
