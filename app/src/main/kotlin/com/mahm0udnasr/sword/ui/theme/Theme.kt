package com.mahm0udnasr.sword.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Theme

/** Colour tokens from the extension's shared/base.css (light and dark). Phase 5 builds the full theme on these. */
@Immutable
data class SwordColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val gold: Color,
    val danger: Color,
    val dangerSoft: Color,
)

val LightTokens = SwordColors(
    bg = Color(0xFFF3F6FA), surface = Color(0xFFFFFFFF), text = Color(0xFF0F172A), muted = Color(0xFF5A6578),
    accent = Color(0xFF1A6F88), accentSoft = Color(0xFFE0EFF5), accentText = Color(0xFFFFFFFF),
    gold = Color(0xFF8A5C10), danger = Color(0xFFB4382A), dangerSoft = Color(0xFFFBE8E5),
)

val DarkTokens = SwordColors(
    bg = Color(0xFF0B1220), surface = Color(0xFF111A2B), text = Color(0xFFE6EAF2), muted = Color(0xFF8B97AB),
    accent = Color(0xFF5FB6C9), accentSoft = Color(0xFF13304A), accentText = Color(0xFF071019),
    gold = Color(0xFFE3B65E), danger = Color(0xFFF08A7A), dangerSoft = Color(0xFF3B1E27),
)

/** Reminders (Quran, hadith, poetry). */
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))

/** Interface text. */
val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

private val base = androidx.compose.material3.Typography()

/** Interface text in IBM Plex Sans Arabic (the extension's UI font). */
val PlexTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = PlexArabic), displayMedium = base.displayMedium.copy(fontFamily = PlexArabic),
    displaySmall = base.displaySmall.copy(fontFamily = PlexArabic), headlineLarge = base.headlineLarge.copy(fontFamily = PlexArabic),
    headlineMedium = base.headlineMedium.copy(fontFamily = PlexArabic), headlineSmall = base.headlineSmall.copy(fontFamily = PlexArabic),
    titleLarge = base.titleLarge.copy(fontFamily = PlexArabic), titleMedium = base.titleMedium.copy(fontFamily = PlexArabic),
    titleSmall = base.titleSmall.copy(fontFamily = PlexArabic), bodyLarge = base.bodyLarge.copy(fontFamily = PlexArabic),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlexArabic), bodySmall = base.bodySmall.copy(fontFamily = PlexArabic),
    labelLarge = base.labelLarge.copy(fontFamily = PlexArabic), labelMedium = base.labelMedium.copy(fontFamily = PlexArabic),
    labelSmall = base.labelSmall.copy(fontFamily = PlexArabic),
)

val LocalSwordColors = staticCompositionLocalOf { LightTokens }

@Composable
fun isDark(theme: Theme): Boolean = when (theme) {
    Theme.SYSTEM -> isSystemInDarkTheme()
    Theme.LIGHT -> false
    Theme.DARK -> true
}

/** The app theme: follows the user's System/Light/Dark choice. */
@Composable
fun SwordTheme(theme: Theme = Theme.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDark(theme)
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) {
        darkColorScheme(
            primary = t.accent, onPrimary = t.accentText, primaryContainer = t.accentSoft, onPrimaryContainer = t.accent,
            secondary = t.accent, onSecondary = t.accentText, secondaryContainer = t.accentSoft, onSecondaryContainer = t.accent,
            tertiary = t.gold, background = t.bg, onBackground = t.text, surface = t.surface, onSurface = t.text,
            surfaceVariant = t.accentSoft, onSurfaceVariant = t.muted, outline = t.muted, outlineVariant = t.accentSoft,
            surfaceContainer = t.surface, surfaceContainerLow = t.surface, surfaceContainerHigh = t.surface, surfaceContainerHighest = t.accentSoft,
            error = t.danger, errorContainer = t.dangerSoft,
        )
    } else {
        lightColorScheme(
            primary = t.accent, onPrimary = t.accentText, primaryContainer = t.accentSoft, onPrimaryContainer = t.accent,
            secondary = t.accent, onSecondary = t.accentText, secondaryContainer = t.accentSoft, onSecondaryContainer = t.accent,
            tertiary = t.gold, background = t.bg, onBackground = t.text, surface = t.surface, onSurface = t.text,
            surfaceVariant = t.accentSoft, onSurfaceVariant = t.muted, outline = t.muted, outlineVariant = t.accentSoft,
            surfaceContainer = t.surface, surfaceContainerLow = t.surface, surfaceContainerHigh = t.surface, surfaceContainerHighest = t.accentSoft,
            error = t.danger, errorContainer = t.dangerSoft,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSwordColors provides t) {
        MaterialTheme(colorScheme = scheme, typography = PlexTypography, content = content)
    }
}
