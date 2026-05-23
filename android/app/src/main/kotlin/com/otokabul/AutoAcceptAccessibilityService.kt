package com.otokabul

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * BiTaksi yolculuk teklif kartını izler.
 * Karar: alttaki "8 dk • X km" satırındaki km ≥ taksicinin seçtiği min km.
 * Üstteki "7 dk • … km" (alış) ve başka sayfalardaki "Kabul et" yok sayılır.
 */
class AutoAcceptAccessibilityService : AccessibilityService() {

    companion object {
        const val BITAKSI_PACKAGE = "com.projectslender"
        const val LOG_ACTION = "com.otokabul.LOG"

        @Volatile
        var instance: AutoAcceptAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private var isProcessing = false
    private var lastHandledAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
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

        // Aynı popup için tekrar tekrar işlem yapma
        val now = System.currentTimeMillis()
        if (OtoKabulLogic.shouldSkipEvent(now, lastHandledAt, isProcessing)) return

        val root = rootInActiveWindow ?: return
        try {
            handlePopup(root)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {
        // Servis kesildiğinde yapılacak bir şey yok
    }

    private fun handlePopup(root: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectAllTexts(root, allTexts)
        if (!OtoKabulLogic.isTripOfferScreen(allTexts)) return

        val km = OtoKabulLogic.journeyKmFromTexts(allTexts) ?: return
        val minKm = OtoKabulPrefs.getMinKm(applicationContext)

        if (OtoKabulLogic.shouldAccept(km, minKm)) {
            isProcessing = true
            val delayMs = OtoKabulLogic.randomDelayMs(random)
            handler.postDelayed({
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
                            val clicked = clickAcceptButton(freshRoot)
                            sendLog(freshKm, accepted = clicked, minKm)
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
            sendLog(km, accepted = false, minKm)
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

    /** Tam metni "Kabul et" olan butonu bulur ve tıklar — sadece teklif kartında. */
    private fun clickAcceptButton(root: AccessibilityNodeInfo): Boolean {
        val allTexts = mutableListOf<String>()
        collectAllTexts(root, allTexts)
        if (!OtoKabulLogic.isTripOfferScreen(allTexts)) return false

        val target = findNodeWithExactText(root, OtoKabulLogic.ACCEPT_BUTTON_TEXT) ?: return false
        val clickable = findClickableNode(target)
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeWithExactText(
        node: AccessibilityNodeInfo,
        exactText: String,
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.trim()
        if (nodeText == exactText) return node
        val desc = node.contentDescription?.toString()?.trim()
        if (desc == exactText) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeWithExactText(child, exactText)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findClickableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo = node
        while (!current.isClickable && current.parent != null) {
            val parent = current.parent ?: break
            if (current !== node) current.recycle()
            current = parent
        }
        return current
    }

    private fun sendLog(km: Double, accepted: Boolean, minKm: Double) {
        if (accepted) {
            AcceptSoundPlayer.playDing(applicationContext)
        }
        val time = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())
        val intent = Intent(LOG_ACTION).apply {
            setPackage(packageName)
            putExtra("km", km)
            putExtra("accepted", accepted)
            putExtra("minKm", minKm)
            putExtra("time", time)
        }
        sendBroadcast(intent)
    }
}
