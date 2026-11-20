package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.text.matchesReminder
import com.mahm0udnasr.sword.core.text.typingProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from the Arabic part of friction-and-loosening.test.mjs (kashida tests come with Phase 10). */
class ArabicTest {
    private val q = "من حُسن إسلام المرء تركه ما لا يعنيه"

    @Test
    fun `no tashkeel, plain alef`() = assertTrue(matchesReminder("من حسن اسلام المرء تركه ما لا يعنيه", q))

    @Test
    fun `extra spaces`() = assertTrue(matchesReminder("  من   حسن إسلام المرء تركه ما لا يعنيه ", q))

    @Test
    fun `ta marbuta and ha`() = assertTrue(matchesReminder("من حسن اسلام المرء تركة ما لا يعنيه", q))

    @Test
    fun `alef maqsura and hamza on alef`() = assertTrue(matchesReminder("حتى يسال", "حتي يُسأل"))

    @Test
    fun incomplete() = assertFalse(matchesReminder("من حسن اسلام المرء تركه", q))

    @Test
    fun tanween() = assertTrue(matchesReminder("اغتنم خمسا قبل خمس", "اغتنم خمسًا قبل خمس"))

    @Test
    fun tatweel() = assertTrue(matchesReminder("والوقـــت", "والوقتُ"))

    @Test
    fun `prefix being typed is not an error`() = assertNull(typingProgress("من حسن اسل", q).wrongWordIndex)

    @Test
    fun `finished wrong word flagged`() = assertEquals("اسلام", typingProgress("من حسن اسلم ", q).expected)

    @Test
    fun `progress counts correct words`() {
        val p = typingProgress("من حسن", q)
        assertEquals(2, p.correctWords)
        assertEquals(8, p.totalWords)
    }

    private val poem = "والوقتُ أنفسُ ما عُنيتَ بحفظه ... وأراه أسهلَ ما عليك يضيعُ"

    @Test
    fun `poem typed on two lines`() = assertTrue(matchesReminder("والوقت انفس ما عنيت بحفظه\nواراه اسهل ما عليك يضيع", poem))

    @Test
    fun `poem typed on one line`() = assertTrue(matchesReminder("والوقت انفس ما عنيت بحفظه واراه اسهل ما عليك يضيع", poem))

    @Test
    fun `only the first half`() = assertFalse(matchesReminder("والوقت انفس ما عنيت بحفظه", poem))

    @Test
    fun `non-breaking spaces count as spaces`() = assertTrue(matchesReminder("حتى يسال", "حتي يُسأل"))
}
