package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.loosen.ConfigOp
import com.mahm0udnasr.sword.core.loosen.LooseReason
import com.mahm0udnasr.sword.core.loosen.RuleWhy
import com.mahm0udnasr.sword.core.loosen.diffState
import com.mahm0udnasr.sword.core.loosen.rulesLoosening
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.DayUsage
import com.mahm0udnasr.sword.core.model.Group
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Pause
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.core.model.TimeWindow
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerDay
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.PrayerToggle
import com.mahm0udnasr.sword.core.prayer.cfgKey
import com.mahm0udnasr.sword.core.prayer.lockWindows
import com.mahm0udnasr.sword.core.prayer.normalizePrayer
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.core.rules.evaluate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plan step 1.11: the Kotlin port gives the same answers as the extension's
 * JavaScript on seeded random scenarios. Fixture made by
 * tools/parity/gen-parity.mjs (TZ=Africa/Cairo) from social-lock-ext.
 */
class ParityTest {
    @Serializable data class JsWindow(val start: String, val end: String)
    @Serializable data class JsRule(val days: List<Int>, val mode: String, val limitMinutes: Int, val windows: List<JsWindow> = emptyList())
    @Serializable data class JsSite(val id: String, val domain: String, val enabled: Boolean, val groupId: String? = null, val rules: List<JsRule>)
    @Serializable data class JsGroup(val id: String, val name: String, val enabled: Boolean, val rules: List<JsRule>)
    @Serializable data class JsDay(val seconds: Map<String, Double> = emptyMap())
    @Serializable data class JsPause(val until: Long)
    @Serializable data class EvalIn(
        val now: Long, val resetHour: Int, val sites: List<JsSite>, val groups: List<JsGroup>,
        val usage: Map<String, JsDay>, val pauses: Map<String, JsPause>, val siteId: String,
    )
    @Serializable data class EvalOut(
        val blocked: Boolean, val reason: String? = null, val until: Long? = null, val group: String? = null,
        val remainingSec: Double? = null, val blockAt: Long? = null, val blockKind: String? = null, val paused: Boolean = false,
    )
    @Serializable data class EvalCase(val input: EvalIn, val out: EvalOut)
    @Serializable data class World(val sites: List<JsSite>, val groups: List<JsGroup>)
    @Serializable data class JsOp(val kind: String, val op: String, val id: String, val reasons: Int)
    @Serializable data class DiffCase(val cur: World, val next: World, val ops: List<JsOp>)
    @Serializable data class LoosenCase(val prev: List<JsRule>, val next: List<JsRule>, val reasons: List<String>)
    @Serializable data class JsToggle(val on: Boolean, val adjust: Int)
    @Serializable data class JsPrayer(val enabled: Boolean, val address: String, val method: Int, val before: Int, val after: Int, val prayers: Map<String, JsToggle>)
    @Serializable data class LockCase(val prayer: JsPrayer, val windows: List<List<kotlinx.serialization.json.JsonPrimitive>>)
    @Serializable data class Fixture(
        val evaluate: List<EvalCase>, val diff: List<DiffCase>, val loosen: List<LoosenCase>,
        val lock: List<LockCase>, val timings: Map<String, String>,
    )

    private val fx: Fixture = Json { ignoreUnknownKeys = true }
        .decodeFromString(javaClass.getResource("/parity.json")!!.readText())

    private fun mode(s: String) = when (s) {
        "block" -> Mode.BLOCK
        "limit" -> Mode.LIMIT
        "schedule" -> Mode.SCHEDULE
        "limit+schedule" -> Mode.LIMIT_SCHEDULE
        else -> error(s)
    }

    private fun rule(r: JsRule) = Rule(r.days.toSet(), mode(r.mode), r.limitMinutes, r.windows.map { TimeWindow(it.start, it.end) })
    private fun target(s: JsSite) = Target(s.id, s.domain, s.enabled, s.groupId, domains = listOf(s.domain), rules = s.rules.map(::rule))
    private fun group(g: JsGroup) = Group(g.id, g.name, g.enabled, g.rules.map(::rule))
    private fun config(w: World) = Config(targets = w.sites.map(::target), groups = w.groups.map(::group))

    private fun near(a: Double?, b: Double?) = (a == null && b == null) || (a != null && b != null && abs(a - b) < 1e-6)

    @Test
    fun `evaluate matches the extension`() {
        val bad = mutableListOf<String>()
        fx.evaluate.forEachIndexed { i, c ->
            val inp = c.input
            val targets = inp.sites.map(::target)
            val ctx = EvalContext(
                now = inp.now, zone = CAIRO, resetHour = inp.resetHour,
                usage = inp.usage.mapValues { DayUsage(seconds = it.value.seconds) },
                targets = targets, groups = inp.groups.map(::group),
                pauses = inp.pauses.mapValues { Pause(it.value.until) },
            )
            val v = evaluate(targets.first { it.id == inp.siteId }, ctx)
            val ok = when (v) {
                is Verdict.Blocked -> c.out.blocked && c.out.reason == v.reason.name.lowercase() && c.out.until == v.until && c.out.group == v.group?.name
                is Verdict.Allowed -> !c.out.blocked && near(c.out.remainingSec, v.remainingSec) && c.out.blockAt == v.blockAt &&
                    c.out.blockKind == v.blockKind?.name?.lowercase() && c.out.paused == v.paused
            }
            if (!ok) bad += "#$i js=${c.out} kt=$v"
        }
        println("evaluate parity: ${fx.evaluate.size - bad.size}/${fx.evaluate.size}")
        assertTrue(bad.isEmpty(), bad.take(10).joinToString("\n"))
    }

    @Test
    fun `diffState matches the extension`() {
        val bad = mutableListOf<String>()
        fx.diff.forEachIndexed { i, c ->
            val kt = diffState(config(c.cur), config(c.next)).map {
                when (it) {
                    is ConfigOp.TargetPut -> JsOp("site", "put", it.value.id, it.reasons.size)
                    is ConfigOp.TargetDelete -> JsOp("site", "delete", it.id, it.reasons.size)
                    is ConfigOp.GroupPut -> JsOp("group", "put", it.value.id, it.reasons.size)
                    is ConfigOp.GroupDelete -> JsOp("group", "delete", it.id, it.reasons.size)
                    else -> JsOp("other", "", "", it.reasons.size)
                }
            }
            if (kt != c.ops) bad += "#$i js=${c.ops} kt=$kt"
        }
        println("diffState parity: ${fx.diff.size - bad.size}/${fx.diff.size}")
        assertTrue(bad.isEmpty(), bad.take(10).joinToString("\n"))
    }

    // English text exactly like the extension (format.js describeDays + i18n lo.*), only for comparing.
    private val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private fun describeDays(days: List<Int>): String {
        val sorted = days.toSortedSet().toList()
        if (sorted.size == 7) return "Every day"
        if (sorted.isEmpty()) return "No days"
        val runs = mutableListOf<IntArray>()
        for (d in sorted) {
            val last = runs.lastOrNull()
            if (last != null && d == last[1] + 1) last[1] = d else runs += intArrayOf(d, d)
        }
        return runs.joinToString(", ") { (a, b) ->
            when {
                b - a >= 2 -> "${dayNames[a]}–${dayNames[b]}"
                a == b -> dayNames[a]
                else -> "${dayNames[a]}, ${dayNames[b]}"
            }
        }
    }

    private fun english(r: LooseReason): String {
        r as LooseReason.Rule
        val why = when (val w = r.why) {
            RuleWhy.RuleRemoved -> "rule removed"
            RuleWhy.Unblocked -> "no longer always blocked"
            RuleWhy.LimitRemoved -> "daily limit removed"
            is RuleWhy.LimitRaised -> "limit raised from ${w.a} to ${w.b} min"
            RuleWhy.HoursRemoved -> "allowed hours removed"
            RuleWhy.HoursWidened -> "allowed hours widened"
        }
        return "${describeDays(r.days)}: $why"
    }

    @Test
    fun `rulesLoosening matches the extension`() {
        val bad = mutableListOf<String>()
        fx.loosen.forEachIndexed { i, c ->
            val kt = rulesLoosening(c.prev.map(::rule), c.next.map(::rule)).map(::english)
            if (kt != c.reasons) bad += "#$i js=${c.reasons} kt=$kt"
        }
        println("rulesLoosening parity: ${fx.loosen.size - bad.size}/${fx.loosen.size}")
        assertTrue(bad.isEmpty(), bad.take(10).joinToString("\n"))
    }

    @Test
    fun `lockWindows matches the extension`() {
        val bad = mutableListOf<String>()
        fx.lock.forEachIndexed { i, c ->
            val raw = c.prayer
            val p = normalizePrayer(
                PrayerSettings(
                    enabled = raw.enabled, address = raw.address, method = raw.method, before = raw.before, after = raw.after,
                    prayers = raw.prayers.entries.associate { (k, v) -> Prayer.valueOf(k.uppercase()) to PrayerToggle(v.on, v.adjust) },
                ),
            )
            val days = listOf(19, 20).map { d ->
                val date = LocalDate.of(2026, 11, d)
                PrayerDay(
                    date, cfgKey(p),
                    fx.timings.entries.associate { (k, hm) ->
                        Prayer.valueOf(k.uppercase()) to ZonedDateTime.of(date, LocalTime.parse(hm), CAIRO).toInstant().toEpochMilli()
                    },
                    CAIRO.id,
                )
            }
            val kt = lockWindows(p, days).map { listOf(it.prayer.name.lowercase(), it.start.toString(), it.end.toString()) }
            val js = c.windows.map { w -> w.map { it.content } }
            if (kt != js) bad += "#$i js=$js kt=$kt"
        }
        println("lockWindows parity: ${fx.lock.size - bad.size}/${fx.lock.size}")
        assertTrue(bad.isEmpty(), bad.take(5).joinToString("\n"))
    }
}
