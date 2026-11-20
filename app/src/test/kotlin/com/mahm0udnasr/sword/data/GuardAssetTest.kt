package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.guard.GuardRules
import com.mahm0udnasr.sword.core.guard.NodeText
import com.mahm0udnasr.sword.core.guard.guardPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** assets/guard.json against texts recorded on the phone (S24 Ultra, One UI 9, English system language). */
class GuardAssetTest {
    private val labels = listOf("الوقت سيف", "Time Is a Sword")
    private val rules = GuardRules.parse(File("src/main/assets/guard.json").readText()).withLabels(labels)

    @Test
    fun `guard json parses with every page`() {
        assertEquals(listOf("accessibility", "appInfo", "admin", "uninstall", "runningService"), rules.pages.map { it.name })
    }

    @Test
    fun `One UI 9 App info page of this app is protected`() {
        val nodes = listOf(
            NodeText("App info"), NodeText("Time Is a Sword", id = "com.android.settings:id/entity_header_title"), NodeText("Installed"),
            NodeText("Notifications"), NodeText(desc = "Open", id = "com.android.settings:id/open_button"),
            NodeText(desc = "Uninstall", id = "com.android.settings:id/uninstall_button"), NodeText("Uninstall"),
            NodeText(desc = "Force stop", id = "com.android.settings:id/forcestop_button"), NodeText("Force stop"),
        )
        assertEquals("appInfo", guardPage("com.android.settings", nodes, labels, rules))
    }

    @Test
    fun `One UI 9 accessibility list is not protected`() {
        val nodes = listOf(NodeText("Installed apps"), NodeText("NoShorts"), NodeText("Time Is a Sword", id = "android:id/title"))
        assertNull(guardPage("com.android.settings", nodes, labels, rules))
    }

    @Test
    fun `activating the device admin is never blocked`() {
        val nodes = listOf(NodeText("Time Is a Sword"), NodeText("Activate this device admin app"), NodeText("Activate"), NodeText("Cancel"))
        assertNull(guardPage("com.android.settings", nodes, labels, rules))
    }

    @Test
    fun `One UI 9 accessibility page and device admin deactivate page are protected`() {
        val a11y = listOf(NodeText("Time Is a Sword"), NodeText("On"), NodeText("Time Is a Sword shortcut"), NodeText("App info"))
        assertEquals("accessibility", guardPage("com.android.settings", a11y, labels, rules))
        val admin = listOf(
            NodeText("Device admin app"), NodeText("Time Is a Sword", id = "com.android.settings:id/admin_name"),
            NodeText("This admin app is active and allows Time Is a Sword to perform the following actions:"),
            NodeText("Cancel"), NodeText("Deactivate and uninstall"),
        )
        assertEquals("admin", guardPage("com.android.settings", admin, labels, rules))
    }
}
