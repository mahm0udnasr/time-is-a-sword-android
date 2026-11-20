package com.mahm0udnasr.sword.core

import com.mahm0udnasr.sword.core.prayer.Coords
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerCalc
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity of the on-phone Adhan calculation with the Aladhan API (plan §8.7):
 * stored answers of api.aladhan.com for Cairo and Kafr El-Zayat on 5 dates
 * (winter, spring, DST summer, DST end, the extension's test date), every
 * supported method, and the Hanafi Asr. Allowed difference: 2 minutes.
 */
class PrayerCalcTest {
    @Serializable
    data class Case(
        val place: String, val lat: Double, val lng: Double, val date: String,
        val method: Int, val school: Int, val zone: String, val times: Map<String, String>,
    )

    private val cases: List<Case> = Json.decodeFromString(
        javaClass.getResource("/aladhan_parity.json")!!.readText(),
    )

    @Test
    fun `Adhan matches Aladhan within 2 minutes`() {
        val fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val problems = mutableListOf<String>()
        var worst = 0L
        for (c in cases) {
            val zone = ZoneId.of(c.zone)
            val date = LocalDate.parse(c.date, fmt)
            val p = PrayerSettings(coords = Coords(c.lat, c.lng), method = c.method, school = c.school, timezone = c.zone)
            val day = PrayerCalc.day(p, date, zone)!!
            for (prayer in Prayer.entries) {
                val expected = date.atTime(LocalTime.parse(c.times.getValue(prayer.name.lowercase()))).atZone(zone).toInstant().toEpochMilli()
                val diffMin = abs(day.times.getValue(prayer) - expected) / 60_000
                worst = maxOf(worst, diffMin)
                if (diffMin > 2) {
                    val got = Instant.ofEpochMilli(day.times.getValue(prayer)).atZone(zone).toLocalTime()
                    problems += "${c.place} ${c.date} m${c.method} s${c.school} $prayer: adhan $got vs aladhan ${c.times[prayer.name.lowercase()]}"
                }
            }
        }
        println("Adhan vs Aladhan: ${cases.size} cases, worst difference $worst min")
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `around gives yesterday, today and tomorrow at the location`() {
        val p = PrayerSettings(enabled = true, coords = Coords(30.04, 31.24), timezone = "Africa/Cairo")
        val days = PrayerCalc.around(p, T(19, 12), ZoneId.of("UTC"))
        assertEquals(listOf("2026-11-18", "2026-11-19", "2026-11-20"), days.map { it.date.toString() })
        assertTrue(days.all { it.zone == "Africa/Cairo" })
    }

    @Test
    fun `no coordinates, no times`() =
        assertEquals(null, PrayerCalc.day(PrayerSettings(address = "Cairo"), LocalDate.of(2026, 11, 19), CAIRO))
}
