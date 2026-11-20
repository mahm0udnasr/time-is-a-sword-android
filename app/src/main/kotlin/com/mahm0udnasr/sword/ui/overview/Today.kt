package com.mahm0udnasr.sword.ui.overview

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.Group
import com.mahm0udnasr.sword.core.model.State
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.core.rules.activeRule
import com.mahm0udnasr.sword.core.rules.dayKey
import com.mahm0udnasr.sword.core.rules.evaluate
import com.mahm0udnasr.sword.core.rules.groupUsedSeconds
import com.mahm0udnasr.sword.core.rules.membersOf
import com.mahm0udnasr.sword.core.rules.nextResetAt
import com.mahm0udnasr.sword.core.rules.todayLimitSec
import java.time.ZoneId

/** Today for one target (port of getStatus() rows in tracker.js). */
data class TargetToday(
    val target: Target,
    val usedSec: Double,
    val limitSec: Double?,
    val verdict: Verdict,
    val groupName: String?,
    val hasRuleToday: Boolean,
)

data class GroupToday(val group: Group, val usedSec: Double, val limitSec: Double?, val members: List<Target>)

data class Today(
    val targets: List<TargetToday>,
    val groups: List<GroupToday>,
    val totalSec: Double,
    val pausesLeft: Int,
    val resetAt: Long,
)

fun today(config: Config, state: State, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Today {
    val rh = Defaults.RESET_HOUR
    val ctx = EvalContext(now, zone, rh, state.usage, config.targets, config.groups, state.pauses)
    val day = state.usage[dayKey(now, zone, rh)]
    val seconds = day?.seconds.orEmpty()
    val rows = config.targets.map { t ->
        val group = t.groupId?.let { id -> config.groups.firstOrNull { it.id == id } }
        TargetToday(
            target = t,
            usedSec = seconds[t.id] ?: 0.0,
            limitSec = if (t.enabled) todayLimitSec(t, now, zone, rh) else null,
            verdict = evaluate(t, ctx),
            groupName = group?.takeIf { it.enabled && t.enabled }?.name,
            hasRuleToday = t.enabled && activeRule(t, now, zone, rh) != null,
        )
    }
    val groups = config.groups.filter { it.enabled }.map { g ->
        GroupToday(g, groupUsedSeconds(g, config.targets, state.usage, now, zone, rh), todayLimitSec(g, now, zone, rh), membersOf(g, config.targets))
    }
    return Today(
        targets = rows,
        groups = groups,
        totalSec = seconds.values.sum(),
        pausesLeft = maxOf(0, Defaults.PAUSE_MAX_PER_DAY - (day?.pausesUsed ?: 0)),
        resetAt = nextResetAt(now, zone, rh),
    )
}
