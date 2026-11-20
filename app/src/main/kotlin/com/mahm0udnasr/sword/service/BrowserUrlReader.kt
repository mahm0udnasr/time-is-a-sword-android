package com.mahm0udnasr.sword.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityWindowInfo
import com.mahm0udnasr.sword.core.rules.hostOf
import com.mahm0udnasr.sword.core.rules.hostOfUrlBar
import com.mahm0udnasr.sword.data.StoreJson
import kotlinx.serialization.Serializable

/** One entry of assets/browsers.json: data, so a changed view id needs no code change. */
@Serializable
data class BrowserSpec(
    val name: String,
    val packages: List<String>,
    val urlBarIds: List<String>,
    val verified: Boolean = false,
)

/** What the URL bar told us. */
sealed interface UrlRead {
    /** A host was read. [source] is "urlbar" or "webapk". */
    data class Host(val host: String, val source: String) : UrlRead

    /** The user is typing in the URL bar: keep the previous host. */
    data object Typing : UrlRead

    /** No URL bar and no WebAPK (new tab page, browser settings, unknown layout). */
    data object None : UrlRead
}

/**
 * Reads the website open in a supported browser (plan §8.2, step 3.4):
 * the URL-bar node by view id (Custom Tabs use the same id in Chrome, verified),
 * skipped while focused; otherwise an installed web app (WebAPK) recognized
 * by its window title.
 */
class BrowserUrlReader(private val context: Context, browsersJson: String) {
    val browsers: List<BrowserSpec> = StoreJson.decodeFromString(browsersJson)
    private val byPackage: Map<String, BrowserSpec> = browsers.flatMap { b -> b.packages.map { it to b } }.toMap()

    /** A browser whose URL bar we know how to read. */
    fun isBrowser(pkg: String?) = pkg != null && byPackage[pkg]?.urlBarIds?.isNotEmpty() == true

    /**
     * Installed apps that open any https link but whose URL bar we can't read
     * (not in browsers.json, or listed without an id like Via). Blocked while web
     * rules are active (D13), or every website rule could be skipped by switching browser.
     */
    @Volatile
    var unsupported: Set<String> = emptySet()
        private set

    fun refreshUnsupported(ignored: Set<String>) {
        val pm = context.packageManager
        val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
        val handlers = runCatching { pm.queryIntentActivities(view, PackageManager.MATCH_ALL).map { it.activityInfo.packageName } }
            .getOrDefault(emptyList())
        unsupported = handlers.filter { !isBrowser(it) && it !in ignored && !it.startsWith(WEBAPK_PREFIX) }.toSet()
    }

    /** WebAPK label → host, from each WebAPK's manifest meta-data (Spike A). */
    @Volatile
    var webApks: Map<String, String> = emptyMap()
        private set

    fun refreshWebApks() {
        val pm = context.packageManager
        val launchers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
        val out = mutableMapOf<String, String>()
        for (ri in launchers) {
            val pkg = ri.activityInfo.packageName
            if (!pkg.startsWith(WEBAPK_PREFIX)) continue
            runCatching {
                val ai = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                val md = ai.metaData ?: return@runCatching
                val url = md.getString("org.chromium.webapk.shell_apk.scope") ?: md.getString("org.chromium.webapk.shell_apk.startUrl")
                val host = url?.let(::hostOf) ?: return@runCatching
                out[pm.getApplicationLabel(ai).toString()] = host
                ri.loadLabel(pm)?.toString()?.let { out[it] = host }
            }
        }
        webApks = out
    }

    fun read(service: AccessibilityService, pkg: String, windowTitle: String?): UrlRead {
        val spec = byPackage[pkg] ?: return UrlRead.None
        // Every window of the browser, not only the first: a dialog or bottom sheet
        // (e.g. "Open in app?") is a second window without the URL bar.
        val roots = service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == pkg }
        for (root in roots) {
            for (id in spec.urlBarIds) {
                val node = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull() ?: continue
                if (node.isFocused) return UrlRead.Typing
                val host = hostOfUrlBar(node.text?.toString() ?: "")
                if (host != null) return UrlRead.Host(host, "urlbar")
            }
        }
        windowTitle?.let { t -> webApks[t] ?: webApks[t.removePrefix("Chrome: ")] }?.let { return UrlRead.Host(it, "webapk") }
        return UrlRead.None
    }

    private companion object {
        const val WEBAPK_PREFIX = "org.chromium.webapk."
    }
}
