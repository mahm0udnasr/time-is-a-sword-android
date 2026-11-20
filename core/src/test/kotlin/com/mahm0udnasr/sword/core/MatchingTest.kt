package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.rules.findTargetByHost
import com.mahm0udnasr.sword.core.rules.findTargetByPackage
import com.mahm0udnasr.sword.core.rules.findTargetByUrl
import com.mahm0udnasr.sword.core.rules.hostOf
import com.mahm0udnasr.sword.core.rules.hostOfUrlBar
import com.mahm0udnasr.sword.core.rules.normalizeDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchingTest {
    // --- ported from core.test.mjs
    @Test
    fun `normalize URL`() = assertEquals("youtube.com", normalizeDomain("https://www.YouTube.com/watch?v=1"))

    @Test
    fun `reject non-domain`() = assertNull(normalizeDomain("hello"))

    private val sites = listOf(site("x.com"), site("whatsapp.com"), site("web.whatsapp.com"))

    @Test
    fun `x_com does not match box_com`() = assertNull(findTargetByUrl("https://box.com/", sites))

    @Test
    fun `no suffix trick`() = assertNull(findTargetByUrl("https://x.com.evil.io/", sites))

    @Test
    fun subdomain() = assertEquals("x.com", findTargetByUrl("https://mobile.x.com/a", sites)?.domain)

    @Test
    fun `longest match wins`() = assertEquals("web.whatsapp.com", findTargetByUrl("https://web.whatsapp.com/", sites)?.domain)

    // --- more normalizeDomain / hostOf behaviour of the JS URL parser
    @Test
    fun `normalizeDomain edge cases`() {
        assertEquals("m.facebook.com", normalizeDomain("m.facebook.com."))
        assertEquals("example.com", normalizeDomain("  http://user:pw@Example.com:8080/x?y#z "))
        assertEquals("xn--mgbh0fb.xn--wgbh1c", normalizeDomain("مثال.مصر"))
        assertNull(normalizeDomain("-bad.com"))
        assertNull(normalizeDomain(""))
        assertNull(normalizeDomain(null))
        assertNull(normalizeDomain("has space.com"))
    }

    @Test
    fun `hostOf only http and https`() {
        assertEquals("youtube.com", hostOf("https://www.youtube.com/watch"))
        assertNull(hostOf("chrome://settings"))
        assertNull(hostOf("not a url"))
    }

    // --- Android: URL bars (verified texts from Chrome on the S24 Ultra)
    @Test
    fun `URL bar text without scheme`() {
        assertEquals("m.youtube.com", hostOfUrlBar("m.youtube.com"))
        assertEquals("linkedin.com", hostOfUrlBar("linkedin.com/feed/"))
        assertEquals("instagram.com", hostOfUrlBar("https://www.instagram.com/p/1"))
        assertNull(hostOfUrlBar("search for cats"), "typed search text is not a host")
        assertNull(hostOfUrlBar(""))
    }

    @Test
    fun `apps and websites of one target`() {
        val ig = makeTarget("t1", "Instagram", packages = listOf("com.instagram.android"), domains = listOf("instagram.com", "instagr.am"))
        val all = listOf(ig, site("x.com"))
        assertEquals("t1", findTargetByPackage("com.instagram.android", all)?.id)
        assertEquals("t1", findTargetByHost("instagr.am", all)?.target?.id)
        assertNull(findTargetByPackage("com.whatsapp", all))
    }

    @Test
    fun `an enabled target wins over a disabled one listing the same package`() {
        val off = makeTarget("a", "A", packages = listOf("p"), enabled = false)
        val on = makeTarget("b", "B", packages = listOf("p"))
        assertEquals("b", findTargetByPackage("p", listOf(off, on))?.id)
    }
}
