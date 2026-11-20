package com.mahm0udnasr.sword.core.track

import com.mahm0udnasr.sword.core.model.DayUsage
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.Pause
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.core.rules.dayKey
import com.mahm0udnasr.sword.core.rules.logicalDate
import com.mahm0udnasr.sword.core.rules.nextResetAt
import com.mahm0udnasr.sword.core.rules.zoned
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/*
 * Pure parts of tracker.js: crediting time, warnings, history pruning, pauses.
 * Usage maps are immutable: every function returns a new map.
 */

/**
 * Add the time between [from] and [to] (wall clock, ms) to usage[day].seconds[targetId],
 * splitting across days when the span crosses the daily reset. A span longer
 * than [Defaults.MAX_GAP_MS] is capped (the phone slept).
 */
fun creditTime(
    usage: Map<String, DayUsage>,
    targetId: String,
    from: Long,
    to: Long,
    zone: ZoneId,
    resetHour: Int = 0,
): Map<String, DayUsage> {
    if (to <= from) return usage
    val end = min(to, from + Defaults.MAX_GAP_MS)
    val out = usage.toMutableMap()
    var t = from
    while (t < end) {
        val stop = min(end, nextResetAt(t, zone, resetHour))
        val key = dayKey(t, zone, resetHour)
        val day = out[key] ?: DayUsage()
        val prev = day.seconds[targetId] ?: 0.0
        // Same rounding as the extension: to the millisecond.
        val next = Math.round((prev + (stop - t) / 1000.0) * 1000) / 1000.0
        out[key] = day.copy(seconds = day.seconds + (targetId to next))
        t = stop
    }
    return out
}

/**
 * Which warnings are due now. [thresholds] are minutes (e.g. [5, 1]); [done]
 * the ones already shown for this stretch.
 * @property due thresholds to mark as shown now (secondsLeft has reached them)
 * @property next the largest threshold still ahead, or null
 */
data class DueWarnings(val due: List<Int>, val next: Int?)

fun dueWarnings(thresholds: List<Int>, done: List<Int>, secondsLeft: Double): DueWarnings {
    val pending = thresholds.distinct().filter { it > 0 && it !in done }.sortedDescending()
    val due = pending.filter { secondsLeft <= it * 60 + 1 }
    val ahead = pending.filter { secondsLeft > it * 60 + 1 }
    return DueWarnings(due, ahead.firstOrNull())
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The `warned` bookkeeping key: a window end is a fixed moment, so each window
 * gets its own warnings ("id@HH:MM"); a budget gets one set per day ("id").
 */
fun warningKey(targetId: String, kind: BlockKind, blockAt: Long, zone: ZoneId): String =
    if (kind == BlockKind.SCHEDULE) "$targetId@${zoned(blockAt, zone).format(HM)}" else targetId

/** Keep only the last [Defaults.HISTORY_DAYS] logical days (today included). */
fun pruneUsage(usage: Map<String, DayUsage>, now: Long, zone: ZoneId, resetHour: Int = 0): Map<String, DayUsage> {
    val minKey = logicalDate(now, zone, resetHour).minusDays((Defaults.HISTORY_DAYS - 1).toLong()).toString()
    return usage.filterKeys { it >= minKey }
}

// ---------------------------------------------------------------------------
// Pauses (logic of requestPause(); the friction step happens in the UI first)

sealed interface PauseResult {
    data class Granted(val until: Long, val left: Int, val usage: Map<String, DayUsage>, val pauses: Map<String, Pause>) : PauseResult

    /** Codes, not sentences: the UI shows them in the user's language. */
    data class Refused(val code: Code) : PauseResult

    enum class Code { NOT_ACTIVE, NONE_LEFT }
}

/**
 * Grant a pause for a target: at most [Defaults.PAUSE_MAX_PER_DAY] per logical
 * day, each [Defaults.PAUSE_MAX_MINUTES] minutes (never more than 5).
 */
fun requestPause(
    target: Target?,
    usage: Map<String, DayUsage>,
    pauses: Map<String, Pause>,
    now: Long,
    zone: ZoneId,
    resetHour: Int = 0,
    maxPerDay: Int = Defaults.PAUSE_MAX_PER_DAY,
    maxMinutes: Int = Defaults.PAUSE_MAX_MINUTES,
): PauseResult {
    if (target == null || !target.enabled) return PauseResult.Refused(PauseResult.Code.NOT_ACTIVE)
    val key = dayKey(now, zone, resetHour)
    val day = usage[key] ?: DayUsage()
    if (day.pausesUsed >= maxPerDay) return PauseResult.Refused(PauseResult.Code.NONE_LEFT)
    val minutes = min(5, max(1, maxMinutes))
    val until = now + minutes * 60_000L
    val used = day.pausesUsed + 1
    return PauseResult.Granted(
        until = until,
        left = maxPerDay - used,
        usage = usage + (key to day.copy(pausesUsed = used)),
        pauses = pauses + (target.id to Pause(until)),
    )
}

/** Pauses that have not ended yet. */
fun activePauses(pauses: Map<String, Pause>, now: Long): Map<String, Pause> = pauses.filterValues { it.until > now }
