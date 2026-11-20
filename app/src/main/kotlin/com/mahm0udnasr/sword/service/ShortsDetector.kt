package com.mahm0udnasr.sword.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.mahm0udnasr.sword.core.shorts.ShortsRules
import com.mahm0udnasr.sword.core.shorts.ShortsSection
import com.mahm0udnasr.sword.core.shorts.matchInApp
import com.mahm0udnasr.sword.core.shorts.matchWeb
import com.mahm0udnasr.sword.core.shorts.matchWholeApp

/**
 * Finds the short-video section in front (Phase 12, step 12.4): a whole
 * short-video app by package, a short-video page in a browser by host + path,
 * or the player inside a bigger app by its view ids / selected tab
 * (`assets/shorts.json`, recorded with the spike).
 */
class ShortsDetector(json: String) {
    val rules: ShortsRules = ShortsRules.parse(json)

    /** Packages whose windows are re-read on content changes (the player opens inside the same window). */
    fun watches(pkg: String?): Boolean = pkg != null && pkg in rules.inAppPackages

    fun forApp(service: AccessibilityService, pkg: String): ShortsSection? =
        matchWholeApp(rules, pkg) ?: if (watches(pkg)) inApp(service, pkg) else null

    fun forWeb(host: String?, path: String?): ShortsSection? = matchWeb(rules, host, path)

    private fun inApp(service: AccessibilityService, pkg: String): ShortsSection? {
        val ids = HashSet<String>(128)
        val selected = ArrayList<String>(8)
        var n = 0
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > MAX_DEPTH || n >= MAX_NODES) return
            n++
            // Only what the user can see: Instagram keeps every tab alive off screen.
            if (!node.isVisibleToUser) return
            node.viewIdResourceName?.let { ids += it }
            if (node.isSelected) node.contentDescription?.let { selected += it.toString() }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        for (w in service.windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = w.root ?: continue
            if (root.packageName?.toString() != pkg) continue
            walk(root, 0)
        }
        return matchInApp(rules, pkg, ids, selected)
    }

    private companion object {
        const val MAX_NODES = 2500
        const val MAX_DEPTH = 80
    }
}
