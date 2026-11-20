package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.rules.dayKey
import com.mahm0udnasr.sword.core.track.PauseResult
import com.mahm0udnasr.sword.core.track.requestPause
import java.time.ZoneId

/**
 * Pauses (step 6.1): at most 3 a day, 5 minutes each, behind the friction step
 * (which the caller runs first). The rule logic is the core's requestPause();
 * this grants it atomically in the state store.
 */
class Pauses(private val config: ConfigRepository, private val state: StateRepository) {

    data class Availability(val ok: Boolean, val code: PauseResult.Code?, val left: Int, val minutes: Int)

    fun left(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val used = state.value.usage[dayKey(now, zone, Defaults.RESET_HOUR)]?.pausesUsed ?: 0
        return maxOf(0, Defaults.PAUSE_MAX_PER_DAY - used)
    }

    /** Can [targetId] be paused now? (port of pauseAvailability()) */
    fun availability(targetId: String?): Availability {
        val minutes = minOf(5, Defaults.PAUSE_MAX_MINUTES)
        val target = config.value.targets.firstOrNull { it.id == targetId && it.enabled }
        val left = left()
        val code = when {
            target == null -> PauseResult.Code.NOT_ACTIVE
            left <= 0 -> PauseResult.Code.NONE_LEFT
            else -> null
        }
        return Availability(code == null, code, left, minutes)
    }

    /** Grant a pause (the friction step has already been passed). */
    suspend fun grant(targetId: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): PauseResult {
        var result: PauseResult = PauseResult.Refused(PauseResult.Code.NOT_ACTIVE)
        state.update { s ->
            val target = config.value.targets.firstOrNull { it.id == targetId }
            val r = requestPause(target, s.usage, s.pauses, now, zone, Defaults.RESET_HOUR)
            result = r
            if (r is PauseResult.Granted) s.copy(usage = r.usage, pauses = r.pauses) else s
        }
        return result
    }
}
