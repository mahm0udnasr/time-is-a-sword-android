package com.mahm0udnasr.sword.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mahm0udnasr.sword.core.quotes.Quote
import com.mahm0udnasr.sword.core.text.POEM_STRETCH
import com.mahm0udnasr.sword.core.text.hemistichs
import com.mahm0udnasr.sword.core.text.kashidaSlots
import com.mahm0udnasr.sword.core.text.stretch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A reminder: as verse if it is poetry with halves, else centred text (port of renderReminderText()). */
@Composable
fun ReminderText(quote: Quote, style: TextStyle, modifier: Modifier = Modifier) {
    val halves = remember(quote.text) { if (quote.type == "poetry") hemistichs(quote.text) else emptyList() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (halves.size < 2) {
            Text(quote.text, style = style, textAlign = TextAlign.Center, modifier = modifier)
        } else {
            PoemText(halves, style, modifier)
        }
    }
}

/**
 * A poem laid out like a printed diwan (port of justifyPoem(), step 10.1):
 * each verse on two lines, the first half at the right, the second at the
 * left, every half stretched with kashida to the same width — about 15% wider
 * than the longest half, never wider than the room. Re-justified whenever the
 * size or font changes (BoxWithConstraints).
 */
@Composable
private fun PoemText(halves: List<String>, style: TextStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fontPx = with(density) { style.fontSize.toPx() }
        // The offset between the halves: 2.5em, 1em on narrow screens (base.css).
        val offsetPx = fontPx * if (maxWidth < 560.dp) 1f else 2.5f
        val roomPx = (constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: Int.MAX_VALUE).toFloat()
        val laid = remember(halves, style, roomPx, offsetPx) { justify(halves, measurer, style, roomPx - offsetPx - 4f) }
        val widthDp = with(density) { min(laid.target + offsetPx, roomPx).toDp() }
        val offsetDp = with(density) { offsetPx.toDp() }
        val rowGap = with(density) { (fontPx * 0.15f).toDp() }
        val verseGap = with(density) { (fontPx * 0.9f).toDp() }
        Column(Modifier.width(widthDp), verticalArrangement = Arrangement.spacedBy(verseGap)) {
            laid.lines.chunked(2).forEach { verse ->
                Column(Modifier.width(widthDp), verticalArrangement = Arrangement.spacedBy(rowGap)) {
                    // RTL: Start = right edge (sadr), End = left edge (ajuz).
                    Text(verse[0], style = laid.style, modifier = Modifier.align(Alignment.Start).padding(end = offsetDp))
                    verse.getOrNull(1)?.let { Text(it, style = laid.style, modifier = Modifier.align(Alignment.End).padding(start = offsetDp)) }
                }
            }
        }
    }
}

private class Laid(val lines: List<String>, val target: Float, val style: TextStyle)

/** Smallest font a poem is shrunk to so its halves stay on one line each. */
private const val MIN_SCALE = 0.6f

/**
 * Plan the tatweels: aim, measure, correct (same loop as the extension).
 * A phone is narrower than a browser window: when the longest half doesn't
 * fit, the font shrinks first (down to [MIN_SCALE]) so no half wraps.
 */
private fun justify(halves: List<String>, measurer: TextMeasurer, base: TextStyle, room: Float): Laid {
    var style = base
    fun width(s: String) = measurer.measure(s, style, softWrap = false, maxLines = 1).size.width.toFloat()
    var natural = halves.map(::width)
    var longest = natural.maxOrNull() ?: 0f
    if (longest <= 0f) return Laid(halves, 0f, style)
    if (longest > room && room > 0f) {
        val k = maxOf(MIN_SCALE, room / longest * 0.98f)
        style = base.copy(fontSize = base.fontSize * k, lineHeight = if (base.lineHeight.isSp) base.lineHeight * k else base.lineHeight)
        natural = halves.map(::width)
        longest = natural.maxOrNull() ?: 0f
    }
    val target = max(longest, min(longest * POEM_STRETCH, room))
    val lines = halves.mapIndexed { i, base ->
        val nat = natural[i]
        if (target - nat < 2f) return@mapIndexed base
        val slots = kashidaSlots(base)
        if (slots.isEmpty()) return@mapIndexed base
        val per = max(1f, width(stretch(base, 1, slots).text) - nat)
        var n = max(0, ((target - nat) / per).roundToInt())
        var out = stretch(base, n, slots)
        for (tries in 0 until 6) {
            out = stretch(base, n, slots)
            val diff = target - width(out.text)
            if (abs(diff) < per / 2 || out.used < n) break
            n = max(0, n + (diff / per).roundToInt())
        }
        while (n > 0 && width(out.text) > target + 0.5f) out = stretch(base, --n, slots)
        out.text
    }
    return Laid(lines, target, style)
}
