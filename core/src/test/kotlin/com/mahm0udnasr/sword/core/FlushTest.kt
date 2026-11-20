package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Pause
import com.mahm0udnasr.sword.core.model.Session
import com.mahm0udnasr.sword.core.model.State
import com.mahm0udnasr.sword.core.model.makeGroup
import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.core.track.flush
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The tracker's flush (port of doRefresh()). Replaces the extension's
 * heartbeat tests: on a phone "in use" = foreground + screen on and unlocked,
 * which the service turns into [inUse].
 */
class FlushTest {
    private val ig = makeTarget("ig", "Instagram", packages = listOf("com.instagram.android"), domains = listOf("instagram.com"), mode = Mode.LIMIT)
    private val yt = makeTarget("yt", "YouTube", packages = listOf("com.google.android.youtube"), mode = Mode.LIMIT)
    private val config = Config(targets = listOf(ig, yt))

    /** Simulated phone: wall clock + elapsedRealtime, flushing every [step] ms. */
    private inner class Phone(var wall: Long = T(18, 10), var elapsed: Long = 1_000_000) {
        var state = State()
        var cfg = config
        fun flushNow(inUse: String?, discard: Boolean = false) =
            flush(state, cfg, inUse, wall, elapsed, CAIRO, discardSession = discard).also { state = it.state }
        fun advance(ms: Long) { wall += ms; elapsed += ms }
        fun use(id: String?, ms: Long, step: Long = 30_000) {
            var left = ms
            flushNow(id)
            while (left > 0) {
                val s = minOf(step, left)
                advance(s); left -= s
                flushNow(id)
            }
        }
        fun seconds(id: String) = state.usage["2026-11-18"]?.seconds?.get(id) ?: 0.0
    }

    @Test
    fun `3 minutes of use adds 180 s`() {
        val p = Phone()
        p.use("ig", 180_000)
        assertEquals(180.0, p.seconds("ig"))
    }

    @Test
    fun `screen off (nothing in use) stops counting`() {
        val p = Phone()
        p.use("ig", 60_000)
        p.use(null, 600_000) // screen off for 10 min
        p.use("ig", 60_000)
        assertEquals(120.0, p.seconds("ig"))
    }

    @Test
    fun `switching targets credits each once, never twice`() {
        val p = Phone()
        p.use("ig", 90_000)
        p.use("yt", 45_000, step = 15_000)
        p.use("ig", 30_000)
        assertEquals(120.0, p.seconds("ig"))
        assertEquals(45.0, p.seconds("yt"))
    }

    @Test
    fun `a long gap between flushes is capped at 75 s`() {
        val p = Phone()
        p.flushNow("ig")
        p.advance(30 * 60_000) // service frozen for 30 min
        p.flushNow("ig")
        assertEquals(75.0, p.seconds("ig"))
    }

    @Test
    fun `moving the date forward adds no time and starts no new budget early`() {
        val p = Phone()
        p.use("ig", 60_000)
        p.wall += 3 * 3_600_000L // user moves the clock 3 h forward
        p.advance(30_000)
        p.flushNow("ig")
        assertEquals(90.0, p.seconds("ig"), "30 real seconds credited, not 3 h")
    }

    @Test
    fun `process restart or boot - the old session is discarded, not credited`() {
        val p = Phone()
        p.use("ig", 60_000)
        p.advance(40_000)
        p.elapsed = 5_000 // reboot: elapsedRealtime starts again
        val r = p.flushNow("ig", discard = true)
        assertEquals(60.0, p.seconds("ig"))
        assertEquals(Session("ig", p.wall, p.wall, 5_000), r.state.session)
    }

    @Test
    fun `elapsed going backwards without discard credits nothing`() {
        val p = Phone()
        p.use("ig", 30_000)
        p.elapsed -= 10_000_000
        p.wall += 10_000
        p.flushNow("ig")
        assertEquals(30.0, p.seconds("ig"))
    }

    @Test
    fun `session keeps its start while the same target stays in use`() {
        val p = Phone()
        p.use("ig", 90_000)
        assertEquals(T(18, 10), p.state.session?.since)
        p.use("yt", 30_000)
        assertEquals(T(18, 10, 1, 30), p.state.session?.since)
    }

    @Test
    fun `disabled or unknown targets are never tracked`() {
        val p = Phone()
        p.cfg = config.copy(targets = listOf(ig.copy(enabled = false), yt))
        p.use("ig", 60_000)
        p.use("nope", 60_000)
        assertEquals(0.0, p.seconds("ig"))
        assertNull(p.state.session)
    }

    @Test
    fun `20 min limit - warnings at 5 and 1, then ran out`() {
        val p = Phone()
        val shown = mutableListOf<Int>()
        var ranOut: String? = null
        var firstNextWarn: Long? = null
        val start = p.wall
        repeat(130) {
            val r = p.flushNow("ig")
            if (it == 0) firstNextWarn = r.nextWarnAt
            r.warning?.let { w -> shown += (w.secondsLeft / 60).roundToInt() }
            if (r.ranOut != null) ranOut = r.ranOut
            p.advance(10_000)
        }
        assertEquals(start + 15 * 60_000L, firstNextWarn, "first wake-up: 5 min before the end")
        assertEquals(listOf(5, 1), shown)
        assertEquals("ig", ranOut)
        assertIs<Verdict.Blocked>(p.flushNow("ig").verdict)
        assertNull(p.state.session, "a blocked target is not tracked")
        assertEquals(1200.0, p.seconds("ig"))
    }

    @Test
    fun `blockAt is reported for the timer`() {
        val p = Phone()
        val r = p.flushNow("ig")
        assertEquals(p.wall + 20 * 60_000L, r.blockAt)
        assertNull(p.flushNow(null).blockAt)
    }

    @Test
    fun `group budget runs out for the whole group`() {
        val g = makeGroup("g", "Chat").copy(rules = listOf(R(ALL, Mode.LIMIT, 2)))
        val wa = makeTarget("wa", "WhatsApp", mode = Mode.LIMIT).copy(rules = emptyList(), groupId = "g")
        val tg = makeTarget("tg", "Telegram", mode = Mode.LIMIT).copy(rules = emptyList(), groupId = "g")
        val p = Phone()
        p.cfg = Config(targets = listOf(wa, tg), groups = listOf(g))
        p.use("wa", 60_000)
        p.use("tg", 60_000)
        assertIs<Verdict.Blocked>(p.flushNow("wa").verdict)
        assertIs<Verdict.Blocked>(p.flushNow("tg").verdict)
    }

    @Test
    fun `pause - time still counts, no warnings, ended pauses dropped`() {
        val p = Phone()
        p.cfg = config.copy(targets = listOf(ig.copy(rules = listOf(R(ALL, Mode.BLOCK))), yt))
        p.state = p.state.copy(pauses = mapOf("ig" to Pause(p.wall + 60_000)))
        val r = p.flushNow("ig")
        assertEquals(BlockKind.PAUSE, (r.verdict as Verdict.Allowed).blockKind)
        assertNull(r.warning)
        assertNotNull(r.blockAt)
        p.use("ig", 60_000)
        assertEquals(60.0, p.seconds("ig"))
        assertEquals(emptyMap(), p.state.pauses)
    }
}
