package com.mahm0udnasr.sword.ui.friction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The no-paste heuristic of the friction typing box (plan §8.5). */
class PasteGuardTest {
    @Test
    fun `typing one letter at a time is fine`() {
        assertTrue(acceptEdit("", "ا"))
        assertTrue(acceptEdit("من حسن", "من حسن "))
        assertTrue(acceptEdit("من حسن ", "من حسن ا"))
    }

    @Test
    fun `glide typing a whole word (with its space) is fine`() {
        assertTrue(acceptEdit("من حسن ", "من حسن إسلام "))
        assertTrue("insert in the middle", acceptEdit("abc", "abXc"))
    }

    @Test
    fun `pasting a sentence or a long chunk is rejected`() {
        assertFalse(acceptEdit("", "من حسن إسلام المرء"))
        assertFalse(acceptEdit("من ", "من حسن إسلام"))
        assertFalse("more than 20 chars at once", acceptEdit("", "abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `deleting and replacing by less is always fine`() {
        assertTrue(acceptEdit("من حسن إسلام المرء", ""))
        assertTrue(acceptEdit("من حسن إسلام", "من حسن"))
        assertTrue("autocorrect-sized fix", acceptEdit("من حسن اسلم", "من حسن اسلام"))
    }
}
