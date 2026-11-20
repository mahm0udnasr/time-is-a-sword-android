package com.mahm0udnasr.sword.core.rules

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/*
 * Days and the daily reset (port of the day helpers in rules.js).
 *
 * A "logical day" runs from resetHour to resetHour in local time. Everything
 * here uses calendar arithmetic on java.time, never "+24 h", so DST changes
 * (Egypt has DST) are safe. The zone is always passed in: no hidden clock.
 */

/** JS getDay() numbering: 0 = Sunday … 6 = Saturday. java.time is 1 = Monday … 7 = Sunday. */
fun DayOfWeek.jsDay(): Int = value % 7

internal fun zoned(ts: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(ts).atZone(zone)

/** Epoch ms of a local date + time in [zone]. A time in a DST gap moves forward, like JS Date. */
internal fun epochOf(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
    ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()

/**
 * The calendar day a moment belongs to once the reset hour is taken into
 * account. With resetHour = 4, 02:00 Saturday still belongs to Friday.
 */
fun logicalDate(ts: Long, zone: ZoneId, resetHour: Int = 0): LocalDate {
    val z = zoned(ts, zone)
    return if (z.hour < resetHour) z.toLocalDate().minusDays(1) else z.toLocalDate()
}

/** Storage key for the logical day containing ts, e.g. "2026-11-18". */
fun dayKey(ts: Long, zone: ZoneId, resetHour: Int = 0): String = logicalDate(ts, zone, resetHour).toString()

/** Weekday of the logical day: 0 = Sunday … 6 = Saturday. */
fun weekdayOf(ts: Long, zone: ZoneId, resetHour: Int = 0): Int = logicalDate(ts, zone, resetHour).dayOfWeek.jsDay()

/** Timestamp of the next daily reset strictly after ts. */
fun nextResetAt(ts: Long, zone: ZoneId, resetHour: Int = 0): Long {
    val today = zoned(ts, zone).toLocalDate()
    val r = epochOf(today, LocalTime.of(resetHour, 0), zone)
    return if (r <= ts) epochOf(today.plusDays(1), LocalTime.of(resetHour, 0), zone) else r
}

/** Minutes since local midnight, with fractions. */
internal fun minuteOfDay(ts: Long, zone: ZoneId): Double {
    val z = zoned(ts, zone)
    return z.hour * 60 + z.minute + z.second / 60.0 + (z.nano / 1_000_000) / 60_000.0
}
