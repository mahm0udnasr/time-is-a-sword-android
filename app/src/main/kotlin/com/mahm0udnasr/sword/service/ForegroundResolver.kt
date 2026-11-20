package com.mahm0udnasr.sword.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/**
 * What is on screen (plan §8.1, step 3.3).
 * @property pkg the active application window's package (null = none seen)
 * @property visible every visible application window's package (split screen / freeform)
 * @property title the active window's title (WebAPKs: the web app's name)
 * @property ignored [pkg] is never treated as a target (self, keyboard, System UI, launcher, dialer)
 */
data class WindowsSnapshot(
    val pkg: String?,
    val visible: List<String>,
    val title: String?,
    val ignored: Boolean,
)

/**
 * Finds the foreground app from the service's window list, never from
 * event.packageName alone (Spike A: it is often null, and keyboards, the
 * notification shade and dialogs fire events too).
 */
class ForegroundResolver(private val context: Context) {

    /** Packages that are never a target and never blocked (rule 9: never brick the phone). */
    @Volatile
    var ignoredPackages: Set<String> = emptySet()
        private set

    /** The current Home apps (Spike B: the block screen waits for one of them to be in front). */
    @Volatile
    var launchers: Set<String> = emptySet()
        private set

    fun refreshIgnored() {
        val pm = context.packageManager
        val out = mutableSetOf(context.packageName, "com.android.systemui", "android")
        runCatching {
            context.getSystemService(InputMethodManager::class.java).inputMethodList.forEach { out += it.packageName }
        }
        // Not Settings' FallbackHome (priority -1000, shown only while booting): Settings is no launcher,
        // and ignoring it would switch the Settings guard off.
        launchers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .filter { it.priority >= 0 }.map { it.activityInfo.packageName }
        }.getOrDefault(emptyList()).toSet()
        out += launchers
        out += resolvers(pm, Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")))
        out += resolvers(pm, Intent("android.intent.action.CALL_EMERGENCY"))
        out += DIALER_EXTRAS
        ignoredPackages = out
    }

    private fun resolvers(pm: PackageManager, i: Intent): List<String> =
        runCatching { pm.queryIntentActivities(i, PackageManager.MATCH_ALL).map { it.activityInfo.packageName } }.getOrDefault(emptyList())

    fun resolve(service: AccessibilityService): WindowsSnapshot {
        val apps = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val active = apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused } ?: apps.firstOrNull()
        val pkg = active?.root?.packageName?.toString()
        val visible = apps.mapNotNull { it.root?.packageName?.toString() }.distinct()
        return WindowsSnapshot(pkg, visible, active?.title?.toString(), pkg != null && pkg in ignoredPackages)
    }

    private companion object {
        /** In-call and emergency screens that may not answer ACTION_DIAL. */
        val DIALER_EXTRAS = setOf(
            "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
            "com.android.incallui", "com.samsung.android.incallui", "com.android.server.telecom",
            "com.android.phone", "com.android.emergency", "com.google.android.apps.safetyhub",
        )
    }
}
