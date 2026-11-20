package com.mahm0udnasr.sword.core.prayer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

@Serializable
enum class Prayer {
    @SerialName("fajr") FAJR,
    @SerialName("dhuhr") DHUHR,
    @SerialName("asr") ASR,
    @SerialName("maghrib") MAGHRIB,
    @SerialName("isha") ISHA,
}

@Serializable
data class Coords(val lat: Double, val lng: Double)

@Serializable
data class PrayerToggle(val on: Boolean = true, val adjust: Int = 0)

/**
 * "Salah First" settings (port of DEFAULT_PRAYER in prayer.js).
 *
 * On the phone, times are calculated locally (D8), so a typed [address] is
 * turned into [coords] once (plus the location's [timezone]); only coords are
 * needed afterwards. [address] is kept for display and for cfgKey().
 */
@Serializable
data class PrayerSettings(
    val enabled: Boolean = false,
    val address: String = "",
    /** From "Use my location" or the one-time city lookup; rounded to 2 decimals. */
    val coords: Coords? = null,
    /** IANA zone of the location, e.g. "Africa/Cairo"; null = the phone's zone. */
    val timezone: String? = null,
    /** Aladhan method id (default 5 = Egyptian General Authority of Survey). */
    val method: Int = DEFAULT_METHOD,
    /** Asr: 0 = Shafi'i/Maliki/Hanbali, 1 = Hanafi. */
    val school: Int = 0,
    /** Minutes locked before the prayer time. */
    val before: Int = 5,
    /** Minutes locked after the prayer time. */
    val after: Int = 15,
    val prayers: Map<Prayer, PrayerToggle> = Prayer.entries.associateWith { PrayerToggle() },
) {
    fun toggle(p: Prayer): PrayerToggle = prayers[p] ?: PrayerToggle()

    companion object {
        const val DEFAULT_METHOD = 5
    }
}

/** Fill in defaults and reject out-of-range numbers (port of normalizePrayer()). */
fun normalizePrayer(raw: PrayerSettings): PrayerSettings {
    fun int(v: Int, lo: Int, hi: Int, d: Int) = if (v in lo..hi) v else d
    return PrayerSettings(
        enabled = raw.enabled,
        address = raw.address.trim(),
        coords = normalizeCoords(raw.coords),
        timezone = raw.timezone?.takeIf { it.isNotBlank() },
        method = if (raw.method in Methods.SUPPORTED) raw.method else PrayerSettings.DEFAULT_METHOD,
        school = if (raw.school == 1) 1 else 0,
        before = int(raw.before, 0, 60, 5),
        after = int(raw.after, 0, 120, 15),
        prayers = Prayer.entries.associateWith { p ->
            val t = raw.prayers[p] ?: PrayerToggle()
            PrayerToggle(on = t.on, adjust = int(t.adjust, -60, 60, 0))
        },
    )
}

/** Valid coordinates rounded to 2 decimals (about 1 km), or null. */
fun normalizeCoords(c: Coords?): Coords? {
    if (c == null) return null
    val (lat, lng) = c
    if (!lat.isFinite() || !lng.isFinite() || abs(lat) > 90 || abs(lng) > 180) return null
    // Math.round semantics (half up), like the extension.
    fun r(x: Double) = Math.round(x * 100) / 100.0
    return Coords(r(lat), r(lng))
}

/** True if a location is set (coordinates or an address). */
fun hasLocation(p: PrayerSettings): Boolean = p.coords != null || p.address.isNotEmpty()

/** Everything that changes the times themselves (not the lock length). */
fun cfgKey(p: PrayerSettings): String {
    val where = p.coords?.let { "@${num(it.lat)},${num(it.lng)}" } ?: p.address.lowercase()
    return listOf(where, p.method.toString(), p.school.toString()).joinToString("|")
}

/** Number formatting like JS String(n): 30.0 → "30", 30.04 → "30.04". */
private fun num(x: Double): String =
    if (x == x.roundToLong().toDouble()) x.roundToLong().toString() else x.toString()
