package com.mahm0udnasr.sword.core.text

/**
 * Poetry laid out like a printed diwan (port of shared/poetry.js, step 10.1):
 * each verse on two lines, every half stretched with kashida (tatweel, "ـ")
 * to the same width. This file plans *where* tatweels go; the app measures
 * the text and decides *how many* (ui/reminder/PoemText.kt).
 *
 * In quotes.json the two halves of a verse are separated by " ... " (or "…"),
 * and verses by a line break — or by " — " (5 poems use it; the extension
 * pairs their halves wrongly, so this one deliberately differs).
 */

const val TATWEEL = 'ـ'

/** Split a poem into hemistichs. */
fun hemistichs(text: String): List<String> =
    text.split(HEMI_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

private val HEMI_SPLIT = Regex("""\s*(?:\.{3}|…|\n)\s*|\s+—\s+""")

/** Letters that join only to the letter before them (never to the next one). */
private val RIGHT_JOINING = setOf(0x0622, 0x0623, 0x0624, 0x0625, 0x0627, 0x0629, 0x062F, 0x0630, 0x0631, 0x0632, 0x0648, 0x0671, 0x0698)
private val ALEFS = setOf(0x0622, 0x0623, 0x0625, 0x0627, 0x0671)
private const val HAMZA = 0x0621 // joins nothing
private const val LAM = 0x0644
private const val ALEF = 0x0627

/** Harakat, tanween, shadda, sukun, dagger alef and Quranic marks. */
private fun isMark(cp: Int) = cp in 0x0610..0x061A || cp in 0x064B..0x065F || cp == 0x0670 || cp in 0x06D6..0x06ED
private fun isLetter(cp: Int) = cp in 0x0620..0x064A || cp in 0x066E..0x06D3
private fun joinsNext(cp: Int) = isLetter(cp) && cp != HAMZA && cp !in RIGHT_JOINING
private fun joinsPrev(cp: Int) = isLetter(cp) && cp != HAMZA

/** JS `\s` (the extension splits words on it). */
private fun isSpace(c: Char) = c.isWhitespace() || c == ' ' || c == '﻿'

/**
 * Where a tatweel may go: between a letter that joins forward and the next
 * letter (after any harakat). Skipped: lam + alef (keeps the لا ligature) and
 * the lam of a word-initial definite article (الـدهر looks wrong).
 * Returns string indices grouped by word, each word's list ordered from its
 * end (classic typesetting stretches the last joins first).
 */
fun kashidaSlots(text: String): List<List<Int>> {
    val words = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    var wordStart = 0
    for (i in text.indices) {
        if (isSpace(text[i])) {
            if (current.isNotEmpty()) words += current
            current = mutableListOf()
            wordStart = i + 1
            continue
        }
        val cp = text[i].code
        if (!joinsNext(cp)) continue
        var j = i + 1
        while (j < text.length && isMark(text[j].code)) j++
        if (j >= text.length) continue
        val next = text[j].code
        if (!joinsPrev(next)) continue
        if (cp == LAM && next in ALEFS) continue
        // "ال" at the start of a word (also after و / ف / ب / ك as in "والدهر").
        fun articleAt(k: Int) = k < text.length && text[k].code == ALEF && i == k + 1
        if (cp == LAM && (articleAt(wordStart) || (i == wordStart + 2 && articleAt(wordStart + 1) && text[wordStart] in "وفبك"))) continue
        current += j // insert just before the next letter, after this letter's harakat
    }
    if (current.isNotEmpty()) words += current
    return words.map { it.reversed() }
}

/** @property used how many tatweels went in (less than asked when there is no room) */
data class Stretched(val text: String, val used: Int)

/**
 * Insert [count] tatweels into [text], spread round-robin: first the last join
 * of every word, then the one before it, and so on. They go in pairs ([unit]):
 * a single tatweel is only a couple of pixels wide and reads as noise. At most
 * [maxPerSlot] at one join.
 */
fun stretch(text: String, count: Int, slots: List<List<Int>> = kashidaSlots(text), maxPerSlot: Int = 4, unit: Int = 2): Stretched {
    val order = mutableListOf<Int>()
    val longest = slots.maxOfOrNull { it.size } ?: 0
    for (r in 0 until longest) for (w in slots) if (r < w.size) order += w[r]
    val at = LinkedHashMap<Int, Int>()
    var used = 0
    var round = 0
    while (round * unit < maxPerSlot && used < count) {
        for (i in order) {
            if (used >= count) break
            val add = minOf(unit, count - used, maxPerSlot - (at[i] ?: 0))
            if (add <= 0) continue
            at[i] = (at[i] ?: 0) + add
            used += add
        }
        round++
    }
    val sb = StringBuilder(text)
    for (i in at.keys.sortedDescending()) sb.insert(i, TATWEEL.toString().repeat(at.getValue(i)))
    return Stretched(sb.toString(), used)
}

/** How much wider than its longest half a poem is set (every half gets some kashida). */
const val POEM_STRETCH = 1.15f
