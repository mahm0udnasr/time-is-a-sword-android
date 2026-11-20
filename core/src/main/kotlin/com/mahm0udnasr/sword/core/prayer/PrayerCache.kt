package com.mahm0udnasr.sword.core.prayer

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Times for a typed city, fetched from Aladhan a month at a time (port of the
 * extension's prayerTimes cache, extended to weeks ahead so locking keeps
 * working offline). "Use my location" doesn't use this: those times are
 * calculated on the phone (PrayerCalc).
 *
 * @property cfg cfgKey() of the settings the times are for
 * @property timezone the location's IANA zone (from Aladhan's meta.timezone)
 * @property days "YYYY-MM-DD" → prayer → "HH:MM" on the location's wall clock
 * @property attemptedAt last fetch attempt (successful or not), for the retry throttle
 * @property error message of the last failed fetch, or null
 */
@Serializable
data class PrayerCache(
    val cfg: String,
    val timezone: String,
    val days: Map<String, Map<Prayer, String>> = emptyMap(),
    val attemptedAt: Long = 0,
    val error: String? = null,
)

/** Days kept ahead of today (a month at a time, so 1–2 fetches cover it). */
const val PRAYER_DAYS_AHEAD = 35L

/** Retry a failed or missing fetch at most this often (the extension's 15 min). */
const val PRAYER_RETRY_MS = 15 * 60_000L

/** Times come from Aladhan (a typed city), not from the phone's own calculation. */
fun usesCityTimes(p: PrayerSettings): Boolean = p.coords == null && p.address.isNotBlank()

/** "04:55 (EET)" → "04:55"; null if there's no time in it (port of fetchTimings()' check). */
fun parseAladhanTime(s: String): String? =
    Regex("""(\d{1,2}):(\d{2})""").find(s)?.let { m -> "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}" }

private fun zoneOrNull(id: String?): ZoneId? = id?.let { runCatching { ZoneId.of(it) }.getOrNull() }

/**
 * Yesterday, today and tomorrow at the location from the cache. A missing day
 * borrows the nearest cached day's times, marked approx (the extension's
 * offline fallback).
 */
fun cachedDays(p: PrayerSettings, cache: PrayerCache?, now: Long): List<PrayerDay> {
    val key = cfgKey(p)
    if (cache == null || cache.cfg != key || cache.days.isEmpty()) return emptyList()
    val zone = zoneOrNull(cache.timezone) ?: return emptyList()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val known = cache.days.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    return (-1L..1L).mapNotNull { off ->
        val date = today.plusDays(off)
        val exact = cache.days[date.toString()]
        val timings = exact ?: known.minByOrNull { kotlin.math.abs(it.toEpochDay() - date.toEpochDay()) }?.let { cache.days[it.toString()] } ?: return@mapNotNull null
        val times = timings.mapNotNull { (prayer, hm) ->
            runCatching { prayer to date.atTime(LocalTime.parse(hm)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        }.toMap()
        PrayerDay(date, key, times, zone.id, approx = exact == null)
    }
}

/**
 * Months to fetch so yesterday through [PRAYER_DAYS_AHEAD] days ahead are
 * cached for [p] (all of them if the cache is for other settings).
 * @param fallbackZone used before the location's zone is known
 */
fun monthsToFetch(p: PrayerSettings, cache: PrayerCache?, now: Long, fallbackZone: ZoneId): List<YearMonth> {
    if (!usesCityTimes(p)) return emptyList()
    val same = cache != null && cache.cfg == cfgKey(p)
    val zone = (if (same) zoneOrNull(cache?.timezone) else null) ?: fallbackZone
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return (-1L..PRAYER_DAYS_AHEAD).map { today.plusDays(it) }
        .filter { !same || cache!!.days[it.toString()] == null }
        .map { YearMonth.from(it) }
        .distinct()
}

/** Is a fetch due? Something is missing, and the last attempt for these settings is older than the throttle. */
fun prayerFetchDue(p: PrayerSettings, cache: PrayerCache?, now: Long, fallbackZone: ZoneId): Boolean {
    if (monthsToFetch(p, cache, now, fallbackZone).isEmpty()) return false
    return cache == null || cache.cfg != cfgKey(p) || now - cache.attemptedAt >= PRAYER_RETRY_MS
}

/**
 * New cache after fetching [fetched] days for [cfg] (old days kept when the
 * settings are the same), pruned to yesterday onwards in the location's zone.
 */
fun mergePrayerCache(old: PrayerCache?, cfg: String, timezone: String, fetched: Map<String, Map<Prayer, String>>, now: Long, error: String?): PrayerCache {
    val base = if (old != null && old.cfg == cfg) old.days else emptyMap()
    val zone = zoneOrNull(timezone) ?: ZoneId.of("UTC")
    val oldest = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(1).toString()
    return PrayerCache(cfg, timezone, (base + fetched).filterKeys { it >= oldest }.toSortedMap(), attemptedAt = now, error = error)
}
