package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.newId
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.quotes.QuoteFile
import com.mahm0udnasr.sword.core.quotes.parseQuotes
import com.mahm0udnasr.sword.core.quotes.pickQuote
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `modes serialize like the extension`() {
        assertEquals("\"limit+schedule\"", json.encodeToString(Mode.serializer(), Mode.LIMIT_SCHEDULE))
        assertEquals("\"block\"", json.encodeToString(Mode.serializer(), Mode.BLOCK))
        assertEquals("\"fajr\"", json.encodeToString(Prayer.serializer(), Prayer.FAJR))
    }

    @Test
    fun `config round-trips through JSON`() {
        val c = Config(targets = listOf(site("x.com").copy(rules = listOf(R(setOf(5, 6), Mode.LIMIT_SCHEDULE, 30, listOf(W("22:00", "01:00")))))))
        val text = json.encodeToString(Config.serializer(), c)
        assertTrue("\"mode\":\"limit+schedule\"" in text)
        assertEquals(c, json.decodeFromString(Config.serializer(), text))
    }

    @Test
    fun `ids have a prefix and differ`() {
        val r = Random(1)
        val a = newId("t", 1_700_000_000_000, r)
        val b = newId("t", 1_700_000_000_000, r)
        assertTrue(Regex("^t_[0-9a-z]{9}$").matches(a), a)
        assertNotEquals(a, b)
    }

    // --- reminders (warnings-pause-changes.test.mjs: ids and no repeats)
    private val file = """[{"text":"a","source":"s","type":"quran"},{"text":"b","source":"s","type":"poetry"},{"text":"c","source":"","type":"hadith"}]"""

    @Test
    fun `reminders get stable ids`() {
        assertEquals(listOf("q0", "q1", "q2"), parseQuotes(file, QuoteFile.REMINDERS).map { it.id })
        assertEquals("s0", parseQuotes(file, QuoteFile.SALAH).first().id)
    }

    @Test
    fun `no reminder twice in a row`() {
        val qs = parseQuotes(file, QuoteFile.REMINDERS)
        val r = Random(7)
        var last: String? = null
        repeat(200) {
            val q = pickQuote(qs, last, r)!!
            assertNotEquals(last, q.id)
            last = q.id
        }
        assertEquals("q0", pickQuote(qs.take(1), "q0", r)!!.id, "a single reminder may repeat")
        assertNull(pickQuote(emptyList(), null, r))
    }
}
