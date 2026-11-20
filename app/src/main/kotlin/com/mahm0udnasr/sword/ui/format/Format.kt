package com.mahm0udnasr.sword.ui.format

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.loosen.LooseItem
import com.mahm0udnasr.sword.core.loosen.LooseReason
import com.mahm0udnasr.sword.core.loosen.RuleWhy
import com.mahm0udnasr.sword.core.loosen.Subject
import com.mahm0udnasr.sword.core.model.Lang
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.prayer.Prayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * Display helpers (port of shared/format.js and the text side of i18n.js, step 5.2).
 * Strings keep the extension's {param} placeholders; [tr] fills them by name.
 */

/** Locale for a language choice; Arabic with Western digits, like the extension (ar-u-nu-latn). */
fun localeOf(lang: Lang): Locale = if (lang == Lang.AR) Locale.forLanguageTag("ar-u-nu-latn") else Locale.ENGLISH

fun Context.isArabic(): Boolean = resources.configuration.locales[0].language == "ar"

fun Context.locale(): Locale = if (isArabic()) Locale.forLanguageTag("ar-u-nu-latn") else Locale.ENGLISH

/** A context whose resources use [lang] (for the service's overlay, which has no activity). */
fun Context.withLang(lang: Lang): Context {
    val cfg = Configuration(resources.configuration)
    cfg.setLocale(localeOf(lang))
    cfg.setLayoutDirection(localeOf(lang))
    return createConfigurationContext(cfg)
}

/** getString + named arguments: tr(R.string.x_site_removed, "site" to "x.com"). */
fun Context.tr(@StringRes id: Int, vararg args: Pair<String, Any?>): String {
    var s = getString(id)
    for ((k, v) in args) s = s.replace("{$k}", v.toString())
    return s
}

@Composable
fun tr(@StringRes id: Int, vararg args: Pair<String, Any?>): String = LocalContext.current.tr(id, *args)

/** Unicode isolate so a domain, app name or time stays left-to-right inside Arabic text. */
fun ltr(s: String) = "⁦$s⁩"

/**
 * Human duration (port of formatDuration()).
 *   en: 45 → "45s", 754 → "12m", 3900 → "1h 05m"
 *   ar: 45 → "45 ث", 754 → "12 د", 3900 → "1 س 05 د"
 */
fun Context.formatDuration(seconds: Double, precise: Boolean = false): String {
    val sec = maxOf(0L, kotlin.math.floor(seconds).toLong())
    val sp = if (isArabic()) " " else ""
    fun u(n: Any, @StringRes unit: Int) = "$n$sp${getString(unit)}"
    fun two(n: Long) = n.toString().padStart(2, '0')
    if (sec < 60) return u(sec, R.string.x_dur_s)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    if (h > 0) return "${u(h, R.string.x_dur_h)} ${u(two(m), R.string.x_dur_m)}"
    if (precise && sec < 600) return "${u(m, R.string.x_dur_m)} ${u(two(s), R.string.x_dur_s)}"
    return u(m, R.string.x_dur_m)
}

/** "13:00" / "1:00 م" in the current language (port of formatClock()). */
fun Context.formatClock(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale()))

/** "today 13:00" / "tomorrow 00:00" / "Monday 13:00" (port of formatWhen()). */
fun Context.formatWhen(ts: Long): String {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val time = formatClock(ts)
    return when (d) {
        today -> tr(R.string.x_when_today, "time" to time)
        today.plusDays(1) -> tr(R.string.x_when_tomorrow, "time" to time)
        else -> tr(R.string.x_when_day, "day" to d.format(DateTimeFormatter.ofPattern("EEEE", locale())), "time" to time)
    }
}

private val DAYS_EN = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val DAYS_AR = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

/** Short weekday name, 0 = Sunday. */
fun Context.dayName(d: Int): String = (if (isArabic()) DAYS_AR else DAYS_EN)[d]

/** [0,1,2,3,4] → "Sun–Thu"; [5,6] → "Fri, Sat"; all → "Every day". */
fun Context.describeDays(days: Collection<Int>): String {
    val sorted = days.toSortedSet().toList()
    if (sorted.size == 7) return getString(R.string.x_days_every)
    if (sorted.isEmpty()) return getString(R.string.x_days_none)
    val runs = mutableListOf(intArrayOf(sorted[0], sorted[0]))
    for (d in sorted.drop(1)) if (d == runs.last()[1] + 1) runs.last()[1] = d else runs += intArrayOf(d, d)
    val sep = getString(R.string.x_list_sep)
    return runs.joinToString(sep) { (a, b) ->
        when {
            b - a >= 2 -> "${dayName(a)}–${dayName(b)}"
            a == b -> dayName(a)
            else -> "${dayName(a)}$sep${dayName(b)}"
        }
    }
}

/** "13:00–13:30, 21:00–21:30", each range kept left-to-right on one line. */
private fun Context.describeWindows(r: Rule): String =
    r.windows.joinToString(getString(R.string.x_list_sep)) { ltr("${it.start}⁠–⁠${it.end}") }

fun Context.describeRule(r: Rule): String {
    val days = describeDays(r.days)
    return when (r.mode) {
        Mode.BLOCK -> tr(R.string.x_sum_blocked, "days" to days)
        Mode.LIMIT -> tr(R.string.x_sum_limit, "days" to days, "n" to r.limitMinutes)
        Mode.SCHEDULE -> tr(R.string.x_sum_schedule, "days" to days, "w" to describeWindows(r))
        Mode.LIMIT_SCHEDULE -> tr(R.string.x_sum_limitSchedule, "days" to days, "n" to r.limitMinutes, "w" to describeWindows(r))
    }
}

fun Context.describeRules(rules: List<Rule>, empty: String = getString(R.string.x_sum_noRule)): String =
    if (rules.isEmpty()) empty else rules.joinToString(" · ") { describeRule(it) }

@StringRes
fun modeLabel(m: Mode): Int = when (m) {
    Mode.BLOCK -> R.string.x_mode_block
    Mode.LIMIT -> R.string.x_mode_limit
    Mode.SCHEDULE -> R.string.x_mode_schedule
    Mode.LIMIT_SCHEDULE -> R.string.x_mode_limitSchedule
}

@StringRes
fun prayerName(p: Prayer): Int = when (p) {
    Prayer.FAJR -> R.string.x_prayer_fajr
    Prayer.DHUHR -> R.string.x_prayer_dhuhr
    Prayer.ASR -> R.string.x_prayer_asr
    Prayer.MAGHRIB -> R.string.x_prayer_maghrib
    Prayer.ISHA -> R.string.x_prayer_isha
}

/** One line of the friction reasons list: "Instagram: Sun–Thu: limit raised from 20 to 30 min". */
fun Context.looseText(item: LooseItem, appLabel: (String) -> String = { it }): String {
    val who = when (val s = item.subject) {
        is Subject.OfTarget -> ltr(s.name)
        is Subject.OfGroup -> tr(R.string.x_group_label, "name" to s.name)
        Subject.OfProtection -> getString(R.string.protection_title)
        Subject.OfPrayer -> getString(R.string.x_nav_prayer)
        Subject.OfAppearance -> getString(R.string.x_theme_label)
    }
    return "$who: ${looseReason(item.reason, appLabel)}"
}

fun Context.looseReason(r: LooseReason, appLabel: (String) -> String = { it }): String = when (r) {
    is LooseReason.Rule -> "${describeDays(r.days)}: " + when (val w = r.why) {
        RuleWhy.RuleRemoved -> getString(R.string.x_lo_ruleRemoved)
        RuleWhy.Unblocked -> getString(R.string.x_lo_unblocked)
        RuleWhy.LimitRemoved -> getString(R.string.x_lo_limitRemoved)
        is RuleWhy.LimitRaised -> tr(R.string.x_lo_limitRaised, "a" to w.a, "b" to w.b)
        RuleWhy.HoursRemoved -> getString(R.string.x_lo_hoursRemoved)
        RuleWhy.HoursWidened -> getString(R.string.x_lo_hoursWidened)
    }
    LooseReason.SwitchedOff -> getString(R.string.x_lo_switchedOff)
    LooseReason.Removed -> getString(R.string.x_lo_removed)
    is LooseReason.LeavesGroup -> tr(R.string.x_lo_leavesGroup, "name" to r.groupName)
    is LooseReason.PackageRemoved -> tr(R.string.lo_appRemoved, "app" to ltr(appLabel(r.pkg)))
    is LooseReason.DomainRemoved -> tr(R.string.lo_domainRemoved, "site" to ltr(r.domain))
    is LooseReason.SectionRemoved -> tr(R.string.lo_sectionRemoved, "name" to shortsSectionName(r.section))
    LooseReason.GuardOff -> getString(R.string.lo_guardOff)
    LooseReason.UnsupportedBrowsersAllowed -> getString(R.string.lo_browsersAllowed)
    LooseReason.AutoTimeNotRequired -> getString(R.string.lo_autoTimeOff)
    is LooseReason.PrayerAllowlistAdded -> tr(R.string.lo_allowlistAdded, "app" to ltr(appLabel(r.pkg)))
    LooseReason.PrayerOff -> getString(R.string.x_pl_off)
    LooseReason.PrayerLocationChanged -> getString(R.string.x_pl_location)
    is LooseReason.PrayerBeforeReduced -> tr(R.string.x_pl_before, "a" to r.a, "b" to r.b)
    is LooseReason.PrayerAfterReduced -> tr(R.string.x_pl_after, "a" to r.a, "b" to r.b)
    is LooseReason.PrayerSwitchedOff -> tr(R.string.x_pl_prayerOff, "name" to getString(prayerName(r.prayer)))
    is LooseReason.PrayerAdjusted -> tr(R.string.x_pl_adjust, "name" to getString(prayerName(r.prayer)), "a" to r.a, "b" to r.b)
}

/** A short-video section's name in the app's language (from shorts.json). */
fun Context.shortsSectionName(id: String): String = com.mahm0udnasr.sword.ui.targets.ShortsCatalog.name(this, id)
