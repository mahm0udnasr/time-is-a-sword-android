package com.mahm0udnasr.sword.prayer

import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.parseAladhanTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Prayer times of a typed city from Aladhan, a month per call
 * (calendarByAddress; port of fetchTimings() in prayer.js). Only the city
 * name, the method and the Asr school are sent.
 *
 * Plan change (2026-11-19): Aladhan's meta.latitude/longitude are now the
 * same placeholder for every address, so a typed city can't be turned into
 * coordinates; its times are fetched instead (D8's documented fallback).
 */
object AladhanApi {
    sealed interface Result {
        /** @property days "YYYY-MM-DD" → prayer → "HH:MM" */
        data class Ok(val days: Map<String, Map<Prayer, String>>, val timezone: String?) : Result
        data class NotFound(val message: String) : Result
        data object Offline : Result
        data class Failed(val message: String) : Result
    }

    private const val BASE = "https://api.aladhan.com/v1/calendarByAddress/"
    private val API_NAMES = mapOf(Prayer.FAJR to "Fajr", Prayer.DHUHR to "Dhuhr", Prayer.ASR to "Asr", Prayer.MAGHRIB to "Maghrib", Prayer.ISHA to "Isha")
    private val DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun month(p: PrayerSettings, ym: YearMonth): Result = withContext(Dispatchers.IO) {
        val url = URL(
            "$BASE${ym.year}/${ym.monthValue}?address=" + URLEncoder.encode(p.address, "UTF-8") +
                "&method=${p.method}&school=${p.school}",
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            parse(code, body)
        } catch (_: UnknownHostException) {
            Result.Offline
        } catch (e: IOException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }

    /** Reads a calendar answer (internal for tests). */
    internal fun parse(httpCode: Int, body: String): Result {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val apiCode = root?.get("code")?.jsonPrimitive?.intOrNull
        val data = root?.get("data")
        if (httpCode !in 200..299 || apiCode != 200) {
            val msg = runCatching { data!!.jsonPrimitive.content }.getOrNull() ?: "HTTP $httpCode"
            return if (httpCode == 400 || apiCode == 400) Result.NotFound(msg) else Result.Failed(msg)
        }
        val list = data as? JsonArray ?: return Result.Failed("Unexpected answer from the prayer-times service.")
        var tz: String? = null
        val days = mutableMapOf<String, Map<Prayer, String>>()
        for (item in list) {
            val o = item as? JsonObject ?: continue
            val dateText = runCatching { o["date"]!!.jsonObject["gregorian"]!!.jsonObject["date"]!!.jsonPrimitive.content }.getOrNull() ?: continue
            val date = runCatching { LocalDate.parse(dateText, DMY) }.getOrNull() ?: continue
            val timings = o["timings"] as? JsonObject ?: continue
            val t = API_NAMES.mapValues { (_, name) -> timings[name]?.jsonPrimitive?.content?.let(::parseAladhanTime) }
            if (t.values.any { it == null }) return Result.Failed("Unexpected answer from the prayer-times service.")
            days[date.toString()] = t.mapValues { it.value!! }
            tz = tz ?: runCatching { o["meta"]!!.jsonObject["timezone"]!!.jsonPrimitive.content }.getOrNull()
        }
        if (days.isEmpty()) return Result.Failed("Unexpected answer from the prayer-times service.")
        return Result.Ok(days, tz)
    }
}
