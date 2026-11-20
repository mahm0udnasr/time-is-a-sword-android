package com.mahm0udnasr.sword.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mahm0udnasr.sword.block.BlockActivity
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.State
import com.mahm0udnasr.sword.core.rules.BlockDecision
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.Locks
import com.mahm0udnasr.sword.core.rules.WindowInfo
import com.mahm0udnasr.sword.core.rules.decide
import java.time.ZoneId

/**
 * Blocks what must not be used (port of enforcer.js, steps 4.1, 4.2, 4.4).
 *
 * Every visible application window is judged (split screen / freeform, §8.1.3),
 * on every foreground change and after every flush — which covers a budget
 * running out, a window ending, a pause ending and a rule change (4.4).
 *
 * How a block happens (Spike B): HOME, then — once a window event shows the
 * launcher in front (fallback 700 ms) — BlockActivity. Starting the activity
 * right away loses the race: HOME is asynchronous and the launcher covers it.
 */
class Enforcer(private val service: AccessibilityService, private val launchers: () -> Set<String>) {

    private val main = Handler(Looper.getMainLooper())
    private var pending: BlockDecision? = null
    private var lastBlockAt = 0L
    private var lastBlockPkg: String? = null
    private val fallback = Runnable { pending?.let { show(it, "fallback") } }

    /** Locks from later phases (prayer: 7, guard and clock: 8). */
    var locks: () -> Locks = { Locks() }

    /**
     * Judge the windows on screen; block the first one that must not be used.
     * @return the decision acted on, or null
     */
    fun check(windows: List<WindowInfo>, config: Config, state: State, now: Long, zone: ZoneId): BlockDecision? {
        // Waiting for the launcher after HOME?
        pending?.let { p ->
            if (windows.any { it.pkg in launchers() } && windows.none { it.pkg == p.pkg }) show(p, "launcher-seen")
            return null
        }
        val ctx = EvalContext(now, zone, usage = state.usage, targets = config.targets, groups = config.groups, pauses = state.pauses)
        val l = locks()
        val d = windows.firstNotNullOfOrNull { decide(it, config, ctx, l) } ?: return null
        val t = SystemClock.elapsedRealtime()
        if (d.pkg == lastBlockPkg && t - lastBlockAt < REBLOCK_GAP_MS) return null // one block per storm of events
        lastBlockAt = t
        lastBlockPkg = d.pkg
        val home = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        pending = d
        main.removeCallbacks(fallback)
        main.postDelayed(fallback, LAUNCHER_WAIT_MS)
        DebugBus.event("BLOCK ${d.reason} ${d.pkg}${d.host?.let { " · $it" } ?: ""} home=$home")
        return d
    }

    private fun show(d: BlockDecision, via: String) {
        pending = null
        main.removeCallbacks(fallback)
        val r = runCatching { service.startActivity(BlockActivity.intent(service, d.reason, d.targetId, d.pkg, d.host, d.until)) }
        DebugBus.event("BLOCK SCREEN via=$via ok=${r.isSuccess}${r.exceptionOrNull()?.let { " $it" } ?: ""}")
    }

    fun stop() {
        main.removeCallbacks(fallback)
        pending = null
    }

    private companion object {
        const val LAUNCHER_WAIT_MS = 700L
        const val REBLOCK_GAP_MS = 800L
    }
}
