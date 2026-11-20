package com.mahm0udnasr.sword.prayer

import com.mahm0udnasr.sword.core.prayer.Coords
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.PrayerToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerAppTest {
    private val cairo = ZoneId.of("Africa/Cairo")
    private val settings = PrayerSettings(enabled = true, coords = Coords(30.04, 31.24), timezone = "Africa/Cairo")
    private fun at(h: Int, m: Int, day: Int = 19) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, cairo).toInstant().toEpochMilli()

    @Test
    fun `parses an Aladhan month, its errors and odd answers`() {
        val body = """{"code":200,"status":"OK","data":[
          {"timings":{"Fajr":"04:55 (EET)","Sunrise":"06:20 (EET)","Dhuhr":"11:41 (EET)","Asr":"14:38 (EET)","Maghrib":"16:57 (EET)","Isha":"18:18 (EET)"},
           "date":{"gregorian":{"date":"19-11-2026"}},"meta":{"latitude":8.8888888,"timezone":"Africa/Cairo"}}]}"""
        val ok = AladhanApi.parse(200, body) as AladhanApi.Result.Ok
        assertEquals("Africa/Cairo", ok.timezone)
        assertEquals(mapOf(Prayer.FAJR to "04:55", Prayer.DHUHR to "11:41", Prayer.ASR to "14:38", Prayer.MAGHRIB to "16:57", Prayer.ISHA to "18:18"), ok.days["2026-11-19"])
        val bad = AladhanApi.parse(400, """{"code":400,"status":"BAD_REQUEST","data":"Unable to geocode address: zz"}""")
        assertEquals(AladhanApi.Result.NotFound("Unable to geocode address: zz"), bad)
        assertTrue(AladhanApi.parse(200, "not json") is AladhanApi.Result.Failed)
        assertTrue(AladhanApi.parse(500, "") is AladhanApi.Result.Failed)
    }

    @Test
    fun `lock around Dhuhr in Cairo (11 40 on 2026-11-19, 5 before, 15 after)`() {
        val clock = PrayerClock({ settings }, { cairo })
        assertNull("before the lock", clock.active(at(11, 30)))
        val lock = clock.active(at(11, 40))
        assertNotNull(lock)
        assertEquals(Prayer.DHUHR, lock!!.prayer)
        assertEquals(15 * 60_000L + 5 * 60_000L, lock.end - lock.start)
        assertTrue("lock window around 11:40 ±2 min", lock.at in at(11, 38)..at(11, 42))
        assertEquals(lock.end, clock.nextBoundary(at(11, 40)))
    }

    @Test
    fun `off, no location or prayer switched off - no lock`() {
        assertNull(PrayerClock({ settings.copy(enabled = false) }, { cairo }).active(at(11, 40)))
        assertNull(PrayerClock({ settings.copy(coords = null) }, { cairo }).active(at(11, 40)))
        val noDhuhr = settings.copy(prayers = Prayer.entries.associateWith { PrayerToggle(on = it != Prayer.DHUHR) })
        assertNull(PrayerClock({ noDhuhr }, { cairo }).active(at(11, 40)))
    }

    @Test
    fun `late Isha lock (adjusted) and the cache follows the date`() {
        val late = settings.copy(prayers = Prayer.entries.associateWith { PrayerToggle(adjust = if (it == Prayer.ISHA) 60 else 0) }, after = 120)
        val clock = PrayerClock({ late }, { cairo })
        val d1 = clock.days(at(12, 0))
        assertSame("same day: cached", d1, clock.days(at(13, 0)))
        // Isha ≈ 18:18 + 60 min adjustment + 120 min after → still locked at 21:00 the same evening.
        assertEquals(Prayer.ISHA, clock.active(at(21, 0))?.prayer)
        val d2 = clock.days(at(0, 5, day = 20))
        assertTrue("new date: recalculated", d1 !== d2)
    }

    @Test
    fun `adjustment moves the lock`() {
        val moved = settings.copy(prayers = Prayer.entries.associateWith { PrayerToggle(adjust = if (it == Prayer.DHUHR) 30 else 0) })
        val clock = PrayerClock({ moved }, { cairo })
        assertNull(clock.active(at(11, 40)))
        assertEquals(Prayer.DHUHR, clock.active(at(12, 10))?.prayer)
    }
}
