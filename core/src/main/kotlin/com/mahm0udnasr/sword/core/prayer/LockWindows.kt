package com.mahm0udnasr.sword.core.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Prayer times of one day at the location (the Android version of a cached
 * day in prayer.js). [times] are epoch ms *without* the user's adjustment.
 * @property cfg cfgKey() of the settings they were calculated for
 * @property zone IANA zone of the location
 */
data class PrayerDay(
    val date: LocalDate,
    val cfg: String,
    val times: Map<Prayer, Long>,
    val zone: String,
    val approx: Boolean = false,
)

/** One lock: from [start] (before the prayer) to [end] (after it). [at] includes the adjustment. */
data class LockWindow(val prayer: Prayer, val at: Long, val start: Long, val end: Long)

/** The prayer time of [prayer] on [day], including the user's adjustment. */
private fun prayerAt(day: PrayerDay, prayer: Prayer, p: PrayerSettings): Long? =
    day.times[prayer]?.let { it + p.toggle(prayer).adjust * 60_000L }

/** Lock windows for the current settings, sorted by start. Empty if the feature is off. */
fun lockWindows(p: PrayerSettings, days: List<PrayerDay>): List<LockWindow> {
    if (!p.enabled) return emptyList()
    val key = cfgKey(p)
    val out = mutableListOf<LockWindow>()
    for (day in days) {
        if (day.cfg != key) continue
        for (prayer in Prayer.entries) {
            if (!p.toggle(prayer).on) continue
            val at = prayerAt(day, prayer, p) ?: continue
            out += LockWindow(prayer, at, at - p.before * 60_000L, at + p.after * 60_000L)
        }
    }
    return out.sortedBy { it.start }
}

fun activeLock(windows: List<LockWindow>, now: Long): LockWindow? = windows.firstOrNull { it.start <= now && now < it.end }

fun nextLock(windows: List<LockWindow>, now: Long): LockWindow? = windows.firstOrNull { it.start > now }

/** The next moment a lock starts or ends (for the timer and the exact alarm), or null. */
fun nextBoundary(windows: List<LockWindow>, now: Long): Long? =
    windows.flatMap { listOf(it.start, it.end) }.filter { it > now }.minOrNull()

/** Today's times at the location (for display), or null. */
fun todayTimes(p: PrayerSettings, days: List<PrayerDay>, now: Long, deviceZone: ZoneId): PrayerDay? {
    val key = cfgKey(p)
    val zone = days.firstOrNull { it.cfg == key }?.zone?.let(ZoneId::of) ?: deviceZone
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return days.firstOrNull { it.cfg == key && it.date == today }
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** Display time "HH:MM" of a prayer at the location, with the adjustment applied. */
fun adjustedTime(day: PrayerDay, prayer: Prayer, p: PrayerSettings): String? =
    prayerAt(day, prayer, p)?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(day.zone)).format(HM) }
