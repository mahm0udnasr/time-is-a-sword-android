package com.mahm0udnasr.sword.core.rules

import com.mahm0udnasr.sword.core.model.DayUsage
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.Group
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Pause
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.model.RuleHolder
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.core.model.TimeWindow
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Is a target allowed right now? (port of the rule/group/verdict part of rules.js)
 * Usage is keyed by target id.
 */

/** Everything evaluate() needs. [now] is epoch ms; [zone] is the phone's zone. */
data class EvalContext(
    val now: Long,
    val zone: ZoneId,
    val resetHour: Int = Defaults.RESET_HOUR,
    val usage: Map<String, DayUsage> = emptyMap(),
    val targets: List<Target> = emptyList(),
    val groups: List<Group> = emptyList(),
    /** Key = target id. */
    val pauses: Map<String, Pause> = emptyMap(),
)

enum class BlockKind { LIMIT, SCHEDULE, PAUSE }

/** Why a target's own (or its group's) rule blocks it. */
enum class RuleReason { BLOCKED, LIMIT, SCHEDULE }

sealed interface Verdict {
    val blocked: Boolean

    /**
     * @property remainingSec budget left (target or group, whichever is less), or null
     * @property blockAt when it gets blocked if used continuously from now, or null
     * @property blockKind what [blockAt] is
     * @property paused true while a pause is active
     */
    data class Allowed(
        val remainingSec: Double? = null,
        val blockAt: Long? = null,
        val blockKind: BlockKind? = null,
        val paused: Boolean = false,
    ) : Verdict {
        override val blocked get() = false
    }

    /**
     * @property until when it opens again, or null if not within the next week
     * @property group the group whose rule blocks it, or null for the target's own rule
     */
    data class Blocked(val reason: RuleReason, val until: Long?, val group: Group? = null) : Verdict {
        override val blocked get() = true
    }
}

/** Seconds used today by a target. */
fun usedSeconds(usage: Map<String, DayUsage>, targetId: String, now: Long, zone: ZoneId, resetHour: Int = 0): Double =
    usage[dayKey(now, zone, resetHour)]?.seconds?.get(targetId) ?: 0.0

/** The first rule whose days include today, or null (unrestricted today). */
fun activeRule(entity: RuleHolder, now: Long, zone: ZoneId, resetHour: Int = 0): Rule? {
    val weekday = weekdayOf(now, zone, resetHour)
    return entity.rules.firstOrNull { weekday in it.days }
}

/** "13:30" → 810 */
fun parseHM(hm: String): Int {
    val (h, m) = hm.split(':').map { it.trim().toInt() }
    return h * 60 + m
}

/** Is a minute-of-day inside a window? Handles windows that cross midnight. */
fun inWindow(minute: Double, w: TimeWindow): Boolean {
    val a = parseHM(w.start)
    val b = parseHM(w.end)
    if (a == b) return false // empty window
    return if (a < b) minute >= a && minute < b else minute >= a || minute < b
}

private fun inAnyWindow(rule: Rule, t: Long, zone: ZoneId): Boolean {
    val minute = minuteOfDay(t, zone)
    return rule.windows.any { inWindow(minute, it) }
}

/** If ts is inside one of the rule's windows, when that window ends; else null. */
internal fun windowEndAt(rule: Rule, ts: Long, zone: ZoneId): Long? {
    val minute = minuteOfDay(ts, zone)
    val date = zoned(ts, zone).toLocalDate()
    var best: Long? = null
    for (w in rule.windows) {
        if (!inWindow(minute, w)) continue
        val b = parseHM(w.end)
        val time = LocalTime.of(b / 60, b % 60)
        var end = epochOf(date, time, zone)
        if (end <= ts) end = epochOf(date.plusDays(1), time, zone)
        if (best == null || end > best) best = end
    }
    return best
}

private fun limitSecondsOf(rule: Rule): Double = max(0, rule.limitMinutes) * 60.0

private data class RuleCheck(val blocked: Boolean, val reason: RuleReason? = null, val remainingSec: Double? = null)

/** Check one rule at time t with usedSec already spent that day. */
private fun checkRule(rule: Rule?, t: Long, usedSec: Double, zone: ZoneId): RuleCheck {
    if (rule == null) return RuleCheck(false)
    if (rule.mode == Mode.BLOCK) return RuleCheck(true, RuleReason.BLOCKED)
    if (rule.mode.hasLimit) {
        val remainingSec = limitSecondsOf(rule) - usedSec
        if (remainingSec <= 0) return RuleCheck(true, RuleReason.LIMIT)
        if (rule.mode.hasSchedule && !inAnyWindow(rule, t, zone)) return RuleCheck(true, RuleReason.SCHEDULE)
        return RuleCheck(false, remainingSec = remainingSec)
    }
    if (rule.mode.hasSchedule && !inAnyWindow(rule, t, zone)) return RuleCheck(true, RuleReason.SCHEDULE)
    return RuleCheck(false)
}

// ---------------------------------------------------------------------------
// Groups

/** The enabled group a target belongs to, or null. */
fun groupOf(target: Target, groups: List<Group>): Group? =
    target.groupId?.let { id -> groups.firstOrNull { it.id == id && it.enabled } }

/** Enabled member targets of a group. A switched-off target is out of its group. */
fun membersOf(group: Group, targets: List<Target>): List<Target> =
    targets.filter { it.groupId == group.id && it.enabled }

/** Seconds used today by all enabled members of a group, together. */
fun groupUsedSeconds(group: Group, targets: List<Target>, usage: Map<String, DayUsage>, now: Long, zone: ZoneId, resetHour: Int = 0): Double =
    membersOf(group, targets).sumOf { usedSeconds(usage, it.id, now, zone, resetHour) }

// ---------------------------------------------------------------------------
// Verdict

private data class Part(val entity: RuleHolder, val used: Double, val group: Group?)

/** The rule holders that apply: the target itself plus its group. The stricter wins. */
private fun partsFor(target: Target, ctx: EvalContext): List<Part> {
    val parts = mutableListOf(Part(target, usedSeconds(ctx.usage, target.id, ctx.now, ctx.zone, ctx.resetHour), null))
    groupOf(target, ctx.groups)?.let { g ->
        parts += Part(g, groupUsedSeconds(g, ctx.targets, ctx.usage, ctx.now, ctx.zone, ctx.resetHour), g)
    }
    return parts
}

private data class PartsCheck(val blocked: Boolean, val reason: RuleReason? = null, val group: Group? = null, val remainingSec: Double? = null)

/** Check all parts at time t. [fresh] = t is on a later day, so usage is 0. */
private fun checkParts(parts: List<Part>, t: Long, zone: ZoneId, resetHour: Int, fresh: Boolean): PartsCheck {
    var remainingSec: Double? = null
    for (p in parts) {
        val r = checkRule(activeRule(p.entity, t, zone, resetHour), t, if (fresh) 0.0 else p.used, zone)
        if (r.blocked) return PartsCheck(true, r.reason, p.group)
        if (r.remainingSec != null) remainingSec = min(remainingSec ?: Double.POSITIVE_INFINITY, r.remainingSec)
    }
    return PartsCheck(false, remainingSec = remainingSec)
}

/**
 * When does a blocked target open again? Access can only start at a window's
 * start or at a daily reset, so only those moments over the next 8 days are
 * tested. Assumes no more time is spent. Null if it never opens in that range.
 */
private fun nextOpenAt(parts: List<Part>, now: Long, zone: ZoneId, resetHour: Int): Long? {
    val today = dayKey(now, zone, resetHour)
    val base = zoned(now, zone).toLocalDate()
    val candidates = sortedSetOf<Long>()
    for (i in 0..8) {
        val day = base.plusDays(i.toLong())
        candidates += epochOf(day, LocalTime.of(resetHour, 0), zone)
        for (p in parts) {
            for (rule in p.entity.rules) {
                for (w in rule.windows) {
                    val m = parseHM(w.start)
                    candidates += epochOf(day, LocalTime.of(m / 60, m % 60), zone)
                }
            }
        }
    }
    for (t in candidates) {
        if (t <= now) continue
        if (!checkParts(parts, t, zone, resetHour, dayKey(t, zone, resetHour) != today).blocked) return t
    }
    return null
}

/**
 * Decide whether a target may be used right now (port of evaluate()).
 * A switched-off target is fully unrestricted (and not counted in its group).
 * A paused target is allowed until its pause ends.
 */
fun evaluate(target: Target?, ctx: EvalContext): Verdict {
    if (target == null || !target.enabled) return Verdict.Allowed()
    val now = ctx.now

    val pauseUntil = ctx.pauses[target.id]?.until
    if (pauseUntil != null && pauseUntil > now) {
        return Verdict.Allowed(blockAt = pauseUntil, blockKind = BlockKind.PAUSE, paused = true)
    }

    val parts = partsFor(target, ctx)
    val v = checkParts(parts, now, ctx.zone, ctx.resetHour, false)
    if (v.blocked) {
        return Verdict.Blocked(v.reason!!, nextOpenAt(parts, now, ctx.zone, ctx.resetHour), v.group)
    }

    var blockAt: Long? = v.remainingSec?.let { now + (it * 1000).roundToLong() }
    var blockKind: BlockKind? = if (blockAt != null) BlockKind.LIMIT else null
    for (p in parts) {
        val rule = activeRule(p.entity, now, ctx.zone, ctx.resetHour)
        val end = if (rule != null && rule.mode.hasSchedule) windowEndAt(rule, now, ctx.zone) else null
        if (end != null && (blockAt == null || end < blockAt)) {
            blockAt = end
            blockKind = BlockKind.SCHEDULE
        }
    }
    return Verdict.Allowed(remainingSec = v.remainingSec, blockAt = blockAt, blockKind = blockKind)
}

/** Today's limit in seconds for a target or group, or null if it has none today. */
fun todayLimitSec(entity: RuleHolder, now: Long, zone: ZoneId, resetHour: Int = 0): Double? {
    val rule = activeRule(entity, now, zone, resetHour)
    return if (rule != null && rule.mode.hasLimit) limitSecondsOf(rule) else null
}
