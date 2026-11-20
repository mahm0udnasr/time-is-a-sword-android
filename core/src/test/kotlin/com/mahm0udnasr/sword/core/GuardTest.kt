package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.guard.GuardPage
import com.mahm0udnasr.sword.core.guard.GuardRules
import com.mahm0udnasr.sword.core.guard.NodeText
import com.mahm0udnasr.sword.core.guard.guardActive
import com.mahm0udnasr.sword.core.guard.guardPage
import com.mahm0udnasr.sword.core.model.Protection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardTest {
    private val rules = GuardRules(
        packages = listOf("com.android.settings"),
        pages = listOf(
            GuardPage("appInfo", equals = listOf("Force stop", "Uninstall", "فرض الإيقاف")),
            GuardPage("admin", equals = listOf("Deactivate this device admin app")),
            GuardPage("ids", ids = listOf("com.android.settings:id/button2")),
        ),
    ).withContains("accessibility", emptyList()).let {
        it.copy(pages = it.pages + GuardPage("accessibility", contains = listOf("uses this service to see which app")))
    }
    private val labels = listOf("Time Is a Sword", "الوقت سيف")

    private fun page(pkg: String = "com.android.settings", vararg n: NodeText) = guardPage(pkg, n.toList(), labels, rules)

    @Test
    fun `our App info page is protected`() {
        assertEquals("appInfo", page(n = arrayOf(NodeText("Time Is a Sword"), NodeText("Open"), NodeText("Uninstall"), NodeText("Force stop"))))
    }

    @Test
    fun `another app's App info page is not`() {
        assertNull(page(n = arrayOf(NodeText("WhatsApp"), NodeText("Uninstall"), NodeText("Force stop"))))
    }

    @Test
    fun `the apps list that only mentions us is not`() {
        assertNull(page(n = arrayOf(NodeText("Apps"), NodeText("Time Is a Sword"), NodeText("WhatsApp"))))
    }

    @Test
    fun `only watched packages`() {
        assertNull(page("com.whatsapp", NodeText("Time Is a Sword"), NodeText("Force stop")))
    }

    @Test
    fun `equals is exact - activating the admin is free, deactivating is protected`() {
        assertNull(page(n = arrayOf(NodeText("Time Is a Sword"), NodeText("Activate this device admin app"))))
        assertEquals("admin", page(n = arrayOf(NodeText("Time Is a Sword"), NodeText("Deactivate this device admin app"))))
    }

    @Test
    fun `contains matches long texts and labels match inside longer text`() {
        val desc = "Time Is a Sword uses this service to see which app is on screen and which website is open"
        assertEquals("accessibility", page(n = arrayOf(NodeText("Use Time Is a Sword"), NodeText(desc))))
    }

    @Test
    fun `case, bidi marks and odd spaces are ignored and content descriptions count`() {
        assertEquals("appInfo", page(n = arrayOf(NodeText(desc = "⁨TIME IS A SWORD⁩"), NodeText("force stop "))))
        assertEquals("appInfo", page(n = arrayOf(NodeText("‏الوقت سيف"), NodeText("فرض  الإيقاف"))))
    }

    @Test
    fun `view ids are markers too`() {
        assertEquals("ids", page(n = arrayOf(NodeText("Time Is a Sword"), NodeText(id = "com.android.settings:id/button2"))))
    }

    @Test
    fun `guard active - switch and the 5-minute suspension`() {
        val on = Protection()
        assertTrue(guardActive(on, null, 1000))
        assertFalse(guardActive(on.copy(guardEnabled = false), null, 1000))
        assertFalse(guardActive(on, 2000, 1000))
        assertTrue(guardActive(on, 2000, 2000))
    }
}
