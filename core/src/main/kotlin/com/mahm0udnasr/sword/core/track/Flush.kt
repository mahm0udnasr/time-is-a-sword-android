package com.mahm0udnasr.sword.core.track

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.DayUsage
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.Session
import com.mahm0udnasr.sword.core.model.State
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.core.rules.dayKey
import com.mahm0udnasr.sword.core.rules.evaluate
import java.time.ZoneId
import kotlin.math.min

/** A "N minutes left" warning to show now. */
data class Warning(val targetId: String, val kind: BlockKind, val blockAt: Long, val secondsLeft: Double)

/**
 * @property state the new state to store
 * @property verdict the verdict of the target in use now (null if none)
 * @property warning a warning that became due now
 * @property blockAt when the tracked target gets blocked if used on (wake up then)
 * @property nextWarnAt when the next warning is due (wake up then)
 * @property ranOut the target that was being tracked and is now blocked (re-check every window of it)
 */
data class FlushResult(
    val state: State,
    val verdict: Verdict?,
    val warning: Warning?,
    val blockAt: Long?,
    val nextWarnAt: Long?,
    val ranOut: String?,
)

/**
 * One flush (port of doRefresh() in tracker.js, step 3.5):
 *  1. credit the time since the last flush to the session's target,
 *  2. decide afresh what is being tracked,
 *  3. work out warnings and the next moments to wake up.
 *
 * Durations come from the monotonic clock ([elapsedNow], SystemClock.elapsedRealtime),
 * so changing the date never adds or removes time; the wall clock ([wallNow])
 * only decides which day the time goes to (D12). A gap longer than
 * [Defaults.MAX_GAP_MS] is capped (the phone slept or the process was frozen).
 *
 * @param inUse the enabled target in use right now (foreground + screen on and
 *   unlocked), or null. Only one session exists, so nothing is counted twice.
 * @param discardSession drop an old session without crediting it (process start, boot).
 * @param alsoInUse short videos inside an app: the app's own target, credited too (Q8).
 */
fun flush(
    state: State,
    config: Config,
    inUse: String?,
    wallNow: Long,
    elapsedNow: Long,
    zone: ZoneId,
    discardSession: Boolean = false,
    resetHour: Int = Defaults.RESET_HOUR,
    warningMinutes: List<Int> = Defaults.WARNING_MINUTES,
    alsoInUse: String? = null,
): FlushResult {
    val old = if (discardSession) null else state.session

    // 1. Credit the time since the last flush.
    var usage = state.usage
    if (old != null) {
        val delta = min(elapsedNow - old.lastFlushElapsed, Defaults.MAX_GAP_MS)
        if (delta > 0) {
            usage = creditTime(usage, old.targetId, wallNow - delta, wallNow, zone, resetHour)
            old.alsoTargetId?.takeIf { it != old.targetId }?.let { usage = creditTime(usage, it, wallNow - delta, wallNow, zone, resetHour) }
        }
    }
    usage = pruneUsage(usage, wallNow, zone, resetHour)
    val pauses = activePauses(state.pauses, wallNow)

    // 2. Decide what to track from now on. A target that is not allowed is not
    //    tracked (it is about to be blocked anyway).
    val ctx = EvalContext(wallNow, zone, resetHour, usage, config.targets, config.groups, pauses)
    val target = inUse?.let { id -> config.targets.firstOrNull { it.id == id && it.enabled } }
    val verdict = target?.let { evaluate(it, ctx) }
    val also = alsoInUse?.takeIf { it != inUse }?.let { id -> config.targets.firstOrNull { it.id == id && it.enabled } }
    val alsoVerdict = also?.let { evaluate(it, ctx) }
    val session = if (target != null && verdict is Verdict.Allowed && alsoVerdict !is Verdict.Blocked) {
        Session(
            targetId = target.id,
            alsoTargetId = also?.id,
            since = if (old?.targetId == target.id) old.since else wallNow,
            lastFlushWall = wallNow,
            lastFlushElapsed = elapsedNow,
        )
    } else {
        null
    }

    // 3. "N minutes left" warnings for the target in use.
    var warning: Warning? = null
    var nextWarnAt: Long? = null
    val allowed = verdict as? Verdict.Allowed
    if (session != null && allowed?.blockAt != null && allowed.blockKind != null && allowed.blockKind != BlockKind.PAUSE) {
        val blockAt = allowed.blockAt
        val secondsLeft = (blockAt - wallNow) / 1000.0
        val key = warningKey(session.targetId, allowed.blockKind, blockAt, zone)
        val dayK = dayKey(wallNow, zone, resetHour)
        val day = usage[dayK] ?: DayUsage()
        val done = day.warned[key].orEmpty()
        val w = dueWarnings(warningMinutes, done, secondsLeft)
        if (w.due.isNotEmpty()) {
            usage = usage + (dayK to day.copy(warned = day.warned + (key to done + w.due)))
            warning = Warning(session.targetId, allowed.blockKind, blockAt, secondsLeft)
        }
        if (w.next != null) nextWarnAt = blockAt - w.next * 60_000L
    }

    // 4. Did the target we were just on run out (its own budget or its group's)?
    val ranOut = listOfNotNull(old?.targetId, old?.alsoTargetId).firstOrNull { id ->
        val t = config.targets.firstOrNull { it.id == id }
        t != null && evaluate(t, ctx).blocked
    }
    // Wake up at whichever runs out first: the short videos or the app they are in.
    val alsoBlockAt = (alsoVerdict as? Verdict.Allowed)?.blockAt

    return FlushResult(
        state = state.copy(usage = usage, session = session, pauses = pauses),
        verdict = verdict,
        warning = warning,
        blockAt = if (session != null) listOfNotNull(allowed?.blockAt, alsoBlockAt).minOrNull() else null,
        nextWarnAt = nextWarnAt,
        ranOut = ranOut,
    )
}
