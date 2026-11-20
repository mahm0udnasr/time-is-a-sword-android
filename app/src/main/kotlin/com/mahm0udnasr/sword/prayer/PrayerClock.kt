package com.mahm0udnasr.sword.prayer

import com.mahm0udnasr.sword.core.prayer.LockWindow
import com.mahm0udnasr.sword.core.prayer.PrayerCache
import com.mahm0udnasr.sword.core.prayer.PrayerCalc
import com.mahm0udnasr.sword.core.prayer.cachedDays
import com.mahm0udnasr.sword.core.prayer.usesCityTimes
import com.mahm0udnasr.sword.core.prayer.PrayerDay
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.activeLock
import com.mahm0udnasr.sword.core.prayer.cfgKey
import com.mahm0udnasr.sword.core.prayer.lockWindows
import com.mahm0udnasr.sword.core.prayer.nextBoundary
import com.mahm0udnasr.sword.core.prayer.nextLock
import com.mahm0udnasr.sword.core.prayer.normalizePrayer
import java.time.Instant
import java.time.ZoneId

/**
 * Prayer times and lock windows: calculated on the phone for "Use my
 * location" (D8), or read from the Aladhan cache for a typed city; kept in
 * memory: yesterday, today and tomorrow at the location (an Isha lock can pass
 * midnight). Recalculated only when the settings or the local date change, so
 * the service can ask on every event. Nothing is stored or sent.
 */
class PrayerClock(
    private val settings: () -> PrayerSettings,
    private val deviceZone: () -> ZoneId = ZoneId::systemDefault,
    /** A typed city's times from Aladhan (null in "Use my location" mode). */
    private val cache: () -> PrayerCache? = { null },
) {
    private var key: String? = null
    private var days: List<PrayerDay> = emptyList()

    @Synchronized
    fun days(now: Long, raw: PrayerSettings = settings()): List<PrayerDay> {
        val p = normalizePrayer(raw)
        if (usesCityTimes(p)) return cachedDays(p, cache(), now) // cheap: no cache needed
        if (p.coords == null) return emptyList()
        val zone = PrayerCalc.zoneOf(p, deviceZone())
        val k = "${cfgKey(p)}#${zone.id}#${Instant.ofEpochMilli(now).atZone(zone).toLocalDate()}"
        if (k != key) {
            days = PrayerCalc.around(p, now, deviceZone())
            key = k
        }
        return days
    }

    fun windows(now: Long): List<LockWindow> {
        val p = settings()
        return lockWindows(normalizePrayer(p), days(now, p))
    }

    /** The lock in force now, or null. */
    fun active(now: Long = System.currentTimeMillis()): LockWindow? = activeLock(windows(now), now)

    fun next(now: Long = System.currentTimeMillis()): LockWindow? = nextLock(windows(now), now)

    /** The next moment a lock starts or ends. */
    fun nextBoundary(now: Long = System.currentTimeMillis()): Long? = nextBoundary(windows(now), now)

    /** Settings or zone changed outside a date change (e.g. the phone's zone): forget the cache. */
    @Synchronized
    fun invalidate() {
        key = null
    }
}
