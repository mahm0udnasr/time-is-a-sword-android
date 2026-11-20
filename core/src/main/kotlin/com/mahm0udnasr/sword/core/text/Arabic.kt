package com.mahm0udnasr.sword.core.text

import java.text.Normalizer

/*
 * Normalization for the "type the reminder" check (port of shared/arabic.js).
 *
 * Differences that are ignored:
 *   - tashkeel (harakat, tanween, shadda, sukun, dagger alef, Quranic marks)
 *   - tatweel (ـ)
 *   - أ إ آ ٱ → ا ,  ى → ي ,  ة → ه
 *   - extra spaces (any run of whitespace counts as one; leading/trailing ignored)
 *   - the verse separator "..." / "…" in poetry (counts as a space)
 * Everything else (letters, punctuation) must match.
 */

// U+0610–061A Quranic signs, U+064B–065F harakat, U+0670 dagger alef,
// U+06D6–06ED Quranic annotation marks, U+0640 tatweel.
private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
private val ALEFS = Regex("[أإآٱ]")
private val ELLIPSIS = Regex("\\.{3}|…")

// Exactly what JavaScript's \s matches (so behaviour equals the extension). Written
// out instead of (?U)\s: Android's ICU regex engine rejects the (?U) flag.
private const val JS_SPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
private val SPACES = Regex("[$JS_SPACE]+")
private val TRAILING_SPACE = Regex("[$JS_SPACE]$")

fun normalizeArabic(text: String?): String =
    Normalizer.normalize(text ?: "", Normalizer.Form.NFC)
        .replace(DIACRITICS, "")
        .replace(ALEFS, "ا")
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(ELLIPSIS, " ")
        .replace(SPACES, " ")
        .trim(' ')

/** True if what was typed matches the reminder text, ignoring the above. */
fun matchesReminder(typed: String, original: String): Boolean = normalizeArabic(typed) == normalizeArabic(original)

/**
 * @property correctWords words of the original typed correctly so far
 * @property wrongWordIndex the first finished word that differs, or null
 * @property expected the original word at [wrongWordIndex], or null
 */
data class TypingProgress(val correctWords: Int, val totalWords: Int, val wrongWordIndex: Int?, val expected: String?)

/** Live feedback while typing (port of typingProgress()). */
fun typingProgress(typed: String, original: String): TypingProgress {
    val t = normalizeArabic(typed).split(' ').filter { it.isNotEmpty() }
    val o = normalizeArabic(original).split(' ')
    var ok = 0
    while (ok < t.size && ok < o.size && t[ok] == o[ok]) ok++
    // The word being typed right now may be an unfinished prefix: not an error yet.
    val typingLast = ok == t.size - 1 && o.getOrNull(ok)?.startsWith(t[ok]) == true && !TRAILING_SPACE.containsMatchIn(typed)
    val wrongWord = if (ok < t.size && !typingLast) ok else null
    return TypingProgress(ok, o.size, wrongWord, wrongWord?.let { o[it] })
}
