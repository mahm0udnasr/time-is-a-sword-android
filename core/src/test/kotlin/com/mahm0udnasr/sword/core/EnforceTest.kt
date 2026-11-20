package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Pause
import com.mahm0udnasr.sword.core.model.Protection
import com.mahm0udnasr.sword.core.model.makeGroup
import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.rules.BlockReason
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.Locks
import com.mahm0udnasr.sword.core.rules.WindowInfo
import com.mahm0udnasr.sword.core.rules.decide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnforceTest {
    private val ig = makeTarget("ig", "Instagram", packages = listOf("com.instagram.android"), domains = listOf("instagram.com"))
    private val cfg = Config(targets = listOf(ig))
    private val ctx = EvalContext(T(18, 10), CAIRO)

    private val app = WindowInfo("com.instagram.android")
    private val chromeIg = WindowInfo("com.android.chrome", host = "www.instagram.com".removePrefix("www."), browser = true)

    @Test
    fun `always blocked - app and website`() {
        assertEquals(BlockReason.BLOCKED, decide(app, cfg, ctx)?.reason)
        assertEquals(BlockReason.BLOCKED, decide(chromeIg, cfg, ctx)?.reason)
        assertEquals("ig", decide(chromeIg, cfg, ctx)?.targetId)
    }

    @Test
    fun `other apps and sites are free`() {
        assertNull(decide(WindowInfo("com.whatsapp"), cfg, ctx))
        assertNull(decide(WindowInfo("com.android.chrome", host = "linkedin.com", browser = true), cfg, ctx))
        assertNull(decide(WindowInfo("com.android.chrome", host = null, browser = true), cfg, ctx), "new tab page")
    }

    @Test
    fun `ignored packages are never blocked, whatever the locks`() {
        val dialer = WindowInfo("com.samsung.android.dialer", ignored = true, guarded = true)
        assertNull(decide(dialer, cfg, ctx, Locks(prayerLocked = true, prayerAllows = { false }, clockTrusted = false)))
    }

    @Test
    fun `disabled target is not blocked`() = assertNull(decide(app, cfg.copy(targets = listOf(ig.copy(enabled = false))), ctx))

    @Test
    fun `limit used up - reason limit with reopening time and group name`() {
        val g = makeGroup("g", "Social").copy(rules = listOf(R(ALL, Mode.LIMIT, 2)))
        val t = ig.copy(rules = listOf(R(ALL, Mode.LIMIT, 60)), groupId = "g")
        val c = Config(targets = listOf(t), groups = listOf(g))
        val used = ctx.copy(usage = usageOf("2026-11-18", "ig" to 120.0), targets = c.targets, groups = c.groups)
        val d = decide(app, c, used)!!
        assertEquals(BlockReason.LIMIT, d.reason)
        assertEquals(T(19, 0), d.until)
        assertEquals("Social", d.groupName)
    }

    @Test
    fun `a pause lifts the target's own rule`() =
        assertNull(decide(app, cfg, ctx.copy(pauses = mapOf("ig" to Pause(T(18, 10, 5))))))

    @Test
    fun `unsupported browser blocked only while web rules are active`() {
        val via = WindowInfo("mark.via.gp", browser = false, unsupportedBrowser = true)
        assertEquals(BlockReason.BROWSER, decide(via, cfg, ctx)?.reason)
        assertNull(decide(via, cfg.copy(protection = Protection(blockUnsupportedBrowsers = false)), ctx), "switch off")
        assertNull(decide(via, cfg.copy(targets = listOf(ig.copy(enabled = false))), ctx), "no enabled target")
        assertNull(decide(via, cfg.copy(targets = listOf(ig.copy(domains = emptyList()))), ctx), "no domains")
    }

    @Test
    fun `later phases - guard, prayer and clock locks`() {
        assertEquals(BlockReason.GUARD, decide(WindowInfo("com.android.settings", guarded = true), cfg, ctx)?.reason)
        val prayer = Locks(prayerLocked = true, prayerAllows = { it == "com.quran" })
        assertEquals(BlockReason.PRAYER, decide(WindowInfo("com.whatsapp"), cfg, ctx, prayer)?.reason)
        assertNull(decide(WindowInfo("com.quran"), cfg, ctx, prayer))
        assertEquals(BlockReason.PRAYER, decide(WindowInfo("com.android.chrome", "linkedin.com", browser = true), cfg, ctx, prayer)?.reason, "every website")
        val limited = cfg.copy(targets = listOf(ig.copy(rules = listOf(R(ALL, Mode.LIMIT, 60)))))
        assertEquals(BlockReason.CLOCK, decide(app, limited, ctx, Locks(clockTrusted = false))?.reason)
    }

    @Test
    fun `only the target's own rules can be paused`() {
        assertTrue(BlockReason.LIMIT.pausable)
        assertFalse(BlockReason.PRAYER.pausable)
        assertFalse(BlockReason.BROWSER.pausable)
    }
}
