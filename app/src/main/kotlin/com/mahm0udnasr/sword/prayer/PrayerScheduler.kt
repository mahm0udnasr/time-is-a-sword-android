package com.mahm0udnasr.sword.prayer

import android.content.Context
import com.mahm0udnasr.sword.service.WakeAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wakes the service exactly when a prayer lock starts or ends (step 7.5):
 * an in-service timer plus an exact alarm as a backup (the coroutine timer
 * stops while the phone is in deep sleep). Rescheduled on start, on every
 * boundary, on settings change and on time / zone / date change.
 */
class PrayerScheduler(
    private val context: Context,
    private val clock: PrayerClock,
    private val scope: CoroutineScope,
    private val onBoundary: () -> Unit,
    private val wall: () -> Long = System::currentTimeMillis,
) {
    private var job: Job? = null

    /** @return the next boundary, or null if none (feature off, no location). */
    fun reschedule(): Long? {
        job?.cancel()
        val now = wall()
        val at = clock.nextBoundary(now)
        WakeAlarm.schedule(context, at?.plus(SLACK_MS), WakeAlarm.PRAYER)
        if (at != null) {
            job = scope.launch {
                delay((at - now).coerceAtLeast(0) + SLACK_MS)
                onBoundary()
                reschedule()
            }
        }
        return at
    }

    fun stop() {
        job?.cancel()
        WakeAlarm.schedule(context, null, WakeAlarm.PRAYER)
    }

    private companion object {
        /** Like the extension's alarms: a little after the moment, so it is surely inside the new state. */
        const val SLACK_MS = 250L
    }
}
