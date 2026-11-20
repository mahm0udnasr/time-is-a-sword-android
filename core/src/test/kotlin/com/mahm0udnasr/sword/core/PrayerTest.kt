package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.loosen.LooseReason
import com.mahm0udnasr.sword.core.loosen.prayerLoosening
import com.mahm0udnasr.sword.core.prayer.Coords
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerDay
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.PrayerToggle
import com.mahm0udnasr.sword.core.prayer.activeLock
import com.mahm0udnasr.sword.core.prayer.adjustedTime
import com.mahm0udnasr.sword.core.prayer.cfgKey
import com.mahm0udnasr.sword.core.prayer.hasLocation
import com.mahm0udnasr.sword.core.prayer.lockWindows
import com.mahm0udnasr.sword.core.prayer.nextBoundary
import com.mahm0udnasr.sword.core.prayer.nextLock
import com.mahm0udnasr.sword.core.prayer.normalizeCoords
import com.mahm0udnasr.sword.core.prayer.normalizePrayer
import com.mahm0udnasr.sword.core.prayer.todayTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from prayer.test.mjs (the Aladhan fetching parts move to the app's CityLookup in Phase 7). */
class PrayerTest {
    @Test
    fun `normalize - defaults and clamping`() {
        val p0 = normalizePrayer(
            PrayerSettings(enabled = true, address = " Cairo, Egypt ", before = 999, prayers = mapOf(Prayer.ASR to PrayerToggle(false, 5))),
        )
        assertEquals("Cairo, Egypt", p0.address)
        assertEquals(5, p0.before)
        assertEquals(15, p0.after)
        assertEquals(PrayerToggle(false, 5), p0.toggle(Prayer.ASR))
        assertEquals(PrayerToggle(true, 0), p0.toggle(Prayer.FAJR))
        assertEquals(5, normalizePrayer(PrayerSettings(method = 23)).method, "unsupported method → default")
        assertEquals(0, normalizePrayer(PrayerSettings(prayers = mapOf(Prayer.FAJR to PrayerToggle(true, 99)))).toggle(Prayer.FAJR).adjust)
    }

    // The extension test's fake Aladhan day for Cairo (same values as the real API for 2026-11-19).
    private val cairoDay = mapOf(Prayer.FAJR to "04:53", Prayer.DHUHR to "11:40", Prayer.ASR to "14:38", Prayer.MAGHRIB to "16:57", Prayer.ISHA to "18:18")

    private fun dayOf(date: LocalDate, p: PrayerSettings) = PrayerDay(
        date = date,
        cfg = cfgKey(p),
        times = cairoDay.mapValues { (_, hm) -> ZonedDateTime.of(date, LocalTime.parse(hm), CAIRO).toInstant().toEpochMilli() },
        zone = CAIRO.id,
    )

    private val p = normalizePrayer(PrayerSettings(enabled = true, address = "Cairo, Egypt", method = 5))
    private val days = listOf(dayOf(LocalDate.of(2026, 11, 19), p), dayOf(LocalDate.of(2026, 11, 20), p))
    private val w = lockWindows(p, days)
    private val dhuhr = w.first { it.prayer == Prayer.DHUHR }
    private fun utcHM(ts: Long) = Instant.ofEpochMilli(ts).toString().substring(11, 16)

    @Test
    fun `5 prayers x 2 days`() = assertEquals(10, w.size)

    @Test
    fun `Dhuhr 11_40 Cairo locked 11_35 to 11_55 (UTC 09_35 to 09_55)`() =
        assertEquals(listOf("09:35", "09:55"), listOf(utcHM(dhuhr.start), utcHM(dhuhr.end)))

    @Test
    fun `active lock boundaries`() {
        assertEquals(Prayer.DHUHR, activeLock(w, dhuhr.start + 1000)?.prayer, "locked just after the start")
        assertNull(activeLock(w, dhuhr.start - 1000), "open just before")
        assertNull(activeLock(w, dhuhr.end), "open at the end")
        assertEquals(dhuhr.end, nextBoundary(w, dhuhr.start + 1000), "next boundary = end of the current lock")
        assertEquals(Prayer.DHUHR, nextLock(w, dhuhr.start - 1000)?.prayer, "next lock (for the status line)")
    }

    @Test
    fun `adjustment, switched-off prayer, feature off`() {
        val adj = normalizePrayer(p.copy(prayers = p.prayers + (Prayer.DHUHR to PrayerToggle(true, 10))))
        assertEquals(600_000L, lockWindows(adj, days).first { it.prayer == Prayer.DHUHR }.at - dhuhr.at, "+10 min adjustment moves the lock")
        val noAsr = normalizePrayer(p.copy(prayers = p.prayers + (Prayer.ASR to PrayerToggle(false))))
        assertFalse(lockWindows(noAsr, days).any { it.prayer == Prayer.ASR }, "a switched-off prayer never locks")
        assertEquals(emptyList(), lockWindows(p.copy(enabled = false), days), "feature off → no locks")
        val today = todayTimes(adj, days, dhuhr.at, CAIRO)!!
        assertEquals("11:50", adjustedTime(today, Prayer.DHUHR, adj), "displayed adjusted time")
    }

    @Test
    fun `days of other settings are ignored`() =
        assertEquals(emptyList(), lockWindows(p.copy(address = "Alexandria, Egypt"), days), "new address → times are for the old place")

    @Test
    fun `a long lock after Isha stays active`() {
        val late = normalizePrayer(p.copy(after = 120))
        val isha = lockWindows(late, days).first { it.prayer == Prayer.ISHA }
        assertEquals(isha, activeLock(lockWindows(late, days), isha.at + 100 * 60_000L))
    }

    // --- anti-cheat
    private val on = PrayerSettings(enabled = true, address = "Cairo", before = 5, after = 15)

    @Test
    fun `setting up while off is free`() =
        assertEquals(emptyList(), prayerLoosening(on.copy(enabled = false), PrayerSettings(enabled = false, address = "x", before = 0)))

    @Test
    fun `more minutes = stricter`() = assertEquals(emptyList(), prayerLoosening(on, on.copy(before = 10, after = 20)))

    @Test
    fun `switch off`() = assertEquals(listOf(LooseReason.PrayerOff), prayerLoosening(on, on.copy(enabled = false)))

    @Test
    fun `fewer minutes + prayer off`() = assertEquals(
        listOf(LooseReason.PrayerBeforeReduced(5, 2), LooseReason.PrayerSwitchedOff(Prayer.ISHA)),
        prayerLoosening(on, on.copy(before = 2, prayers = mapOf(Prayer.ISHA to PrayerToggle(false)))),
    )

    @Test
    fun `location change`() = assertEquals(listOf(LooseReason.PrayerLocationChanged), prayerLoosening(on, on.copy(address = "London")))

    @Test
    fun `time adjustment`() = assertEquals(
        listOf(LooseReason.PrayerAdjusted(Prayer.FAJR, 0, 5)),
        prayerLoosening(on, on.copy(prayers = mapOf(Prayer.FAJR to PrayerToggle(true, 5)))),
    )

    // --- Use my location
    @Test
    fun `coordinates rounded to 2 decimals`() = assertEquals(Coords(30.04, 31.24), normalizeCoords(Coords(30.04442, 31.235712)))

    @Test
    fun `invalid coordinates rejected`() = assertNull(normalizeCoords(Coords(200.0, 0.0)))

    @Test
    fun `coordinates count as a location, cache separately, and changing them while on is looser`() {
        val pc = normalizePrayer(PrayerSettings(coords = Coords(30.04442, 31.235712), address = "ignored when coords are set", method = 5))
        assertTrue(hasLocation(pc))
        assertEquals("@30.04,31.24|5|0", cfgKey(pc))
        assertNotEquals(cfgKey(pc), cfgKey(normalizePrayer(PrayerSettings(address = "Cairo", method = 5))))
        assertEquals(listOf(LooseReason.PrayerLocationChanged), prayerLoosening(on, on.copy(coords = Coords(30.04, 31.24))))
        assertEquals(emptyList(), prayerLoosening(on.copy(enabled = false), on.copy(enabled = false, coords = Coords(1.0, 2.0))), "locating while off is free")
    }
}
