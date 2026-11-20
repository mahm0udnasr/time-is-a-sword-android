package com.mahm0udnasr.sword.core.quotes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** One reminder from data/quotes.json or data/salah.json. type: quran | hadith | athar | poetry. */
data class Quote(val id: String, val text: String, val source: String, val type: String)

@Serializable
private data class RawQuote(val text: String, val source: String = "", val type: String = "")

/** The two reminder files, with their stable id prefixes ("q0…", "s0…"). */
enum class QuoteFile(val asset: String, val prefix: String) {
    REMINDERS("quotes.json", "q"),
    SALAH("salah.json", "s"),
}

private val json = Json { ignoreUnknownKeys = true }

/** Parse a reminder file; each entry gets a stable id: prefix + index. */
fun parseQuotes(text: String, file: QuoteFile): List<Quote> =
    json.decodeFromString<List<RawQuote>>(text).mapIndexed { i, q -> Quote("${file.prefix}$i", q.text, q.source, q.type) }

/** A random reminder, never the same as [lastId] (unless it is the only one). Null if empty. */
fun pickQuote(quotes: List<Quote>, lastId: String?, random: Random = Random.Default): Quote? {
    if (quotes.isEmpty()) return null
    val pool = if (quotes.size > 1) quotes.filter { it.id != lastId } else quotes
    return pool[random.nextInt(pool.size)]
}
