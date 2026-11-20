package com.mahm0udnasr.sword.core.loosen

import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.cfgKey
import com.mahm0udnasr.sword.core.prayer.normalizePrayer

/**
 * Why [next] weakens Salah First compared with [prev] (empty = not looser).
 * Setting it up while it is off is never looser. (Port of prayerLoosening().)
 */
fun prayerLoosening(prevRaw: PrayerSettings, nextRaw: PrayerSettings): List<LooseReason> {
    val prev = normalizePrayer(prevRaw)
    val next = normalizePrayer(nextRaw)
    if (!prev.enabled) return emptyList()
    if (!next.enabled) return listOf(LooseReason.PrayerOff)
    val reasons = mutableListOf<LooseReason>()
    if (cfgKey(prev) != cfgKey(next)) reasons += LooseReason.PrayerLocationChanged
    if (next.before < prev.before) reasons += LooseReason.PrayerBeforeReduced(prev.before, next.before)
    if (next.after < prev.after) reasons += LooseReason.PrayerAfterReduced(prev.after, next.after)
    for (k in Prayer.entries) {
        val a = prev.toggle(k)
        val b = next.toggle(k)
        if (a.on && !b.on) {
            reasons += LooseReason.PrayerSwitchedOff(k)
        } else if (b.on && a.adjust != b.adjust) {
            reasons += LooseReason.PrayerAdjusted(k, a.adjust, b.adjust)
        }
    }
    return reasons
}
