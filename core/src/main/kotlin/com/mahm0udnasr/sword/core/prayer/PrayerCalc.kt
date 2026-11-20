package com.mahm0udnasr.sword.core.prayer

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Prayer times calculated on the phone with the Adhan library (D8): works
 * offline forever, and nothing is sent anywhere after the location is set.
 */
object PrayerCalc {

    /** The five times for [date] at the location, or null if no coordinates are set. */
    fun day(p: PrayerSettings, date: LocalDate, zone: ZoneId): PrayerDay? {
        val c = p.coords ?: return null
        val params = Methods.adhan(p.method).parameters.copy(madhab = if (p.school == 1) Madhab.HANAFI else Madhab.SHAFI)
        val t = PrayerTimes(Coordinates(c.lat, c.lng), DateComponents(date.year, date.monthValue, date.dayOfMonth), params)
        val times = mapOf(
            Prayer.FAJR to t.fajr.toEpochMilliseconds(),
            Prayer.DHUHR to t.dhuhr.toEpochMilliseconds(),
            Prayer.ASR to t.asr.toEpochMilliseconds(),
            Prayer.MAGHRIB to t.maghrib.toEpochMilliseconds(),
            Prayer.ISHA to t.isha.toEpochMilliseconds(),
        )
        return PrayerDay(date, cfgKey(p), times, zone.id)
    }

    /** The zone used for the location: its own if known, else the phone's. */
    fun zoneOf(p: PrayerSettings, deviceZone: ZoneId): ZoneId =
        p.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: deviceZone

    /** Yesterday, today and tomorrow at the location (an Isha lock can run past midnight). */
    fun around(p: PrayerSettings, now: Long, deviceZone: ZoneId): List<PrayerDay> {
        val zone = zoneOf(p, deviceZone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return listOf(-1L, 0L, 1L).mapNotNull { day(p, today.plusDays(it), zone) }
    }
}
