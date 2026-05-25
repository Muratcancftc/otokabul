package com.otokabul

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** BiTaksi popup bazen aktif pencerede değil — tüm erişilebilirlik pencerelerini tara. */
object AccessibilityRootHelper {

    fun collectAllTexts(service: AccessibilityService): List<String> {
        val out = mutableListOf<String>()
        forEachRoot(service) { root ->
            collectTexts(root, out)
        }
        return out
    }

    fun forEachRoot(service: AccessibilityService, block: (AccessibilityNodeInfo) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val windows = service.windows
            if (windows != null && windows.isNotEmpty()) {
                for (window in windows) {
                    val root = window.root ?: continue
                    try {
                        block(root)
                    } finally {
                        root.recycle()
                    }
                }
                return
            }
        }
        val root = service.rootInActiveWindow ?: return
        try {
            block(root)
        } finally {
            root.recycle()
        }
    }

    fun findBestRootForOffer(service: AccessibilityService): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0

        forEachRoot(service) { root ->
            val texts = mutableListOf<String>()
            collectTexts(root, texts)
            val score = offerScore(texts)
            if (score > bestScore) {
                best?.recycle()
                best = AccessibilityNodeInfo.obtain(root)
                bestScore = score
            }
        }
        return best
    }

    private fun offerScore(texts: List<String>): Int {
        if (!OtoKabulLogic.hasAcceptButton(texts)) return 0
        var score = 10
        if (OtoKabulLogic.isTripOfferScreen(texts)) score += 50
        score += OtoKabulLogic.allKmFromTexts(texts).size * 5
        if (texts.any { it.contains("reddet", ignoreCase = true) }) score += 20
        if (texts.any { it.contains("₺") }) score += 15
        return score
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, out)
            child.recycle()
        }
    }
}
