package com.mahm0udnasr.sword.prayer

import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerCache
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.cfgKey
import com.mahm0udnasr.sword.core.prayer.mergePrayerCache
import com.mahm0udnasr.sword.core.prayer.monthsToFetch
import com.mahm0udnasr.sword.core.prayer.normalizePrayer
import com.mahm0udnasr.sword.core.prayer.prayerFetchDue
import com.mahm0udnasr.sword.core.prayer.usesCityTimes
import com.mahm0udnasr.sword.data.ConfigRepository
import com.mahm0udnasr.sword.data.StateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Keeps a typed city's times cached from yesterday to ~5 weeks ahead
 * (port of refreshPrayerTimes()/maybeRefreshPrayer(), a month per call).
 */
class PrayerFetcher(private val config: ConfigRepository, private val state: StateRepository) {
    private val lock = Mutex()

    sealed interface Outcome {
        data class Ok(val cache: PrayerCache) : Outcome
        /** @property recorded the old cache with this attempt and its error noted (for the retry throttle and the status line) */
        data class Error(val result: AladhanApi.Result, val recorded: PrayerCache) : Outcome
    }

    /**
     * Fetch what [raw] needs and return the new cache, without saving it.
     * Used by the screen before saving a new city: an unknown city is
     * reported and nothing changes.
     */
    suspend fun fetch(raw: PrayerSettings, old: PrayerCache?, now: Long = System.currentTimeMillis()): Outcome {
        val p = normalizePrayer(raw)
        // "Use my location" is calculated on the phone: nothing to fetch, and never
        // replace a city's cache with an empty one.
        if (!usesCityTimes(p)) return Outcome.Error(AladhanApi.Result.Failed("not a city"), old ?: PrayerCache(cfgKey(p), ZoneId.systemDefault().id))
        val months = monthsToFetch(p, old, now, ZoneId.systemDefault())
        if (months.isEmpty() && old != null) return Outcome.Ok(old)
        val got = mutableMapOf<String, Map<Prayer, String>>()
        var tz: String? = old?.takeIf { it.cfg == cfgKey(p) }?.timezone
        for (m in months) {
            when (val r = AladhanApi.month(p, m)) {
                is AladhanApi.Result.Ok -> {
                    got += r.days
                    tz = r.timezone ?: tz
                }
                else -> {
                    // Keep what we have (approx fallback covers gaps); remember the error and the attempt.
                    val msg = when (r) {
                        is AladhanApi.Result.NotFound -> r.message
                        is AladhanApi.Result.Failed -> r.message
                        else -> "offline"
                    }
                    if (got.isEmpty()) {
                        val recorded = old?.takeIf { it.cfg == cfgKey(p) }?.copy(attemptedAt = now, error = msg)
                            ?: PrayerCache(cfgKey(p), ZoneId.systemDefault().id, attemptedAt = now, error = msg)
                        return Outcome.Error(r, recorded)
                    }
                    break
                }
            }
        }
        return Outcome.Ok(mergePrayerCache(old, cfgKey(p), tz ?: ZoneId.systemDefault().id, got, now, null))
    }

    /**
     * The service's background refresh: fetch only when something is missing and
     * the last attempt is older than 15 min. @return true if the cache changed.
     */
    suspend fun refreshIfDue(now: Long = System.currentTimeMillis()): Boolean = lock.withLock {
        val p = normalizePrayer(config.value.prayer)
        val old = state.value.prayerCache
        if (!prayerFetchDue(p, old, now, ZoneId.systemDefault())) return false
        val next = when (val out = fetch(p, old, now)) {
            is Outcome.Ok -> out.cache
            is Outcome.Error -> out.recorded
        }
        // Settings may have changed while fetching: only store a cache for the current ones.
        if (cfgKey(normalizePrayer(config.value.prayer)) != next.cfg) return false
        state.update { it.copy(prayerCache = next) }
        true
    }

    /** Store a cache fetched by the screen (after its settings were saved). */
    suspend fun store(cache: PrayerCache) = lock.withLock { state.update { it.copy(prayerCache = cache) } }
}
