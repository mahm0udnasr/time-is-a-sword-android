package com.mahm0udnasr.sword.core.prayer

/**
 * What stays open during a Salah First lock (D6, Q2 = "all apps except an allowlist").
 *
 * Open: the allowlist (phone, contacts, messages, clock, calculator, camera,
 * maps, Settings, plus the apps the user adds) and every package that has no
 * launcher icon: those only appear when something else calls them
 * (permission dialogs, the package installer, sign-in sheets), and closing
 * them would break the allowed apps (rule 9: never brick the phone).
 * Browsers are closed on every website anyway (checked before this, in decide()).
 * Self, keyboards, System UI, launchers and dialers are ignored before this too.
 *
 * @param allow default allowlist plus the user's packages
 * @param launchable packages with a launcher icon
 */
fun prayerAllows(pkg: String, allow: Set<String>, launchable: Set<String>): Boolean =
    pkg in allow || pkg !in launchable

/**
 * The default allowlist: what the phone resolved for the essential intents,
 * minus anything the user lists as a target (a social app that happens to
 * handle "take a photo" must not slip through) and minus browsers.
 */
fun defaultPrayerAllowlist(resolved: Set<String>, targetPackages: Set<String>, browsers: Set<String>): Set<String> =
    resolved - targetPackages - browsers
