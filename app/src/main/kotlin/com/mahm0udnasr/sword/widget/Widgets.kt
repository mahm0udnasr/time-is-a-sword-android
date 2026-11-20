package com.mahm0udnasr.sword.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import com.mahm0udnasr.sword.ui.MainActivity
import com.mahm0udnasr.sword.ui.theme.DarkTokens
import com.mahm0udnasr.sword.ui.theme.LightTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widgets (after v1.0.0, user request): "Today" (usage) and
 * "Prayer times". Built with Jetpack Glance; they follow the phone's
 * light/dark mode with the app's colour tokens.
 */
object Widgets {
    /** MainActivity extra: the screen to open ("overview", "salah"). */
    const val EXTRA_ROUTE = "route"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastUpdate = 0L

    /**
     * Refresh both widgets. Called by the service on every flush (every 30 s while
     * the screen is on) and at prayer boundaries; throttled to once a minute unless
     * [force], so the countdown stays fresh while the phone is used and nothing
     * runs while it sleeps (Android's 30-minute update is the fallback).
     */
    fun request(ctx: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastUpdate < MIN_GAP_MS) return
        lastUpdate = now
        val app = ctx.applicationContext
        scope.launch {
            runCatching { TodayWidget().updateAll(app) }
            runCatching { PrayerWidget().updateAll(app) }
        }
    }

    fun openIntent(ctx: Context, route: String): Intent =
        Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private const val MIN_GAP_MS = 60_000L
}

/** The app's colour tokens for widgets: day = light, night = dark. */
internal object WColors {
    private fun cp(day: Color, night: Color) = ColorProvider(day = day, night = night)
    val bg = cp(LightTokens.bg, DarkTokens.bg)
    val surface = cp(LightTokens.surface, DarkTokens.surface)
    val text = cp(LightTokens.text, DarkTokens.text)
    val muted = cp(LightTokens.muted, DarkTokens.muted)
    val accent = cp(LightTokens.accent, DarkTokens.accent)
    val accentSoft = cp(LightTokens.accentSoft, DarkTokens.accentSoft)
    val gold = cp(LightTokens.gold, DarkTokens.gold)
    val danger = cp(LightTokens.danger, DarkTokens.danger)
    val dangerSoft = cp(LightTokens.dangerSoft, DarkTokens.dangerSoft)
}
