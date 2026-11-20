package com.mahm0udnasr.sword.ui.friction

import android.content.Context
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.track.PauseResult
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.tr

/** What happened, for the caller to show (port of pause-flow.js). */
data class PauseOutcome(val ok: Boolean, val message: String, val until: Long? = null)

/**
 * "Pause for 5 min": check a pause is possible, run the friction step, then
 * grant it (port of runPauseFlow(), steps 6.2/6.3).
 */
suspend fun runPauseFlow(ctx: Context, c: AppContainer, committer: Committer, targetId: String): PauseOutcome {
    val target = c.config.value.targets.firstOrNull { it.id == targetId }
    val site = ltr(target?.name ?: targetId)
    // A pause never lifts a prayer lock (§8.6), so none is offered then.
    if (c.prayerClock.active() != null) return PauseOutcome(false, ctx.getString(R.string.pause_prayer))
    val a = c.pauses.availability(targetId)
    if (!a.ok) {
        return PauseOutcome(false, if (a.code == PauseResult.Code.NONE_LEFT) ctx.getString(R.string.x_pause_noneLeft) else ctx.tr(R.string.x_pause_notActive, "site" to site))
    }
    val confirmed = committer.friction(
        FrictionRequest(
            title = ctx.tr(R.string.x_pause_title, "site" to site),
            reasons = listOf(
                ctx.tr(R.string.x_pause_r1, "site" to site, "n" to a.minutes),
                ctx.tr(R.string.x_pause_r2, "n" to a.left - 1),
            ),
            note = ctx.getString(R.string.x_pause_note),
            confirmLabel = ctx.tr(R.string.x_pause_confirm, "n" to a.minutes),
        ),
    )
    if (!confirmed) return PauseOutcome(false, ctx.getString(R.string.x_pause_cancelled))
    return when (val r = c.pauses.grant(targetId)) {
        is PauseResult.Granted -> PauseOutcome(true, ctx.tr(R.string.pause_done, "site" to site, "time" to ctx.formatClock(r.until)), r.until)
        is PauseResult.Refused -> PauseOutcome(
            false,
            if (r.code == PauseResult.Code.NONE_LEFT) ctx.getString(R.string.x_pause_noneLeft) else ctx.tr(R.string.x_pause_notActive, "site" to site),
        )
    }
}
