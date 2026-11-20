package com.mahm0udnasr.sword.service

import android.os.SystemClock
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.track.FlushResult
import com.mahm0udnasr.sword.core.track.Warning
import com.mahm0udnasr.sword.core.track.flush
import com.mahm0udnasr.sword.data.ConfigRepository
import com.mahm0udnasr.sword.data.StateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Counts time per target (event part of tracker.js, step 3.5).
 *
 * The decision itself is the pure core `flush()`. This class decides *when*
 * to flush: on every change of the target in use, on screen on/off, every
 * 30 s while the screen is on, when the config changes, and exactly at the
 * next block moment or warning (a coroutine timer; Phase 4 adds an exact
 * alarm as a backup).
 */
class Tracker(
    private val config: ConfigRepository,
    private val state: StateRepository,
    private val scope: CoroutineScope,
    private val wall: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** Called on the flush's coroutine; Phase 4 shows the overlay card. */
    var onWarning: (Warning) -> Unit = {}

    /** The target being tracked just got blocked: Phase 4 re-checks its windows. */
    var onRanOut: (String) -> Unit = {}

    /** Mirror of the wake timer as an exact alarm (WakeAlarm, step 6.4). */
    var onScheduleWake: (Long?) -> Unit = {}

    /** Every flush result (debug screen, Phase 4 enforcer). */
    var onFlushed: (FlushResult) -> Unit = {}

    @Volatile
    var inUse: String? = null
        private set

    private var tickJob: Job? = null
    private var timerJob: Job? = null
    private var started = false

    /** Load data, discard any session left from before (process start or boot), then follow config changes. */
    suspend fun start() {
        config.awaitLoaded()
        state.awaitLoaded()
        flushNow(discard = true)
        started = true
        scope.launch { config.flow.drop(1).collect { flushNow() } }
    }

    /** Short videos inside an app: that app's own target, credited too (Q8). */
    @Volatile
    var alsoInUse: String? = null
        private set

    fun setInUse(targetId: String?, also: String? = null) {
        if (targetId == inUse && also == alsoInUse) return
        inUse = targetId
        alsoInUse = also
        if (started) scope.launch { flushNow() }
    }

    /** Ticker runs only while the screen is on and unlocked: no work while it is off. */
    fun onScreen(active: Boolean) {
        tickJob?.cancel()
        if (active) {
            tickJob = scope.launch {
                while (isActive) {
                    delay(Defaults.TICK_MS)
                    flushNow()
                }
            }
        }
    }

    suspend fun flushNow(discard: Boolean = false) {
        var result: FlushResult? = null
        state.update { s ->
            flush(s, config.value, inUse, wall(), elapsed(), zone(), discardSession = discard, alsoInUse = alsoInUse).also { result = it }.state
        }
        val r = result ?: return
        onFlushed(r)
        r.warning?.let(onWarning)
        r.ranOut?.let(onRanOut)
        scheduleWake(listOfNotNull(r.blockAt, r.nextWarnAt).minOrNull())
    }

    /** Wake exactly at the next block moment or warning (+250 ms, like the extension's alarms). */
    private fun scheduleWake(at: Long?) {
        timerJob?.cancel()
        onScheduleWake(at?.plus(250))
        if (at == null) return
        timerJob = scope.launch {
            delay((at - wall()).coerceAtLeast(1000) + 250)
            flushNow()
        }
    }

    fun stop() {
        tickJob?.cancel()
        timerJob?.cancel()
    }
}
