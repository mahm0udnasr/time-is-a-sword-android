package com.mahm0udnasr.sword.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.guard.GuardRules
import com.mahm0udnasr.sword.core.guard.NodeText
import com.mahm0udnasr.sword.core.guard.guardPage
import java.util.Locale

/**
 * Settings guard (plan §10.2, step 8.2): finds this app's protected pages in
 * Settings (its accessibility switch, App info, Device admin, the uninstall
 * dialog) by reading the window's texts, with the rules in `assets/guard.json`.
 *
 * The app's label and its accessibility description are added at run time in
 * both languages: Settings shows them in the *system* language, whatever the
 * app's own language is.
 */
class Guard(context: Context, json: String) {

    /** The app's label in Arabic and English. */
    val labels: List<String>

    val rules: GuardRules

    init {
        val ar = localized(context, "ar")
        val en = localized(context, "en")
        labels = listOf(ar.getString(R.string.app_name), en.getString(R.string.app_name)).distinct()
        // The accessibility page shows the service's description: its first sentence-part is specific enough.
        val desc = listOf(ar, en).map { it.getString(R.string.a11y_description).take(DESC_PREFIX) }
        rules = GuardRules.parse(json).withLabels(labels).withContains("accessibility", desc)
    }

    fun watches(pkg: String?): Boolean = pkg != null && pkg in rules.packages

    /** @return the protected page shown by [pkg]'s windows, or null */
    fun check(service: AccessibilityService, pkg: String): String? {
        if (!watches(pkg)) return null
        val nodes = ArrayList<NodeText>(256)
        for (w in service.windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = w.root ?: continue
            if (root.packageName?.toString() != pkg) continue
            collect(root, 0, nodes)
            if (nodes.size >= MAX_NODES) break
        }
        return guardPage(pkg, nodes, labels, rules)
    }

    private fun collect(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<NodeText>) {
        if (node == null || depth > MAX_DEPTH || out.size >= MAX_NODES) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val id = node.viewIdResourceName
        if (text != null || desc != null || id != null) out += NodeText(text, desc, id)
        for (i in 0 until node.childCount) collect(node.getChild(i), depth + 1, out)
    }

    private companion object {
        const val MAX_NODES = 1500
        const val MAX_DEPTH = 60
        const val DESC_PREFIX = 60

        fun localized(context: Context, lang: String): Context {
            val conf = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
            return context.createConfigurationContext(conf)
        }
    }
}
