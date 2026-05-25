package com.otokabul

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Random

/**
 * BiTaksi yolculuk teklif kartını izler — tüm pencereler taranır.
 */
class AutoAcceptAccessibilityService : AccessibilityService() {

    companion object {
        const val BITAKSI_PACKAGE = "com.projectslender"
        private const val SCAN_DEBOUNCE_MS = 80L

        @Volatile
        var instance: AutoAcceptAccessibilityService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random()
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var isProcessing = false
    private var lastHandledAt = 0L

    private val scanRunnable = Runnable { scanAllWindows() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        workerThread = HandlerThread("OtoKabulA11y").apply { start() }
        workerHandler = Handler(workerThread!!.looper)
        instance = this
    }

    override fun onDestroy() {
        workerHandler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!ServiceState.isActive(applicationContext)) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg != BITAKSI_PACKAGE) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        workerHandler?.let { h ->
            h.removeCallbacks(scanRunnable)
            h.postDelayed(scanRunnable, SCAN_DEBOUNCE_MS)
        }
    }

    override fun onInterrupt() {}

    private fun scanAllWindows() {
        val now = System.currentTimeMillis()
        if (OtoKabulLogic.shouldSkipEvent(now, lastHandledAt, isProcessing)) return

        val allTexts = AccessibilityRootHelper.collectAllTexts(this)
        if (allTexts.isEmpty()) return

        handleOfferTexts(allTexts)
    }

    private fun handleOfferTexts(allTexts: List<String>) {
        if (!OtoKabulLogic.isTripOfferScreen(allTexts)) return

        val km = OtoKabulLogic.journeyKmFromTexts(allTexts)
        val minKm = OtoKabulPrefs.getMinKm(applicationContext)
        val earnings = OtoKabulLogic.earningsFromTexts(allTexts)
        if (km == null) {
            emitOnMain(0.0, TripLogRelay.REASON_NO_KM, minKm, earnings)
            lastHandledAt = System.currentTimeMillis()
            return
        }

        if (OtoKabulLogic.shouldAccept(km, minKm)) {
            isProcessing = true
            val delayMs = OtoKabulLogic.randomDelayMs(random)
            workerHandler?.postDelayed({
                try {
                    val freshMinKm = OtoKabulPrefs.getMinKm(applicationContext)
                    val root = AccessibilityRootHelper.findBestRootForOffer(this)
                    if (root != null) {
                        try {
                            val freshTexts = mutableListOf<String>()
                            AccessibilityRootHelper.forEachRoot(this) { r ->
                                collectTextsInto(r, freshTexts)
                            }
                            if (!OtoKabulLogic.isTripOfferScreen(freshTexts)) return@postDelayed
                            val freshKm = OtoKabulLogic.journeyKmFromTexts(freshTexts)
                                ?: return@postDelayed
                            if (!OtoKabulLogic.shouldAccept(freshKm, freshMinKm)) return@postDelayed
                            val freshEarnings = OtoKabulLogic.earningsFromTexts(freshTexts)
                            val clicked = AcceptClickHelper.tryClickAccept(this, root)
                            val reason = if (clicked) {
                                TripLogRelay.REASON_ACCEPTED
                            } else {
                                TripLogRelay.REASON_CLICK_FAILED
                            }
                            emitOnMain(freshKm, reason, freshMinKm, freshEarnings)
                        } finally {
                            root.recycle()
                        }
                    }
                } finally {
                    isProcessing = false
                    lastHandledAt = System.currentTimeMillis()
                }
            }, delayMs.toLong())
        } else {
            emitOnMain(km, TripLogRelay.REASON_SKIPPED, minKm, earnings)
            lastHandledAt = System.currentTimeMillis()
        }
    }

    private fun collectTextsInto(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextsInto(child, out)
            child.recycle()
        }
    }

    private fun emitOnMain(
        km: Double,
        reason: String,
        minKm: Double,
        earnings: OtoKabulLogic.EarningRange? = null,
    ) {
        mainHandler.post {
            TripLogRelay.emit(
                applicationContext,
                km,
                reason == TripLogRelay.REASON_ACCEPTED,
                minKm,
                reason,
                earningMin = earnings?.min,
                earningMax = earnings?.max,
            )
            SheetsLogger.logTrip(
                context = applicationContext,
                km = km,
                accepted = reason == TripLogRelay.REASON_ACCEPTED,
                earningMin = earnings?.min,
                earningMax = earnings?.max,
            )
        }
    }
}
