package com.mahm0udnasr.sword.ui.settings

import android.content.Context
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.admin.SwordDeviceAdmin
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.friction.FrictionRequest

/** How long "Turn off protection" opens the app's Settings pages (plan §10.2). */
const val GUARD_SUSPEND_MIN = 5

/**
 * The legitimate way out (step 8.3): friction, then the Settings guard is off
 * for [GUARD_SUSPEND_MIN] minutes so the user can switch the service off or
 * uninstall normally. Comes back by itself.
 * @return true if protection was suspended
 */
suspend fun suspendProtection(ctx: Context, c: AppContainer, committer: Committer): Boolean {
    val n = GUARD_SUSPEND_MIN
    val ok = committer.friction(
        FrictionRequest(
            title = ctx.getString(R.string.suspend_title),
            reasons = listOf(ctx.tr(R.string.suspend_r1, "n" to n)),
            note = ctx.tr(R.string.suspend_note, "n" to n),
            confirmLabel = ctx.tr(R.string.suspend_confirm, "n" to n),
        ),
    )
    if (ok) c.state.update { it.copy(guardSuspendedUntil = System.currentTimeMillis() + n * 60_000L) }
    return ok
}

/** Back on at once: stricter, so no friction. */
suspend fun resumeProtection(c: AppContainer) = c.state.update { it.copy(guardSuspendedUntil = null) }

/** Device admin off from inside the app (step 8.4): a looser change, so friction first. */
suspend fun deactivateAdmin(ctx: Context, committer: Committer): Boolean {
    val ok = committer.friction(
        FrictionRequest(
            title = ctx.getString(R.string.admin_offTitle),
            reasons = listOf(ctx.getString(R.string.admin_offR1)),
            note = ctx.getString(R.string.x_fr_note),
            confirmLabel = ctx.getString(R.string.x_fr_apply),
        ),
    )
    if (ok) SwordDeviceAdmin.deactivate(ctx)
    return ok
}
