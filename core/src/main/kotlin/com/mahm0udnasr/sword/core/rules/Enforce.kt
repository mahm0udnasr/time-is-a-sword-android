package com.mahm0udnasr.sword.core.rules

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.shorts.SHORTS_TARGET_ID

/** Why a window is blocked (what the block screen shows and whether a pause is offered). */
enum class BlockReason {
    /** The target's own rule (or its group's): always blocked today. */
    BLOCKED,

    /** Daily budget used up. */
    LIMIT,

    /** Outside the allowed hours. */
    SCHEDULE,

    /** A browser we can't read while web rules are active (D13). */
    BROWSER,

    /** Salah First lock (Phase 7). */
    PRAYER,

    /** Automatic date & time is off (D12, Phase 8). */
    CLOCK,

    /** A protected Settings page (Phase 8). */
    GUARD,

    /** The Short-videos target's rule (Phase 12). */
    SHORTS,
    ;

    /** Pauses only lift the target's own rules (§8.6); short videos are a target too. */
    val pausable: Boolean get() = this == BLOCKED || this == LIMIT || this == SCHEDULE || this == SHORTS
}

/** One window to judge: an app, or a browser with the host it shows. */
data class WindowInfo(
    val pkg: String,
    val host: String? = null,
    val browser: Boolean = false,
    val unsupportedBrowser: Boolean = false,
    /** Self, keyboard, System UI, launcher, dialer: never blocked (rule 9). */
    val ignored: Boolean = false,
    /** A protected Settings page of this app is shown in it (§10.2). */
    val guarded: Boolean = false,
    /** The short-video section shown in it (Phase 12), or null. */
    val section: String? = null,
    /** True for a player inside a bigger app: leaving it with Back is enough (Q9). */
    val sectionInApp: Boolean = false,
)

data class BlockDecision(
    val reason: BlockReason,
    val pkg: String,
    val host: String? = null,
    val targetId: String? = null,
    val until: Long? = null,
    val groupName: String? = null,
    /** Short videos inside an app: press Back first; the block screen only if that fails (Q9). */
    val soft: Boolean = false,
)

/** Phone-wide signals; defaults = not active. */
data class Locks(
    val prayerLocked: Boolean = false,
    val prayerAllows: (String) -> Boolean = { true },
    /** False while automatic date & time is off and the user requires it (D12). */
    val clockTrusted: Boolean = true,
)

/**
 * Should this window be blocked, and why? Decision order of plan §8.4
 * (port of the enforcer's per-tab check, step 4.1):
 *  1. ignored packages → never
 *  2. Settings guard
 *  3. Salah First: every app not allowed by the allowlist, every website
 *  4. clock untrusted → enabled limit/schedule targets
 *  5. the target's rules (evaluate)
 *  6. unsupported browser while any enabled target has a domain
 */
fun decide(w: WindowInfo, config: Config, ctx: EvalContext, locks: Locks = Locks()): BlockDecision? {
    if (w.ignored) return null
    if (w.guarded) return BlockDecision(BlockReason.GUARD, w.pkg)
    if (locks.prayerLocked && (w.browser || !locks.prayerAllows(w.pkg))) return BlockDecision(BlockReason.PRAYER, w.pkg, w.host)

    val target = if (w.browser) findTargetByHost(w.host, config.targets)?.target else findTargetByPackage(w.pkg, config.targets)
    if (target != null && target.enabled) {
        if (!locks.clockTrusted && target.rules.any { it.mode.hasLimit || it.mode.hasSchedule }) {
            return BlockDecision(BlockReason.CLOCK, w.pkg, w.host, target.id)
        }
        val v = evaluate(target, ctx)
        if (v is Verdict.Blocked) {
            val reason = when (v.reason) {
                RuleReason.BLOCKED -> BlockReason.BLOCKED
                RuleReason.LIMIT -> BlockReason.LIMIT
                RuleReason.SCHEDULE -> BlockReason.SCHEDULE
            }
            return BlockDecision(reason, w.pkg, w.host, target.id, v.until, v.group?.name)
        }
    }

    // Short videos (Phase 12): after the app's own rules, so a blocked app stays blocked whole.
    if (w.section != null) {
        val st = config.targets.firstOrNull { it.id == SHORTS_TARGET_ID && it.enabled && w.section in it.sections }
        if (st != null) {
            if (!locks.clockTrusted && st.rules.any { it.mode.hasLimit || it.mode.hasSchedule }) {
                return BlockDecision(BlockReason.CLOCK, w.pkg, w.host, st.id)
            }
            val v = evaluate(st, ctx)
            if (v is Verdict.Blocked) return BlockDecision(BlockReason.SHORTS, w.pkg, w.host, st.id, v.until, v.group?.name, soft = w.sectionInApp)
        }
    }

    if (w.unsupportedBrowser && webRulesActive(config)) return BlockDecision(BlockReason.BROWSER, w.pkg)
    return null
}

/** Unsupported browsers are blocked only while it matters: the switch is on and some enabled target has a website. */
fun webRulesActive(config: Config): Boolean =
    config.protection.blockUnsupportedBrowsers && config.targets.any { it.enabled && it.domains.isNotEmpty() }
