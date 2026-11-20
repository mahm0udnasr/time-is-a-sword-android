package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.loosen.ConfigOp
import com.mahm0udnasr.sword.core.loosen.LooseItem
import com.mahm0udnasr.sword.core.loosen.LooseReason
import com.mahm0udnasr.sword.core.loosen.RuleWhy
import com.mahm0udnasr.sword.core.loosen.Subject
import com.mahm0udnasr.sword.core.loosen.applyOps
import com.mahm0udnasr.sword.core.loosen.describeLoosening
import com.mahm0udnasr.sword.core.loosen.diffState
import com.mahm0udnasr.sword.core.loosen.rulesLoosening
import com.mahm0udnasr.sword.core.model.Appearance
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Lang
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Protection
import com.mahm0udnasr.sword.core.model.Theme
import com.mahm0udnasr.sword.core.model.makeGroup
import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ported from friction-and-loosening.test.mjs (reasons are typed instead of English text). */
class LooseningTest {
    private fun rule(days: List<Int>, why: RuleWhy) = LooseReason.Rule(days, why)
    private val every = (0..6).toList()

    @Test
    fun `lower limit = stricter`() = assertEquals(emptyList(), rulesLoosening(listOf(R(ALL, Mode.LIMIT, 20)), listOf(R(ALL, Mode.LIMIT, 10))))

    @Test
    fun `raise limit`() = assertEquals(
        listOf(rule(every, RuleWhy.LimitRaised(20, 30))),
        rulesLoosening(listOf(R(ALL, Mode.LIMIT, 20)), listOf(R(ALL, Mode.LIMIT, 30))),
    )

    @Test
    fun unblock() = assertEquals(listOf(rule(every, RuleWhy.Unblocked)), rulesLoosening(listOf(R(ALL, Mode.BLOCK)), listOf(R(ALL, Mode.LIMIT))))

    @Test
    fun `block = stricter`() = assertEquals(emptyList(), rulesLoosening(listOf(R(ALL, Mode.LIMIT)), listOf(R(ALL, Mode.BLOCK))))

    @Test
    fun `fewer days`() = assertEquals(
        listOf(rule(listOf(5, 6), RuleWhy.RuleRemoved)),
        rulesLoosening(listOf(R(ALL, Mode.LIMIT)), listOf(R(setOf(0, 1, 2, 3, 4), Mode.LIMIT))),
    )

    @Test
    fun `new rule = stricter`() = assertEquals(emptyList(), rulesLoosening(emptyList(), listOf(R(ALL, Mode.LIMIT))))

    @Test
    fun `narrower window`() = assertEquals(
        emptyList(),
        rulesLoosening(listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("13:00", "14:00")))), listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("13:15", "13:45"))))),
    )

    @Test
    fun `wider window`() = assertEquals(
        listOf(rule(every, RuleWhy.HoursWidened)),
        rulesLoosening(listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("13:00", "14:00")))), listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("13:00", "14:30"))))),
    )

    @Test
    fun `midnight narrower`() = assertEquals(
        emptyList(),
        rulesLoosening(listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("22:00", "01:00")))), listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("23:00", "00:30"))))),
    )

    @Test
    fun `add limit = stricter`() = assertEquals(
        emptyList(),
        rulesLoosening(listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("13:00", "14:00")))), listOf(R(ALL, Mode.LIMIT_SCHEDULE, 30, listOf(W("13:00", "14:00"))))),
    )

    @Test
    fun `drop limit`() = assertEquals(
        listOf(rule(every, RuleWhy.LimitRemoved)),
        rulesLoosening(listOf(R(ALL, Mode.LIMIT_SCHEDULE, 30, listOf(W("13:00", "14:00")))), listOf(R(ALL, Mode.SCHEDULE, 0, listOf(W("13:00", "14:00"))))),
    )

    // --- diffState on whole configs
    private val g = makeGroup("g1", "Messaging")
    private val yt = site("youtube.com").copy(rules = listOf(R(ALL, Mode.LIMIT, 20)))
    private val wa = site("web.whatsapp.com").copy(rules = emptyList(), groupId = g.id)
    private val off = site("tiktok.com", enabled = false)
    private val cur = Config(targets = listOf(yt, wa, off), groups = listOf(g))

    private fun items(next: Config) = describeLoosening(diffState(cur, next))

    @Test
    fun `site diff reasons`() {
        val next = cur.copy(targets = listOf(yt.copy(rules = listOf(R(setOf(0, 1, 2, 3, 4), Mode.LIMIT, 30))), wa, off))
        assertEquals(
            listOf(
                LooseItem(Subject.OfTarget("youtube.com"), rule(listOf(0, 1, 2, 3, 4), RuleWhy.LimitRaised(20, 30))),
                LooseItem(Subject.OfTarget("youtube.com"), rule(listOf(5, 6), RuleWhy.RuleRemoved)),
            ),
            items(next),
        )
    }

    @Test
    fun `removing a disabled site is not looser`() {
        val ops = diffState(cur, cur.copy(targets = listOf(yt, wa)))
        assertEquals(listOf(ConfigOp.TargetDelete("tiktok.com", "tiktok.com", emptyList())), ops)
    }

    @Test
    fun `removing a group member is looser`() =
        assertEquals(listOf(LooseItem(Subject.OfTarget("web.whatsapp.com"), LooseReason.Removed)), items(cur.copy(targets = listOf(yt, off))))

    @Test
    fun `leaving group`() = assertEquals(
        listOf(LooseItem(Subject.OfTarget("web.whatsapp.com"), LooseReason.LeavesGroup("Messaging"))),
        items(cur.copy(targets = listOf(yt, wa.copy(groupId = null), off))),
    )

    @Test
    fun `switching on is stricter`() =
        assertEquals(emptyList(), diffState(cur, cur.copy(targets = listOf(yt, wa, off.copy(enabled = true))))[0].reasons)

    @Test
    fun `group off`() = assertEquals(
        listOf(LooseItem(Subject.OfGroup("Messaging"), LooseReason.SwitchedOff)),
        items(cur.copy(groups = listOf(g.copy(enabled = false)))),
    )

    @Test
    fun `adding a site`() {
        val added = site("reddit.com")
        val ops = diffState(cur, cur.copy(targets = cur.targets + added))
        assertEquals(listOf(1, 0, 4), listOf(ops.size, ops[0].reasons.size, applyOps(cur, ops).targets.size))
    }

    @Test
    fun `raising the limit is looser and applies immediately`() {
        val next = cur.copy(targets = listOf(yt.copy(rules = listOf(R(ALL, Mode.LIMIT, 60))), wa, off))
        val ops = diffState(cur, next)
        assertTrue(ops[0].isLooser)
        assertEquals(next, applyOps(cur, ops))
    }

    @Test
    fun `apply ops round-trips a mixed change`() {
        val next = cur.copy(
            targets = listOf(wa.copy(name = "WhatsApp Web"), site("reddit.com")),
            groups = emptyList(),
            appearance = Appearance(Theme.DARK, Lang.EN),
        )
        assertEquals(next, applyOps(cur, diffState(cur, next)))
    }

    // --- Android extensions (plan §8.5)
    private val ig = makeTarget("ig", "Instagram", packages = listOf("com.instagram.android", "com.instagram.lite"), domains = listOf("instagram.com"))

    @Test
    fun `removing an app or a domain from an enabled target is looser, adding is stricter`() {
        val c = Config(targets = listOf(ig))
        val fewer = Config(targets = listOf(ig.copy(packages = listOf("com.instagram.android"), domains = emptyList())))
        assertEquals(
            listOf(LooseReason.PackageRemoved("com.instagram.lite"), LooseReason.DomainRemoved("instagram.com")),
            diffState(c, fewer).single().reasons,
        )
        val more = Config(targets = listOf(ig.copy(domains = listOf("instagram.com", "instagr.am"))))
        assertEquals(emptyList(), diffState(c, more).single().reasons)
        assertEquals(emptyList(), diffState(c, Config(targets = listOf(ig.copy(name = "IG")))).single().reasons, "rename is neutral")
    }

    @Test
    fun `protection switches off are looser, on is stricter`() {
        val c = Config()
        val loose = c.copy(protection = Protection(guardEnabled = false, blockUnsupportedBrowsers = false, requireAutoTime = false, prayerAllowlist = listOf("com.quran")))
        assertEquals(
            listOf(LooseReason.GuardOff, LooseReason.UnsupportedBrowsersAllowed, LooseReason.AutoTimeNotRequired),
            diffState(c, loose).single().reasons,
        )
        assertEquals(emptyList(), diffState(loose, c).single().reasons)
    }

    @Test
    fun `allowing an app during prayer is looser only while Salah First is on`() {
        val off = Config()
        val allow = { c: Config -> c.copy(protection = c.protection.copy(prayerAllowlist = listOf("com.quran"))) }
        assertEquals(emptyList(), diffState(off, allow(off)).single().reasons, "setting up while off is free")
        val on = Config(prayer = PrayerSettings(enabled = true, address = "Cairo"))
        assertEquals(listOf(LooseReason.PrayerAllowlistAdded("com.quran")), diffState(on, allow(on)).single().reasons)
        assertEquals(emptyList(), diffState(allow(on), on).single().reasons, "removing is stricter")
    }

    @Test
    fun `appearance never needs friction`() {
        val ops = diffState(Config(), Config(appearance = Appearance(Theme.DARK, Lang.EN)))
        assertEquals(listOf(ConfigOp.AppearancePut(Appearance(Theme.DARK, Lang.EN))), ops)
        assertTrue(ops.none { it.isLooser })
    }
}
