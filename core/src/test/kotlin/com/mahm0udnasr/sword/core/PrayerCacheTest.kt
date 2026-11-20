package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.prayer.Coords
import com.mahm0udnasr.sword.core.prayer.PRAYER_RETRY_MS
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerCache
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.activeLock
import com.mahm0udnasr.sword.core.prayer.cachedDays
import com.mahm0udnasr.sword.core.prayer.cfgKey
import com.mahm0udnasr.sword.core.prayer.lockWindows
import com.mahm0udnasr.sword.core.prayer.mergePrayerCache
import com.mahm0udnasr.sword.core.prayer.monthsToFetch
import com.mahm0udnasr.sword.core.prayer.parseAladhanTime
import com.mahm0udnasr.sword.core.prayer.prayerFetchDue
import com.mahm0udnasr.sword.core.prayer.usesCityTimes
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerCacheTest {
    private val cairo = ZoneId.of("Africa/Cairo")
    private val p = PrayerSettings(enabled = true, address = "Kafr El Zayat, Egypt")
    private val key = cfgKey(p)
    private fun at(d: Int, h: Int, m: Int, month: Int = 11) = ZonedDateTime.of(2026, month, d, h, m, 0, 0, cairo).toInstant().toEpochMilli()
    private val day = mapOf(Prayer.FAJR to "04:55", Prayer.DHUHR to "11:41", Prayer.ASR to "14:38", Prayer.MAGHRIB to "16:57", Prayer.ISHA to "18:18")
    private fun cacheOf(vararg dates: String) = PrayerCache(key, "Africa/Cairo", dates.associateWith { day })

    @Test
    fun `parse Aladhan times`() {
        assertEquals("04:55", parseAladhanTime("04:55 (EET)"))
        assertEquals("04:05", parseAladhanTime("4:05"))
        assertNull(parseAladhanTime("soon"))
    }

    @Test
    fun `city mode only without coordinates`() {
        assertTrue(usesCityTimes(p))
        assertFalse(usesCityTimes(p.copy(coords = Coords(30.0, 31.0))))
        assertFalse(usesCityTimes(PrayerSettings()))
    }

    @Test
    fun `cached days become lock windows in the location's zone`() {
        val days = cachedDays(p, cacheOf("2026-11-18", "2026-11-19", "2026-11-20"), at(19, 11, 40))
        assertEquals(3, days.size)
        assertTrue(days.none { it.approx })
        val lock = activeLock(lockWindows(p, days), at(19, 11, 40))
        assertEquals(Prayer.DHUHR, lock?.prayer)
        assertEquals(at(19, 11, 41), lock?.at)
    }

    @Test
    fun `missing day borrows the nearest one, marked approx`() {
        val days = cachedDays(p, cacheOf("2026-11-10"), at(19, 12, 0))
        assertEquals(3, days.size)
        assertTrue(days.all { it.approx })
        assertEquals(at(19, 18, 18), days[1].times[Prayer.ISHA])
    }

    @Test
    fun `other settings' cache is ignored`() {
        assertTrue(cachedDays(p.copy(method = 4), cacheOf("2026-11-19"), at(19, 12, 0)).isEmpty())
    }

    @Test
    fun `months to fetch - everything for new settings, only gaps otherwise`() {
        val now = at(19, 12, 0)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12)), monthsToFetch(p, null, now, cairo))
        val nov = (1..30).map { LocalDate.of(2026, 11, it).toString() }.toTypedArray()
        assertEquals(listOf(YearMonth.of(2026, 12)), monthsToFetch(p, cacheOf(*nov), now, cairo))
        val dec = (1..31).map { LocalDate.of(2026, 12, it).toString() }.toTypedArray()
        assertEquals(emptyList(), monthsToFetch(p, cacheOf(*nov, *dec), now, cairo))
        assertEquals(emptyList(), monthsToFetch(p.copy(coords = Coords(30.0, 31.0)), null, now, cairo), "location mode calculates")
    }

    @Test
    fun `retry throttle`() {
        val now = at(19, 12, 0)
        val failed = cacheOf("2026-11-19").copy(attemptedAt = now - 60_000, error = "offline")
        assertFalse(prayerFetchDue(p, failed, now, cairo))
        assertTrue(prayerFetchDue(p, failed, now + PRAYER_RETRY_MS, cairo))
        assertTrue(prayerFetchDue(p.copy(method = 4), failed, now, cairo), "new settings: at once")
    }

    @Test
    fun `merge keeps same-settings days and prunes before yesterday`() {
        val now = at(19, 12, 0)
        val old = cacheOf("2026-11-17", "2026-11-18", "2026-11-19")
        val merged = mergePrayerCache(old, key, "Africa/Cairo", mapOf("2026-11-20" to day), now, null)
        assertEquals(listOf("2026-11-18", "2026-11-19", "2026-11-20"), merged.days.keys.toList())
        assertEquals(now, merged.attemptedAt)
        val fresh = mergePrayerCache(old, "other", "Africa/Cairo", mapOf("2026-11-20" to day), now, null)
        assertEquals(listOf("2026-11-20"), fresh.days.keys.toList())
    }
}
