package com.mahm0udnasr.sword.core.prayer

import com.batoulapps.adhan2.CalculationMethod

/**
 * Calculation methods: the extension's Aladhan ids mapped to the Adhan
 * library's presets. Only methods both support are offered (plan §8.7); the
 * extension's 8 (Gulf), 12 (France), 18 (Tunisia), 19 (Algeria), 21 (Morocco)
 * and 23 (Jordan) have no Adhan preset and are left out. 15 (Moonsighting
 * Committee) is left out too: Adhan adds +5 min Dhuhr / +3 min Maghrib and a
 * seasonal Fajr that Aladhan does not, 3–6 min apart in Cairo (PrayerCalcTest).
 * Each mapping is checked against stored Aladhan answers in PrayerCalcTest.
 */
object Methods {
    private val map: Map<Int, CalculationMethod> = linkedMapOf(
        5 to CalculationMethod.EGYPTIAN,
        4 to CalculationMethod.UMM_AL_QURA,
        3 to CalculationMethod.MUSLIM_WORLD_LEAGUE,
        2 to CalculationMethod.NORTH_AMERICA,
        1 to CalculationMethod.KARACHI,
        9 to CalculationMethod.KUWAIT,
        10 to CalculationMethod.QATAR,
        16 to CalculationMethod.DUBAI,
        13 to CalculationMethod.TURKEY,
        11 to CalculationMethod.SINGAPORE,
    )

    /** Offered in the settings screen, most common in the region first (extension order). */
    val SUPPORTED: List<Int> = map.keys.toList()

    internal fun adhan(aladhanId: Int): CalculationMethod =
        map[aladhanId] ?: map.getValue(PrayerSettings.DEFAULT_METHOD)
}
