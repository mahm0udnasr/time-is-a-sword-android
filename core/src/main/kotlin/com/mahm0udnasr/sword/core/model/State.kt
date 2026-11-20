package com.mahm0udnasr.sword.core.model

import kotlinx.serialization.Serializable

/** Live data (DataStore file `state.json`): usage, the session, pauses, caches. */
@Serializable
data class State(
    /** Key = logical day "YYYY-MM-DD"; only the last [Defaults.HISTORY_DAYS] days are kept. */
    val usage: Map<String, DayUsage> = emptyMap(),
    val session: Session? = null,
    /** Key = target id. */
    val pauses: Map<String, Pause> = emptyMap(),
    val lastQuoteId: String? = null,
    val lastSalahId: String? = null,
    /** Times of a typed city, from Aladhan (Salah First, step 7.3). */
    val prayerCache: com.mahm0udnasr.sword.core.prayer.PrayerCache? = null,
    /** For clock-tamper detection (D12). */
    val lastTrustedWallClock: Long? = null,
    /** "Turn off protection" (step 8.3): the Settings guard is off until then. */
    val guardSuspendedUntil: Long? = null,
    /** The first-run wizard was finished or skipped (Phase 9). */
    val onboardingDone: Boolean = false,
)

@Serializable
data class DayUsage(
    /** Key = target id. */
    val seconds: Map<String, Double> = emptyMap(),
    /** Warning thresholds already shown; key = target id, or "id@HH:MM" for a window end. */
    val warned: Map<String, List<Int>> = emptyMap(),
    val pausesUsed: Int = 0,
)

/**
 * What is being timed right now (only one at a time, so nothing is counted twice).
 * Wall-clock times decide the day; elapsedRealtime measures durations.
 */
@Serializable
data class Session(
    val targetId: String,
    val since: Long,
    val lastFlushWall: Long,
    val lastFlushElapsed: Long,
)

@Serializable
data class Pause(val until: Long)
