package com.mahm0udnasr.sword.core.model

import kotlin.random.Random

/**
 * The project's fixed settings (port of DEFAULT_SETTINGS in storage.js and the
 * tracker constants). Not editable in the UI: fewer knobs to cheat with (D7).
 */
object Defaults {
    const val SCHEMA_VERSION = 1
    const val RESET_HOUR = 0
    val WARNING_MINUTES: List<Int> = listOf(5, 1)
    const val IDLE_THRESHOLD_SEC = 60
    const val FRICTION_COUNTDOWN_SEC = 30
    const val PAUSE_MAX_MINUTES = 5
    const val PAUSE_MAX_PER_DAY = 3

    /** Days of usage history kept, today included. */
    const val HISTORY_DAYS = 7

    /** The longest gap one flush may credit: a bigger gap means the phone slept. */
    const val MAX_GAP_MS = 75_000L

    /** Flush period while the screen is on. */
    const val TICK_MS = 30_000L

    val ALL_DAYS: Set<Int> = (0..6).toSet()
}

/**
 * Short random id with a type prefix, e.g. "t_k3j9x2abc" (port of newId()).
 * Pure: the clock and the random source are passed in.
 */
fun newId(prefix: String, now: Long, random: Random = Random.Default): String {
    val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
    val rnd = (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    return "${prefix}_$rnd${now.toString(36).takeLast(3)}"
}

/** A new target (port of makeSite()); presets are seeded with enabled = false. */
fun makeTarget(
    id: String,
    name: String,
    packages: List<String> = emptyList(),
    domains: List<String> = emptyList(),
    enabled: Boolean = true,
    mode: Mode = Mode.BLOCK,
) = Target(id = id, name = name, enabled = enabled, packages = packages, domains = domains, rules = listOf(Rule.default(mode)))

/** A new group with a shared 30 min/day budget (port of makeGroup()). */
fun makeGroup(id: String, name: String) =
    Group(id = id, name = name, enabled = true, rules = listOf(Rule.default(Mode.LIMIT).copy(limitMinutes = 30)))
