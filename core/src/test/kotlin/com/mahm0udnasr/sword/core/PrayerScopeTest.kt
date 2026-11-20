package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.prayer.defaultPrayerAllowlist
import com.mahm0udnasr.sword.core.prayer.prayerAllows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrayerScopeTest {
    private val launchable = setOf("com.instagram.android", "com.game", "com.quran", "com.android.settings")
    private val allow = setOf("com.quran", "com.android.settings")

    @Test
    fun `every launchable app is closed except the allowlist`() {
        assertFalse(prayerAllows("com.instagram.android", allow, launchable))
        assertFalse(prayerAllows("com.game", allow, launchable), "not only targets: every app")
        assertTrue(prayerAllows("com.quran", allow, launchable))
        assertTrue(prayerAllows("com.android.settings", allow, launchable))
    }

    @Test
    fun `apps without a launcher icon stay open (permission dialogs, installer)`() {
        assertTrue(prayerAllows("com.google.android.permissioncontroller", allow, launchable))
    }

    @Test
    fun `defaults never include a target or a browser`() {
        val resolved = setOf("com.samsung.camera", "com.snapchat.android", "com.android.chrome", "com.google.maps")
        assertEquals(
            setOf("com.samsung.camera", "com.google.maps"),
            defaultPrayerAllowlist(resolved, targetPackages = setOf("com.snapchat.android"), browsers = setOf("com.android.chrome")),
        )
    }
}
