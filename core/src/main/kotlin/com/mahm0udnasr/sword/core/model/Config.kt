package com.mahm0udnasr.sword.core.model

import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the user chose (DataStore file `config.json`). Every change to it goes
 * through diffState() so looser changes need the friction step.
 */
@Serializable
data class Config(
    val schemaVersion: Int = Defaults.SCHEMA_VERSION,
    val targets: List<Target> = emptyList(),
    val groups: List<Group> = emptyList(),
    val prayer: PrayerSettings = PrayerSettings(),
    /** Theme and language: never need friction. */
    val appearance: Appearance = Appearance(),
    val protection: Protection = Protection(),
)

/** Something that has per-weekday rules: a target or a group. */
sealed interface RuleHolder {
    val id: String
    val name: String
    val enabled: Boolean
    val rules: List<Rule>
}

/**
 * A service to restrict: the Android version of the extension's "site".
 * Usage is counted per target, so the app and the website share one budget.
 */
@Serializable
data class Target(
    override val id: String,
    override val name: String,
    override val enabled: Boolean,
    val groupId: String? = null,
    /** Android package names, e.g. "com.instagram.android". */
    val packages: List<String> = emptyList(),
    /** Normalized domains (see normalizeDomain), e.g. "instagram.com". */
    val domains: List<String> = emptyList(),
    override val rules: List<Rule> = listOf(Rule.default()),
) : RuleHolder

/** Targets sharing one budget; the stricter of the group's and the target's rule wins. */
@Serializable
data class Group(
    override val id: String,
    override val name: String,
    override val enabled: Boolean,
    override val rules: List<Rule>,
) : RuleHolder

@Serializable
enum class Mode {
    @SerialName("block") BLOCK,
    @SerialName("limit") LIMIT,
    @SerialName("schedule") SCHEDULE,
    @SerialName("limit+schedule") LIMIT_SCHEDULE,
    ;

    val hasLimit: Boolean get() = this == LIMIT || this == LIMIT_SCHEDULE
    val hasSchedule: Boolean get() = this == SCHEDULE || this == LIMIT_SCHEDULE
}

/**
 * A rule for some weekdays.
 * @property days 0 = Sunday … 6 = Saturday (the same numbering as JS getDay()).
 */
@Serializable
data class Rule(
    val days: Set<Int>,
    val mode: Mode,
    val limitMinutes: Int = 20,
    val windows: List<TimeWindow> = emptyList(),
) {
    companion object {
        /** A rule that applies every day (port of defaultRule()). */
        fun default(mode: Mode = Mode.BLOCK) = Rule(days = Defaults.ALL_DAYS, mode = mode, limitMinutes = 20)
    }
}

/**
 * "HH:MM" to "HH:MM". An end before the start crosses midnight (22:00–01:00);
 * an end of "00:00" means midnight (21:00–00:00).
 */
@Serializable
data class TimeWindow(val start: String, val end: String)

@Serializable
enum class Theme { @SerialName("system") SYSTEM, @SerialName("light") LIGHT, @SerialName("dark") DARK }

@Serializable
enum class Lang { @SerialName("ar") AR, @SerialName("en") EN }

@Serializable
data class Appearance(val theme: Theme = Theme.SYSTEM, val lang: Lang = Lang.AR)

/** Phone-only anti-cheat switches. Turning any of them off is a looser change. */
@Serializable
data class Protection(
    /** Settings guard (plan §10.2). */
    val guardEnabled: Boolean = true,
    /** Block browsers we can't read while web rules are active (D13). */
    val blockUnsupportedBrowsers: Boolean = true,
    /** Fail closed when automatic date & time is off (D12). */
    val requireAutoTime: Boolean = true,
    /** Extra packages allowed during Salah First (D6). */
    val prayerAllowlist: List<String> = emptyList(),
)
