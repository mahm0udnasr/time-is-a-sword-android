package com.mahm0udnasr.sword.core.rules

import com.mahm0udnasr.sword.core.model.Config

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
    ;

    /** Pauses only lift the target's own rules (§8.6). */
    val pausable: Boolean get() = this == BLOCKED || this == LIMIT || this == SCHEDULE
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
)

data class BlockDecision(
    val reason: BlockReason,
    val pkg: String,
    val host: String? = null,
    val targetId: String? = null,
    val until: Long? = null,
    val groupName: String? = null,
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

    if (w.unsupportedBrowser && webRulesActive(config)) return BlockDecision(BlockReason.BROWSER, w.pkg)
    return null
}

/** Unsupported browsers are blocked only while it matters: the switch is on and some enabled target has a website. */
fun webRulesActive(config: Config): Boolean =
    config.protection.blockUnsupportedBrowsers && config.targets.any { it.enabled && it.domains.isNotEmpty() }
