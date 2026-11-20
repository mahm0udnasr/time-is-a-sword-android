package com.mahm0udnasr.sword.core.loosen

import com.mahm0udnasr.sword.core.model.Appearance
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Group
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Protection
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.rules.parseHM

/*
 * Turn "current config → next config" into change operations and say which of
 * them make the rules looser (port of loosen.js, extended for targets' apps and
 * domains, the protection switches and Salah First).
 *
 * Stricter (or neutral) ops apply at once; looser ops need the friction step
 * first. Either way they then apply at once.
 */

/** What an op is about, for the reasons list ("Instagram: …", "Group “Chat”: …"). */
sealed interface Subject {
    data class OfTarget(val name: String) : Subject
    data class OfGroup(val name: String) : Subject
    data object OfProtection : Subject
    data object OfPrayer : Subject
    data object OfAppearance : Subject
}

/** One entity or one settings block that changed. Empty [reasons] = not looser. */
sealed interface ConfigOp {
    val subject: Subject
    val reasons: List<LooseReason>
    val isLooser: Boolean get() = reasons.isNotEmpty()

    data class TargetPut(val value: Target, override val reasons: List<LooseReason>) : ConfigOp {
        override val subject get() = Subject.OfTarget(value.name)
    }

    data class TargetDelete(val id: String, val name: String, override val reasons: List<LooseReason>) : ConfigOp {
        override val subject get() = Subject.OfTarget(name)
    }

    data class GroupPut(val value: Group, override val reasons: List<LooseReason>) : ConfigOp {
        override val subject get() = Subject.OfGroup(value.name)
    }

    data class GroupDelete(val id: String, val name: String, override val reasons: List<LooseReason>) : ConfigOp {
        override val subject get() = Subject.OfGroup(name)
    }

    data class ProtectionPut(val value: Protection, override val reasons: List<LooseReason>) : ConfigOp {
        override val subject get() = Subject.OfProtection
    }

    data class PrayerPut(val value: PrayerSettings, override val reasons: List<LooseReason>) : ConfigOp {
        override val subject get() = Subject.OfPrayer
    }

    /** Theme and language never need friction. */
    data class AppearancePut(val value: Appearance) : ConfigOp {
        override val subject get() = Subject.OfAppearance
        override val reasons get() = emptyList<LooseReason>()
    }
}

// ---------------------------------------------------------------------------
// Rules

private fun ruleForDay(rules: List<Rule>, d: Int): Rule? = rules.firstOrNull { d in it.days }

/** Minutes of the day (0..1439) that a rule's windows allow. */
private fun allowedMinutes(rule: Rule): BooleanArray {
    val set = BooleanArray(1440)
    for (w in rule.windows) {
        val a = parseHM(w.start)
        val b = parseHM(w.end)
        if (a == b) continue
        var m = a
        while (m != b) {
            set[m] = true
            m = (m + 1) % 1440
        }
    }
    return set
}

/** Why [next] is looser than [prev] for one day, or null if it is not. */
private fun ruleLoosening(prev: Rule?, next: Rule?): RuleWhy? {
    if (prev == null) return null // was unrestricted: anything is at least as strict
    if (next == null) return RuleWhy.RuleRemoved
    if (prev.mode == Mode.BLOCK) {
        return if (next.mode == Mode.BLOCK) null else RuleWhy.Unblocked
    }
    if (next.mode == Mode.BLOCK) return null
    if (prev.mode.hasLimit) {
        if (!next.mode.hasLimit) return RuleWhy.LimitRemoved
        if (next.limitMinutes > prev.limitMinutes) return RuleWhy.LimitRaised(prev.limitMinutes, next.limitMinutes)
    }
    if (prev.mode.hasSchedule) {
        if (!next.mode.hasSchedule) return RuleWhy.HoursRemoved
        val p = allowedMinutes(prev)
        val n = allowedMinutes(next)
        if (n.indices.any { n[it] && !p[it] }) return RuleWhy.HoursWidened
    }
    return null
}

/** Compare two rule lists day by day; days with the same reason are grouped (first day's order). */
fun rulesLoosening(prevRules: List<Rule>, nextRules: List<Rule>): List<LooseReason> {
    val byReason = LinkedHashMap<RuleWhy, MutableList<Int>>()
    for (d in 0..6) {
        val why = ruleLoosening(ruleForDay(prevRules, d), ruleForDay(nextRules, d)) ?: continue
        byReason.getOrPut(why) { mutableListOf() } += d
    }
    return byReason.map { (why, days) -> LooseReason.Rule(days, why) }
}

// ---------------------------------------------------------------------------
// Entities

private fun targetLoosening(prev: Target, next: Target, groups: List<Group>): List<LooseReason> {
    if (!prev.enabled) return emptyList() // it was not restricting anything
    if (!next.enabled) return listOf(LooseReason.SwitchedOff)
    val reasons = mutableListOf<LooseReason>()
    val oldGroup = prev.groupId?.let { id -> groups.firstOrNull { it.id == id && it.enabled } }
    if (oldGroup != null && next.groupId != prev.groupId) reasons += LooseReason.LeavesGroup(oldGroup.name)
    prev.packages.filter { it !in next.packages }.forEach { reasons += LooseReason.PackageRemoved(it) }
    prev.domains.filter { it !in next.domains }.forEach { reasons += LooseReason.DomainRemoved(it) }
    return reasons + rulesLoosening(prev.rules, next.rules)
}

private fun groupLoosening(prev: Group, next: Group): List<LooseReason> {
    if (!prev.enabled) return emptyList()
    if (!next.enabled) return listOf(LooseReason.SwitchedOff)
    return rulesLoosening(prev.rules, next.rules)
}

/**
 * @param prayerOn Salah First is on now. Allowing an app during prayer while it
 *   is off restricts nothing yet, so it is free (like setting up Salah First itself).
 */
fun protectionLoosening(prev: Protection, next: Protection, prayerOn: Boolean = true): List<LooseReason> {
    val reasons = mutableListOf<LooseReason>()
    if (prev.guardEnabled && !next.guardEnabled) reasons += LooseReason.GuardOff
    if (prev.blockUnsupportedBrowsers && !next.blockUnsupportedBrowsers) reasons += LooseReason.UnsupportedBrowsersAllowed
    if (prev.requireAutoTime && !next.requireAutoTime) reasons += LooseReason.AutoTimeNotRequired
    if (prayerOn) next.prayerAllowlist.filter { it !in prev.prayerAllowlist }.forEach { reasons += LooseReason.PrayerAllowlistAdded(it) }
    return reasons
}

/** Restricts anything at all? (Removing something that restricts nothing is not looser.) */
private fun targetRestricts(t: Target, groups: List<Group>) =
    t.enabled && (t.rules.isNotEmpty() || groups.any { it.id == t.groupId && it.enabled })

private fun groupRestricts(g: Group) = g.enabled && g.rules.isNotEmpty()

// ---------------------------------------------------------------------------
// Diff and apply

/** Only what changed: targets (existing order, then new), groups, then the settings blocks. */
fun diffState(cur: Config, next: Config): List<ConfigOp> {
    val ops = mutableListOf<ConfigOp>()
    val curGroups = cur.groups

    val nextTargets = next.targets.associateBy { it.id }
    for (prev in cur.targets) {
        val n = nextTargets[prev.id]
        if (n == null) {
            ops += ConfigOp.TargetDelete(prev.id, prev.name, if (targetRestricts(prev, curGroups)) listOf(LooseReason.Removed) else emptyList())
        } else if (prev != n) {
            ops += ConfigOp.TargetPut(n, targetLoosening(prev, n, curGroups))
        }
    }
    val curTargetIds = cur.targets.map { it.id }.toSet()
    next.targets.filter { it.id !in curTargetIds }.forEach { ops += ConfigOp.TargetPut(it, emptyList()) }

    val nextGroups = next.groups.associateBy { it.id }
    for (prev in curGroups) {
        val n = nextGroups[prev.id]
        if (n == null) {
            ops += ConfigOp.GroupDelete(prev.id, prev.name, if (groupRestricts(prev)) listOf(LooseReason.Removed) else emptyList())
        } else if (prev != n) {
            ops += ConfigOp.GroupPut(n, groupLoosening(prev, n))
        }
    }
    val curGroupIds = curGroups.map { it.id }.toSet()
    next.groups.filter { it.id !in curGroupIds }.forEach { ops += ConfigOp.GroupPut(it, emptyList()) }

    if (cur.protection != next.protection) ops += ConfigOp.ProtectionPut(next.protection, protectionLoosening(cur.protection, next.protection, cur.prayer.enabled))
    if (cur.prayer != next.prayer) ops += ConfigOp.PrayerPut(next.prayer, prayerLoosening(cur.prayer, next.prayer))
    if (cur.appearance != next.appearance) ops += ConfigOp.AppearancePut(next.appearance)
    return ops
}

/** Apply ops to a config. A put for a missing entity adds it. Returns a new config. */
fun applyOps(config: Config, ops: List<ConfigOp>): Config {
    val targets = config.targets.toMutableList()
    val groups = config.groups.toMutableList()
    var c = config
    fun <T> put(list: MutableList<T>, id: (T) -> String, value: T) {
        val i = list.indexOfFirst { id(it) == id(value) }
        if (i >= 0) list[i] = value else list += value
    }
    for (o in ops) {
        when (o) {
            is ConfigOp.TargetPut -> put(targets, Target::id, o.value)
            is ConfigOp.TargetDelete -> targets.removeAll { it.id == o.id }
            is ConfigOp.GroupPut -> put(groups, Group::id, o.value)
            is ConfigOp.GroupDelete -> groups.removeAll { it.id == o.id }
            is ConfigOp.ProtectionPut -> c = c.copy(protection = o.value)
            is ConfigOp.PrayerPut -> c = c.copy(prayer = o.value)
            is ConfigOp.AppearancePut -> c = c.copy(appearance = o.value)
        }
    }
    return c.copy(targets = targets, groups = groups)
}

/** One line of the reasons list: what it is about + why it is looser. */
data class LooseItem(val subject: Subject, val reason: LooseReason)

/** Port of describeLoosening(): every reason of every op, in order. */
fun describeLoosening(ops: List<ConfigOp>): List<LooseItem> = ops.flatMap { o -> o.reasons.map { LooseItem(o.subject, it) } }
