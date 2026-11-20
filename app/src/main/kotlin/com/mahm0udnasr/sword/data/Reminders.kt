package com.mahm0udnasr.sword.data

import android.content.res.AssetManager
import com.mahm0udnasr.sword.core.quotes.Quote
import com.mahm0udnasr.sword.core.quotes.QuoteFile
import com.mahm0udnasr.sword.core.quotes.parseQuotes
import com.mahm0udnasr.sword.core.quotes.pickQuote
import java.util.concurrent.ConcurrentHashMap

/**
 * Reminders from assets/quotes.json and assets/salah.json (copied unchanged
 * from the extension), parsed once (step 2.6).
 */
class Reminders(private val read: (String) -> String) {
    constructor(assets: AssetManager) : this({ name -> assets.open(name).use { it.readBytes().decodeToString() } })

    private val cache = ConcurrentHashMap<QuoteFile, List<Quote>>()

    fun all(file: QuoteFile): List<Quote> = cache.getOrPut(file) {
        runCatching { parseQuotes(read(file.asset), file) }.getOrElse { emptyList() }
    }

    /**
     * A reminder never the same as the last one from that file; the caller
     * stores the returned id as lastQuoteId / lastSalahId.
     */
    fun pick(file: QuoteFile, lastId: String?): Quote? = pickQuote(all(file), lastId)
}
