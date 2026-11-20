package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Pause
import com.mahm0udnasr.sword.core.model.makeGroup
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.RuleReason
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.core.rules.evaluate
import com.mahm0udnasr.sword.core.rules.inWindow
import com.mahm0udnasr.sword.core.rules.parseHM
import com.mahm0udnasr.sword.core.rules.todayLimitSec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ported from core.test.mjs (limits, schedules, days, groups) and the pause part of friction-and-loosening.test.mjs. */
class EvaluateTest {
    private fun ctx(now: Long, usage: Map<String, com.mahm0udnasr.sword.core.model.DayUsage> = emptyMap()) =
        EvalContext(now = now, zone = CAIRO, usage = usage)

    private val yt = site("youtube.com").copy(rules = listOf(R(ALL, Mode.LIMIT, 20)))

    @Test
    fun `limit remaining`() {
        val v = assertIs<Verdict.Allowed>(evaluate(yt, ctx(T(18, 12), usageOf("2026-11-18", "youtube.com" to 1190.0))))
        assertEquals(10.0, v.remainingSec)
        assertEquals(T(18, 12, 0, 10), v.blockAt)
        assertEquals(BlockKind.LIMIT, v.blockKind)
    }

    @Test
    fun `limit hit - next reset`() {
        val v = assertIs<Verdict.Blocked>(evaluate(yt, ctx(T(18, 12), usageOf("2026-11-18", "youtube.com" to 1200.0))))
        assertEquals(RuleReason.LIMIT, v.reason)
        assertEquals(T(19, 0), v.until)
    }

    private val wa = site("web.whatsapp.com").copy(
        rules = listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("13:00", "13:30"), W("21:00", "21:30")))),
    )

    @Test
    fun `window end`() =
        assertEquals(T(18, 13, 30), assertIs<Verdict.Allowed>(evaluate(wa, ctx(T(18, 13, 10)))).blockAt)

    @Test
    fun `next window`() = assertEquals(T(18, 21), assertIs<Verdict.Blocked>(evaluate(wa, ctx(T(18, 14)))).until)

    @Test
    fun `window across midnight`() = assertTrue(inWindow(parseHM("00:30").toDouble(), W("22:00", "01:00")))

    @Test
    fun `combined - spent means tomorrow's window`() {
        val combined = wa.copy(rules = listOf(wa.rules[0].copy(mode = Mode.LIMIT_SCHEDULE, limitMinutes = 20)))
        val v = evaluate(combined, ctx(T(18, 13, 10), usageOf("2026-11-18", "web.whatsapp.com" to 1200.0)))
        assertEquals(T(19, 13), assertIs<Verdict.Blocked>(v).until)
    }

    @Test
    fun `weekday block until Friday`() {
        val days = site("reddit.com").copy(rules = listOf(R(setOf(0, 1, 2, 3, 4), Mode.BLOCK, 0), R(setOf(5, 6), Mode.LIMIT, 45)))
        assertEquals(T(20, 0), assertIs<Verdict.Blocked>(evaluate(days, ctx(T(18, 10)))).until)
    }

    @Test
    fun `shared group budget`() {
        val g = makeGroup("g1", "Messaging")
        val m1 = site("web.whatsapp.com").copy(rules = emptyList(), groupId = g.id)
        val m2 = site("web.telegram.org").copy(rules = emptyList(), groupId = g.id)
        val usage = usageOf("2026-11-18", "web.whatsapp.com" to 1000.0, "web.telegram.org" to 800.0)
        val v = assertIs<Verdict.Blocked>(evaluate(m2, EvalContext(T(18, 10), CAIRO, usage = usage, targets = listOf(m1, m2), groups = listOf(g))))
        assertEquals(RuleReason.LIMIT, v.reason)
        assertEquals("Messaging", v.group?.name)
    }

    @Test
    fun `a switched-off group member does not count`() {
        val g = makeGroup("g1", "Messaging")
        val m1 = site("web.whatsapp.com", enabled = false).copy(rules = emptyList(), groupId = g.id)
        val m2 = site("web.telegram.org").copy(rules = emptyList(), groupId = g.id)
        val usage = usageOf("2026-11-18", "web.whatsapp.com" to 1000.0, "web.telegram.org" to 800.0)
        val v = evaluate(m2, EvalContext(T(18, 10), CAIRO, usage = usage, targets = listOf(m1, m2), groups = listOf(g)))
        assertEquals(1000.0, assertIs<Verdict.Allowed>(v).remainingSec)
    }

    @Test
    fun `disabled target is unrestricted`() =
        assertEquals(Verdict.Allowed(), evaluate(site("x.com", enabled = false), ctx(T(18, 10))))

    @Test
    fun `no rule today means unrestricted`() {
        val weekend = site("x.com").copy(rules = listOf(R(setOf(5, 6), Mode.BLOCK)))
        assertEquals(Verdict.Allowed(), evaluate(weekend, ctx(T(18, 10))))
    }

    @Test
    fun `blocked every day - until is null`() =
        assertEquals(null, assertIs<Verdict.Blocked>(evaluate(site("x.com"), ctx(T(18, 10)))).until)

    @Test
    fun `window ending at midnight`() {
        val night = site("x.com").copy(rules = listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("21:00", "00:00")))))
        assertEquals(T(19, 0), assertIs<Verdict.Allowed>(evaluate(night, ctx(T(18, 22)))).blockAt)
    }

    @Test
    fun `todayLimitSec`() {
        assertEquals(1200.0, todayLimitSec(yt, T(18, 10), CAIRO))
        assertEquals(null, todayLimitSec(site("x.com"), T(18, 10), CAIRO))
    }

    // --- pause (friction-and-loosening.test.mjs)
    private val blocked = site("x.com")

    @Test
    fun `blocked before pause`() = assertTrue(evaluate(blocked, ctx(T(18, 10))).blocked)

    @Test
    fun paused() {
        val now = T(18, 10)
        val v = evaluate(blocked, ctx(now).copy(pauses = mapOf("x.com" to Pause(now + 300_000))))
        assertEquals(Verdict.Allowed(blockAt = now + 300_000, blockKind = BlockKind.PAUSE, paused = true), v)
    }

    @Test
    fun `expired pause`() {
        val now = T(18, 10)
        assertTrue(evaluate(blocked, ctx(now).copy(pauses = mapOf("x.com" to Pause(now - 1)))).blocked)
    }
}
