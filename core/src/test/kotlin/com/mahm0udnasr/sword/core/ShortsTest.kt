package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.loosen.LooseReason
import com.mahm0udnasr.sword.core.loosen.diffState
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.State
import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.rules.BlockReason
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.WindowInfo
import com.mahm0udnasr.sword.core.rules.decide
import com.mahm0udnasr.sword.core.shorts.SHORTS_TARGET_ID
import com.mahm0udnasr.sword.core.shorts.ShortsRules
import com.mahm0udnasr.sword.core.shorts.ShortsSection
import com.mahm0udnasr.sword.core.shorts.defaultShortsTarget
import com.mahm0udnasr.sword.core.shorts.matchInApp
import com.mahm0udnasr.sword.core.shorts.matchWeb
import com.mahm0udnasr.sword.core.shorts.matchWholeApp
import com.mahm0udnasr.sword.core.track.flush
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 12: short videos. Ids are the ones recorded in the spike (S24 Ultra, 2026-11-20). */
class ShortsTest {
    private val yt = "com.google.android.youtube"
    private val ig = "com.instagram.android"
    private val fb = "com.facebook.katana"
    private val rules = ShortsRules(
        listOf(
            ShortsSection("youtube_shorts", packages = listOf(yt), ids = listOf("reel_watch_player", "reel_recycler"), web = listOf("youtube.com/shorts/")),
            ShortsSection("instagram_reels", packages = listOf(ig), ids = listOf("clips_viewer_view_pager"), web = listOf("instagram.com/reels/", "instagram.com/reel/")),
            ShortsSection("facebook_reels", packages = listOf(fb), selectedDesc = listOf("Reels, tab")),
            ShortsSection("tiktok", kind = ShortsSection.WHOLE_APP, packages = listOf("com.zhiliaoapp.musically"), web = listOf("tiktok.com/")),
        ),
    )

    @Test
    fun `in-app player matched by view id`() {
        assertEquals("youtube_shorts", matchInApp(rules, yt, setOf("$yt:id/reel_watch_player", "$yt:id/toolbar"), emptyList())?.id)
        assertEquals("instagram_reels", matchInApp(rules, ig, setOf("$ig:id/clips_viewer_view_pager"), emptyList())?.id)
    }

    @Test
    fun `the normal feed is not matched`() {
        // Home feed ids recorded in the spike: no player ids.
        assertNull(matchInApp(rules, yt, setOf("$yt:id/results", "$yt:id/pivot_bar"), emptyList()))
        assertNull(matchInApp(rules, ig, setOf("$ig:id/feed_tab", "$ig:id/clips_tab"), emptyList()))
    }

    @Test
    fun `an id of another app does not count`() = assertNull(matchInApp(rules, ig, setOf("$yt:id/reel_watch_player"), emptyList()))

    @Test
    fun `apps without view ids - the selected tab`() {
        assertEquals("facebook_reels", matchInApp(rules, fb, emptySet(), listOf("Reels, tab 2 of 6"))?.id)
        assertNull(matchInApp(rules, fb, emptySet(), listOf("Home, tab 1 of 6")))
    }

    @Test
    fun `whole short-video apps`() {
        assertEquals("tiktok", matchWholeApp(rules, "com.zhiliaoapp.musically")?.id)
        assertNull(matchWholeApp(rules, yt))
    }

    @Test
    fun `web - host with subdomains and path prefix`() {
        assertEquals("youtube_shorts", matchWeb(rules, "m.youtube.com", "/shorts/abc")?.id)
        assertEquals("youtube_shorts", matchWeb(rules, "youtube.com", "/SHORTS/abc")?.id)
        assertNull(matchWeb(rules, "youtube.com", "/watch"))
        assertEquals("instagram_reels", matchWeb(rules, "instagram.com", "/reel/xyz/")?.id)
        assertEquals("tiktok", matchWeb(rules, "www.tiktok.com", "/@someone")?.id)
        assertNull(matchWeb(rules, "notyoutube.com", "/shorts/abc"))
    }

    @Test
    fun `path from the URL bar`() {
        assertEquals("/shorts/abc", com.mahm0udnasr.sword.core.rules.pathOfUrlBar("m.youtube.com/shorts/abc?feature=share"))
        assertEquals("/", com.mahm0udnasr.sword.core.rules.pathOfUrlBar("youtube.com"))
        assertEquals("/reel/x/", com.mahm0udnasr.sword.core.rules.pathOfUrlBar("https://www.instagram.com/reel/x/"))
        assertNull(com.mahm0udnasr.sword.core.rules.pathOfUrlBar("search words here"))
    }

    // --- decide()

    private val shorts = defaultShortsTarget(listOf("youtube_shorts", "instagram_reels", "tiktok")).copy(enabled = true, rules = listOf(R(ALL, Mode.BLOCK)))
    private val instagram = makeTarget("ig", "Instagram", packages = listOf(ig)).copy(rules = listOf(R(ALL, Mode.LIMIT, 60)))
    private val cfg = Config(targets = listOf(instagram, shorts))
    private val ctx = EvalContext(T(18, 10), CAIRO, targets = cfg.targets)
    private val reels = WindowInfo(ig, section = "instagram_reels", sectionInApp = true)

    @Test
    fun `short videos blocked - soft (Back) inside an app`() {
        val d = decide(reels, cfg, ctx)!!
        assertEquals(BlockReason.SHORTS, d.reason)
        assertTrue(d.soft)
        assertEquals(SHORTS_TARGET_ID, d.targetId)
    }

    @Test
    fun `the rest of the app stays usable`() = assertNull(decide(WindowInfo(ig), cfg, ctx))

    @Test
    fun `a blocked app stays blocked whole (stricter wins)`() {
        val c = cfg.copy(targets = listOf(instagram.copy(rules = listOf(R(ALL, Mode.BLOCK))), shorts))
        val d = decide(reels, c, ctx.copy(targets = c.targets))!!
        assertEquals(BlockReason.BLOCKED, d.reason)
        assertFalse(d.soft)
    }

    @Test
    fun `whole short-video app - full block`() {
        val d = decide(WindowInfo("com.zhiliaoapp.musically", section = "tiktok"), cfg, ctx)!!
        assertEquals(BlockReason.SHORTS, d.reason)
        assertFalse(d.soft)
    }

    @Test
    fun `a section switched off, or the target off, blocks nothing`() {
        val noReels = cfg.copy(targets = listOf(instagram, shorts.copy(sections = listOf("youtube_shorts"))))
        assertNull(decide(reels, noReels, ctx.copy(targets = noReels.targets)))
        val off = cfg.copy(targets = listOf(instagram, shorts.copy(enabled = false)))
        assertNull(decide(reels, off, ctx.copy(targets = off.targets)))
    }

    @Test
    fun `short videos are pausable`() = assertTrue(BlockReason.SHORTS.pausable)

    // --- flush (Q8: both budgets)

    @Test
    fun `time in Reels counts for short videos and for Instagram`() {
        val c = Config(targets = listOf(instagram, shorts.copy(rules = listOf(R(ALL, Mode.LIMIT, 15)))))
        val t0 = T(18, 10)
        val first = flush(State(), c, SHORTS_TARGET_ID, t0, 1_000, CAIRO, alsoInUse = "ig")
        assertEquals("ig", first.state.session?.alsoTargetId)
        val second = flush(first.state, c, SHORTS_TARGET_ID, t0 + 30_000, 31_000, CAIRO, alsoInUse = "ig")
        val day = second.state.usage.values.single().seconds
        assertEquals(30.0, day[SHORTS_TARGET_ID])
        assertEquals(30.0, day["ig"])
    }

    @Test
    fun `wake up at whichever runs out first`() {
        // Instagram: 1 min left; short videos: 15 min.
        val c = Config(targets = listOf(instagram.copy(rules = listOf(R(ALL, Mode.LIMIT, 1))), shorts.copy(rules = listOf(R(ALL, Mode.LIMIT, 15)))))
        val r = flush(State(), c, SHORTS_TARGET_ID, T(18, 10), 1_000, CAIRO, alsoInUse = "ig")
        assertNotNull(r.blockAt)
        assertEquals(T(18, 10, 1), r.blockAt)
    }

    // --- loosening

    @Test
    fun `switching a section off is looser, on is stricter`() {
        val c = Config(targets = listOf(shorts))
        val off = diffState(c, c.copy(targets = listOf(shorts.copy(sections = listOf("youtube_shorts", "tiktok")))))
        assertTrue(off.single().reasons.contains(LooseReason.SectionRemoved("instagram_reels")))
        val on = diffState(c, c.copy(targets = listOf(shorts.copy(sections = shorts.sections + "facebook_reels"))))
        assertTrue(on.single().reasons.isEmpty())
    }
}
