package com.otokabul

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * "Kabul et" tıklama — önce ACTION_CLICK, olmazsa jest (koordinat) ile dener.
 * Ana thread'i bloklamaz (ANR önleme).
 */
object AcceptClickHelper {

    fun tryClickAccept(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
    ): Boolean {
        val allTexts = mutableListOf<String>()
        collectAllTexts(root, allTexts)
        if (!OtoKabulLogic.isTripOfferScreen(allTexts)) return false

        val target = findAcceptNode(root) ?: return false
        val clickable = findClickableNode(target)
        try {
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            if (target !== clickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return dispatchTapGesture(service, clickable)
            }
            return false
        } finally {
            target.recycle()
            clickable.recycle()
        }
    }

    private fun findAcceptNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        node.text?.toString()?.let {
            if (OtoKabulLogic.isAcceptButtonText(it)) return AccessibilityNodeInfo.obtain(node)
        }
        node.contentDescription?.toString()?.let {
            if (OtoKabulLogic.isAcceptButtonText(it)) return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAcceptNode(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findClickableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = AccessibilityNodeInfo.obtain(node)
        while (!current.isClickable && current.parent != null) {
            val parent = current.parent ?: break
            current.recycle()
            current = AccessibilityNodeInfo.obtain(parent)
            parent.recycle()
        }
        return current
    }

    private fun dispatchTapGesture(
        service: AccessibilityService,
        node: AccessibilityNodeInfo,
    ): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false

        val path = Path().apply {
            moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
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
}
