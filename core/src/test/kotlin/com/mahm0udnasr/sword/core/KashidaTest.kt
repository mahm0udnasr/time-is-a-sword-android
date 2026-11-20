package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.text.Stretched
import com.mahm0udnasr.sword.core.text.TATWEEL
import com.mahm0udnasr.sword.core.text.hemistichs
import com.mahm0udnasr.sword.core.text.kashidaSlots
import com.mahm0udnasr.sword.core.text.matchesReminder
import com.mahm0udnasr.sword.core.text.stretch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of the poetry part of tests/friction-and-loosening.test.mjs. */
class KashidaTest {
    private val poem = "والوقتُ أنفسُ ما عُنيتَ بحفظه ... وأراه أسهلَ ما عليك يضيعُ"
    private val t = TATWEEL.toString()

    @Test
    fun `split into halves and verses`() {
        assertEquals(listOf("والوقتُ أنفسُ ما عُنيتَ بحفظه", "وأراه أسهلَ ما عليك يضيعُ"), hemistichs(poem))
        assertEquals(4, hemistichs("أ ... ب\nج … د").size)
        // quotes.json also separates verses with " — " (Android only).
        assertEquals(listOf("دقاتُ قلب المرء قائلةٌ له", "إن الحياة دقائقٌ وثوانِ", "فارفع لنفسك بعد موتك ذكرها", "فالذكر للإنسان عمرٌ ثانِ"),
            hemistichs("دقاتُ قلب المرء قائلةٌ له ... إن الحياة دقائقٌ وثوانِ — فارفع لنفسك بعد موتك ذكرها ... فالذكر للإنسان عمرٌ ثانِ"))
    }

    @Test
    fun `tatweels go in pairs, last join first`() {
        assertEquals("الحفــر", stretch("الحفر", 2).text)
        assertEquals("الحــفــر", stretch("الحفر", 4).text)
    }

    @Test
    fun `lam-alef never split and nothing right after the article`() {
        assertFalse(stretch("ولا الأمل", 1).text.contains("ل" + t + "ا"))
        assertFalse(stretch("الدهر", 3).text.startsWith("ال" + t))
    }

    @Test
    fun `no joins means nothing inserted`() = assertEquals(Stretched("ورد زاد", 0), stretch("ورد زاد", 5))

    @Test
    fun `inserted before the next letter after the harakat`() = assertEquals("يعـشْ", stretch("يعشْ", 1).text)

    @Test
    fun `stretched text still matches the typing check`() = assertTrue(matchesReminder(stretch(poem, 9).text, poem))

    @Test
    fun `at most 4 per join`() {
        val runs = Regex("$t+").findAll(stretch("بين", 99).text).map { it.value.length }
        assertEquals(4, runs.max())
    }

    @Test
    fun `slots grouped by word`() = assertEquals(2, kashidaSlots("بين بين").size)
}
