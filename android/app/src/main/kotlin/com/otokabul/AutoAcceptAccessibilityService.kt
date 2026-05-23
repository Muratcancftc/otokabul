package com.otokabul

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Random

/**
 * BiTaksi yolculuk teklif kartını izler.
 * Karar: alttaki "8 dk • X km" satırındaki km ≥ taksicinin seçtiği min km.
 * Ağır iş arka plan thread'inde — ana thread ANR olmaz.
 */
class AutoAcceptAccessibilityService : AccessibilityService() {

    companion object {
        const val BITAKSI_PACKAGE = "com.projectslender"
        const val LOG_ACTION = "com.otokabul.LOG"
        private const val SCAN_DEBOUNCE_MS = 120L

        @Volatile
        var instance: AutoAcceptAccessibilityService? = null
            private set
    }

    private val random = Random()
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var isProcessing = false
    private var lastHandledAt = 0L

    private val scanRunnable = Runnable { scanActiveWindow() }

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
        if (!OtoKabulPrefs.isServiceRunning(applicationContext)) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (packageName != BITAKSI_PACKAGE) return

        val handler = workerHandler ?: return
        val delay = if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            0L
        } else {
            SCAN_DEBOUNCE_MS
        }
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, delay)
    }

    override fun onInterrupt() {
        // Servis kesildiğinde yapılacak bir şey yok
    }

    private fun scanActiveWindow() {
        val now = System.currentTimeMillis()
        if (OtoKabulLogic.shouldSkipEvent(now, lastHandledAt, isProcessing)) return

        val root = rootInActiveWindow ?: return
        try {
            handlePopup(root)
        } finally {
            root.recycle()
        }
    }

    private fun handlePopup(root: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectAllTexts(root, allTexts)
        if (!OtoKabulLogic.isTripOfferScreen(allTexts)) return

        val km = OtoKabulLogic.journeyKmFromTexts(allTexts)
        val minKm = OtoKabulPrefs.getMinKm(applicationContext)
        if (km == null) {
            sendLog(0.0, TripLogRelay.REASON_NO_KM, minKm)
            lastHandledAt = System.currentTimeMillis()
            return
        }

        if (OtoKabulLogic.shouldAccept(km, minKm)) {
            isProcessing = true
            val delayMs = OtoKabulLogic.randomDelayMs(random)
            workerHandler?.postDelayed({
                try {
                    val freshRoot = rootInActiveWindow
                    if (freshRoot != null) {
                        try {
                            val freshTexts = mutableListOf<String>()
                            collectAllTexts(freshRoot, freshTexts)
                            if (!OtoKabulLogic.isTripOfferScreen(freshTexts)) return@postDelayed
                            val freshKm = OtoKabulLogic.journeyKmFromTexts(freshTexts)
                                ?: return@postDelayed
                            if (!OtoKabulLogic.shouldAccept(freshKm, minKm)) return@postDelayed
                            val clicked = AcceptClickHelper.tryClickAccept(this, freshRoot)
                            val reason = if (clicked) {
                                TripLogRelay.REASON_ACCEPTED
                            } else {
                                TripLogRelay.REASON_CLICK_FAILED
                            }
                            sendLog(freshKm, reason, minKm)
                        } finally {
                            freshRoot.recycle()
                        }
                    }
                } finally {
                    isProcessing = false
                    lastHandledAt = System.currentTimeMillis()
                }
            }, delayMs.toLong())
        } else {
            sendLog(km, TripLogRelay.REASON_SKIPPED, minKm)
            lastHandledAt = System.currentTimeMillis()
        }
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.let { out.add(it) }
        node.contentDescription?.toString()?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, out)
            child.recycle()
        }
    }

    private fun sendLog(km: Double, reason: String, minKm: Double) {
        val accepted = reason == TripLogRelay.REASON_ACCEPTED
        if (accepted) {
            AcceptSoundPlayer.playDing(applicationContext)
        }
        TripLogRelay.emit(applicationContext, km, accepted, minKm, reason)
    }
}
