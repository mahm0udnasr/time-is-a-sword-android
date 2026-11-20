package com.mahm0udnasr.sword.prayer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.prayer.defaultPrayerAllowlist
import com.mahm0udnasr.sword.core.prayer.prayerAllows

/**
 * What stays open during a Salah First lock (D6, step 7.1): the essential
 * apps this phone uses for calls, contacts, messages, the clock, the
 * calculator, the camera, maps and Settings (found through Android's own
 * intents, so it works on any brand), plus the apps the user adds (e.g. Quran
 * or adhkar). The dialer, the launcher, keyboards and this app are never
 * blocked at all (ForegroundResolver).
 */
class PrayerAllowlist(private val context: Context, private val config: () -> Config) {

    @Volatile private var resolved: Set<String> = emptySet()
    @Volatile private var launchable: Set<String> = emptySet()
    @Volatile private var browsers: Set<String> = emptySet()
    @Volatile private var loaded = false

    /** Call after install/uninstall (and once, lazily). Runs a few PackageManager queries. */
    fun refresh() {
        val pm = context.packageManager
        fun q(i: Intent) = runCatching { pm.queryIntentActivities(i, PackageManager.MATCH_ALL).map { it.activityInfo.packageName } }.getOrDefault(emptyList())
        fun main(category: String) = Intent(Intent.ACTION_MAIN).addCategory(category)
        fun isSystem(pkg: String) = runCatching {
            pm.getApplicationInfo(pkg, 0).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)
        // Only precise intents. Broad ones pull in far too much (on the S24 Ultra "smsto:" also
        // gives WhatsApp and Zoom, "geo:" Zoom, and the Settings action gives Secure Folder, a
        // whole second set of apps), so they are not used. Samsung's Clock and Calculator are
        // normal (non-system) apps, so these answers are taken as they are, except messaging:
        // chat apps may claim that category, so only the phone's own (system) one counts there.
        val out = listOf(
            main(Intent.CATEGORY_APP_CONTACTS),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            main(Intent.CATEGORY_APP_CALCULATOR),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            main(Intent.CATEGORY_APP_MAPS),
        ).flatMap(::q).toMutableSet()
        out += q(main(Intent.CATEGORY_APP_MESSAGING)).filter(::isSystem)
        out += SETTINGS
        // The SMS app the user chose (may be a third-party one): it's their messaging app.
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { out += it }
        out -= context.packageName
        resolved = out
        launchable = q(main(Intent.CATEGORY_LAUNCHER)).toSet()
        browsers = q(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)).toSet()
        loaded = true
    }

    private fun ensure() {
        if (!loaded) refresh()
    }

    /** The phone's essential apps (never a target, never a browser). */
    fun defaults(): Set<String> {
        ensure()
        val targets = config().targets.flatMap { it.packages }.toSet()
        return defaultPrayerAllowlist(resolved, targets, browsers)
    }

    /** May [pkg] stay open during a lock? */
    fun allows(pkg: String): Boolean {
        ensure()
        return prayerAllows(pkg, defaults() + config().protection.prayerAllowlist, launchable)
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
    }
}
