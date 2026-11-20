package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.model.DayUsage
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.model.TimeWindow
import com.mahm0udnasr.sword.core.model.makeTarget
import java.time.ZoneId
import java.time.ZonedDateTime

/** The extension's tests ran in Egypt's local time; these use it explicitly. */
val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")

/** Epoch ms of 2026-11-[d] h:mi:s in Cairo (the extension tests' T()). */
fun T(d: Int, h: Int, mi: Int = 0, s: Int = 0, month: Int = 11, zone: ZoneId = CAIRO): Long =
    ZonedDateTime.of(2026, month, d, h, mi, s, 0, zone).toInstant().toEpochMilli()

val ALL = (0..6).toSet()

fun R(days: Set<Int>, mode: Mode, limitMinutes: Int = 20, windows: List<TimeWindow> = emptyList()) =
    Rule(days, mode, limitMinutes, windows)

fun W(start: String, end: String) = TimeWindow(start, end)

/** A target whose id and name are its domain, like the extension's sites. */
fun site(domain: String, enabled: Boolean = true) = makeTarget(id = domain, name = domain, domains = listOf(domain), enabled = enabled)

fun usageOf(day: String, vararg seconds: Pair<String, Double>) = mapOf(day to DayUsage(seconds = mapOf(*seconds)))
