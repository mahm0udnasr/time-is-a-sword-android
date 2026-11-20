package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.model.DayUsage
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.RuleReason
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.core.rules.dayKey
import com.mahm0udnasr.sword.core.rules.evaluate
import com.mahm0udnasr.sword.core.track.DueWarnings
import com.mahm0udnasr.sword.core.track.PauseResult
import com.mahm0udnasr.sword.core.track.creditTime
import com.mahm0udnasr.sword.core.track.dueWarnings
import com.mahm0udnasr.sword.core.track.pruneUsage
import com.mahm0udnasr.sword.core.track.requestPause
import com.mahm0udnasr.sword.core.track.warningKey
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Ported from core.test.mjs (crediting) and warnings-pause-changes.test.mjs. */
class TrackMathTest {
    @Test
    fun `midnight split`() {
        val u = creditTime(emptyMap(), "x.com", T(18, 23, 59, 40), T(19, 0, 0, 20), CAIRO)
        assertEquals(listOf(20.0, 20.0), listOf(u["2026-11-18"]!!.seconds["x.com"], u["2026-11-19"]!!.seconds["x.com"]))
    }

    @Test
    fun `sleep gap capped`() =
        assertEquals(75.0, creditTime(emptyMap(), "x.com", T(18, 10), T(18, 14), CAIRO)["2026-11-18"]!!.seconds["x.com"])

    @Test
    fun `credit adds to what is there and keeps other fields`() {
        val start = mapOf("2026-11-18" to DayUsage(seconds = mapOf("x.com" to 10.0), pausesUsed = 2))
        val u = creditTime(start, "x.com", T(18, 10), T(18, 10, 0, 30), CAIRO)
        assertEquals(DayUsage(seconds = mapOf("x.com" to 40.0), pausesUsed = 2), u["2026-11-18"])
        assertEquals(10.0, start["2026-11-18"]!!.seconds["x.com"], "input not mutated")
    }

    @Test
    fun `no credit for a backwards span`() = assertEquals(emptyMap(), creditTime(emptyMap(), "x", T(18, 10), T(18, 9), CAIRO))

    @Test
    fun `dueWarnings ahead`() = assertEquals(DueWarnings(emptyList(), 5), dueWarnings(listOf(5, 1), emptyList(), 400.0))

    @Test
    fun `dueWarnings 5`() = assertEquals(DueWarnings(listOf(5), 1), dueWarnings(listOf(5, 1), emptyList(), 290.0))

    @Test
    fun `dueWarnings both when opened late`() = assertEquals(DueWarnings(listOf(5, 1), null), dueWarnings(listOf(5, 1), emptyList(), 30.0))

    @Test
    fun `warning keys - one set per budget, one per window end`() {
        assertEquals("t1", warningKey("t1", BlockKind.LIMIT, T(18, 13, 30), CAIRO))
        assertEquals("t1@13:30", warningKey("t1", BlockKind.SCHEDULE, T(18, 13, 30), CAIRO))
    }

    @Test
    fun `prune keeps 7 logical days`() {
        val usage = (10..18).associate { "2026-11-%02d".format(it) to DayUsage() }
        assertEquals((12..18).map { "2026-11-%02d".format(it) }, pruneUsage(usage, T(18, 10), CAIRO).keys.sorted())
    }

    /**
     * The tracker loop of warnings-pause-changes.test.mjs, in pure form: a
     * 10-minute limit used continuously with a flush every 10 s. Warnings at
     * 5 and 1 minute left, then blocked (reason limit).
     */
    @Test
    fun `10 minute limit - two warnings then blocked`() {
        val yt = site("youtube.com").copy(rules = listOf(R(ALL, Mode.LIMIT, 10)))
        var now = T(18, 10)
        var usage = emptyMap<String, DayUsage>()
        val shown = mutableListOf<Int>()
        var firstWarnAt: Long? = null
        var blockedAt: Long? = null
        repeat(61) {
            val v = evaluate(yt, EvalContext(now, CAIRO, usage = usage))
            if (v is Verdict.Blocked) {
                if (blockedAt == null) blockedAt = now
                assertEquals(RuleReason.LIMIT, v.reason)
                return@repeat
            }
            v as Verdict.Allowed
            val key = dayKey(now, CAIRO)
            val day = usage[key] ?: DayUsage()
            val k = warningKey(yt.id, v.blockKind!!, v.blockAt!!, CAIRO)
            val secondsLeft = (v.blockAt!! - now) / 1000.0
            val w = dueWarnings(Defaults.WARNING_MINUTES, day.warned[k].orEmpty(), secondsLeft)
            if (it == 0) firstWarnAt = v.blockAt!! - w.next!! * 60_000L
            if (w.due.isNotEmpty()) {
                shown += (secondsLeft / 60).roundToInt()
                usage = usage + (key to day.copy(warned = day.warned + (k to day.warned[k].orEmpty() + w.due)))
            }
            usage = creditTime(usage, yt.id, now, now + 10_000, CAIRO)
            now += 10_000
        }
        assertEquals(T(18, 10, 5), firstWarnAt, "first warning scheduled for 5 min before the end")
        assertEquals(listOf(5, 1), shown)
        assertEquals(T(18, 10, 10), blockedAt)
    }

    @Test
    fun `pause - three a day, five minutes each`() {
        val yt = site("youtube.com").copy(rules = listOf(R(ALL, Mode.LIMIT, 10)))
        val now = T(18, 12)
        var usage = emptyMap<String, DayUsage>()
        var pauses = emptyMap<String, com.mahm0udnasr.sword.core.model.Pause>()
        val lefts = mutableListOf<Int>()
        repeat(3) {
            val r = assertIs<PauseResult.Granted>(requestPause(yt, usage, pauses, now, CAIRO))
            assertEquals(now + 5 * 60_000L, r.until)
            lefts += r.left
            usage = r.usage
            pauses = r.pauses
        }
        assertEquals(listOf(2, 1, 0), lefts)
        assertEquals(PauseResult.Refused(PauseResult.Code.NONE_LEFT), requestPause(yt, usage, pauses, now, CAIRO), "fourth pause refused")
        assertEquals(now + 5 * 60_000L, pauses["youtube.com"]?.until, "pause stored")
        assertEquals(PauseResult.Refused(PauseResult.Code.NOT_ACTIVE), requestPause(site("x.com", enabled = false), usage, pauses, now, CAIRO))
    }
}
