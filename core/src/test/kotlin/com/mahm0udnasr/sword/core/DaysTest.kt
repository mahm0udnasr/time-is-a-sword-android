package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.rules.dayKey
import com.mahm0udnasr.sword.core.rules.jsDay
import com.mahm0udnasr.sword.core.rules.logicalDate
import com.mahm0udnasr.sword.core.rules.nextResetAt
import com.mahm0udnasr.sword.core.rules.weekdayOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DaysTest {
    @Test
    fun `java DayOfWeek maps to JS getDay numbering`() {
        assertEquals(0, DayOfWeek.SUNDAY.jsDay())
        assertEquals(1, DayOfWeek.MONDAY.jsDay())
        assertEquals(5, DayOfWeek.FRIDAY.jsDay())
        assertEquals(6, DayOfWeek.SATURDAY.jsDay())
        assertEquals(3, weekdayOf(T(18, 10), CAIRO)) // 2026-11-18 is a Wednesday
    }

    @Test
    fun `logical day with reset 04_00`() {
        assertEquals("2026-11-18", dayKey(T(19, 2), CAIRO, 4))
        assertEquals("2026-11-19", dayKey(T(19, 4), CAIRO, 4))
        assertEquals(LocalDate.of(2026, 11, 18), logicalDate(T(19, 3, 59), CAIRO, 4))
        assertEquals(3, weekdayOf(T(19, 2), CAIRO, 4)) // still Wednesday
    }

    @Test
    fun `next reset`() {
        assertEquals(T(19, 4), nextResetAt(T(18, 10), CAIRO, 4))
        assertEquals(T(19, 0), nextResetAt(T(18, 10), CAIRO, 0))
        assertEquals(T(19, 0), nextResetAt(T(18, 0), CAIRO, 0), "strictly after")
    }

    @Test
    fun `Egypt DST start - midnight does not exist, reset moves to 01_00`() {
        // 2026-04-24 (last Friday of April): clocks jump 00:00 → 01:00.
        val before = ZonedDateTime.of(2026, 4, 23, 22, 0, 0, 0, CAIRO).toInstant().toEpochMilli()
        val reset = nextResetAt(before, CAIRO, 0)
        assertEquals(ZonedDateTime.of(2026, 4, 24, 1, 0, 0, 0, CAIRO).toInstant().toEpochMilli(), reset)
        assertEquals(2 * 3_600_000L, reset - before, "only 2 real hours later")
        assertEquals("2026-04-24", dayKey(reset, CAIRO))
    }

    @Test
    fun `Egypt DST end - the day is 25 hours long`() {
        // 2026-10-29 (last Thursday of October): 24:00 → 23:00.
        val noon = ZonedDateTime.of(2026, 10, 29, 12, 0, 0, 0, CAIRO).toInstant().toEpochMilli()
        val reset = nextResetAt(noon, CAIRO, 0)
        assertEquals(13 * 3_600_000L, reset - noon)
        assertEquals("2026-10-30", dayKey(reset, CAIRO))
        assertEquals("2026-10-29", dayKey(reset - 1, CAIRO))
    }
}
