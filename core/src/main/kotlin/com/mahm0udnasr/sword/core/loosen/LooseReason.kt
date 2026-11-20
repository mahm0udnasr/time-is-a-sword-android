package com.mahm0udnasr.sword.core.loosen

import com.mahm0udnasr.sword.core.prayer.Prayer

/**
 * Why a change makes the rules looser. Keys + parameters, not sentences: the
 * UI turns them into text in the user's language (port of the `lo.*` and
 * `pl.*` i18n keys of the extension).
 */
sealed interface LooseReason {
    /** A rule change on some weekdays (0 = Sunday … 6 = Saturday). */
    data class Rule(val days: List<Int>, val why: RuleWhy) : LooseReason

    data object SwitchedOff : LooseReason
    data object Removed : LooseReason
    data class LeavesGroup(val groupName: String) : LooseReason

    // Android: a target's apps and domains
    data class PackageRemoved(val pkg: String) : LooseReason
    data class DomainRemoved(val domain: String) : LooseReason
    data class SectionRemoved(val section: String) : LooseReason

    // Android: protection switches (§8.5)
    data object GuardOff : LooseReason
    data object UnsupportedBrowsersAllowed : LooseReason
    data object AutoTimeNotRequired : LooseReason
    data class PrayerAllowlistAdded(val pkg: String) : LooseReason

    // Salah First (port of prayerLoosening())
    data object PrayerOff : LooseReason
    data object PrayerLocationChanged : LooseReason
    data class PrayerBeforeReduced(val a: Int, val b: Int) : LooseReason
    data class PrayerAfterReduced(val a: Int, val b: Int) : LooseReason
    data class PrayerSwitchedOff(val prayer: Prayer) : LooseReason
    data class PrayerAdjusted(val prayer: Prayer, val a: Int, val b: Int) : LooseReason
}

/** Why one day's rule got looser. */
sealed interface RuleWhy {
    data object RuleRemoved : RuleWhy
    data object Unblocked : RuleWhy
    data object LimitRemoved : RuleWhy
    data class LimitRaised(val a: Int, val b: Int) : RuleWhy
    data object HoursRemoved : RuleWhy
    data object HoursWidened : RuleWhy
}
