package com.mahm0udnasr.sword.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import com.mahm0udnasr.sword.container
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
            update(app, TodayWidgetReceiver::class.java, TodayWidget())
            update(app, PrayerWidgetReceiver::class.java, PrayerWidget())
        }
    }

    /**
     * Each placed widget by its Android id. Not updateAll(): that looks widgets up in
     * Glance's own receiver → class-name map, which an update with renamed classes
     * (R8) can leave pointing nowhere, so nothing was redrawn.
     */
    private suspend fun update(ctx: Context, receiver: Class<*>, widget: androidx.glance.appwidget.GlanceAppWidget) {
        val ids = runCatching {
            android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetIds(android.content.ComponentName(ctx, receiver))
        }.getOrDefault(IntArray(0))
        val gm = androidx.glance.appwidget.GlanceAppWidgetManager(ctx)
        for (id in ids) runCatching {
            val gid = gm.getGlanceIdBy(id)
            // A running Glance session redraws only when its own state changes: bump it.
            androidx.glance.appwidget.state.updateAppWidgetState(ctx, gid) { it[TICK] = System.currentTimeMillis() }
            widget.update(ctx, gid)
        }
    }

    /** Glance state key: when the data was last asked for (read by the widgets so they recompose). */
    val TICK = androidx.datastore.preferences.core.longPreferencesKey("tick")

    /** [ctx] in the app's chosen language, whatever the process has applied so far. */
    fun localized(ctx: Context): Context {
        val lang = ctx.container.config.value.appearance.lang
        val conf = android.content.res.Configuration(ctx.resources.configuration)
        conf.setLocale(java.util.Locale.forLanguageTag(com.mahm0udnasr.sword.ui.AppLocale.tag(lang)))
        return ctx.createConfigurationContext(conf)
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

/**
 * Widget direction. Widgets are laid out by the launcher in *its* direction
 * (the phone's language), not the app's, and Glance can't change that. So when
 * the two differ (Arabic app on an English phone, or the reverse) the widgets
 * mirror themselves: rows run backwards. Text is aligned by the app's language
 * (Left/Right are absolute in Glance, so they need no flipping).
 */
internal class Dir(private val appRtl: Boolean, hostRtl: Boolean) {
    /** Rows (and progress bars) must be reversed: the launcher lays them out the other way round. */
    val flip = appRtl != hostRtl

    /** Where text sits: the reading start of the app's language. */
    val textAlign get() = if (appRtl) androidx.glance.text.TextAlign.Right else androidx.glance.text.TextAlign.Left

    /** Column children at the reading start (Start/End follow the launcher's direction). */
    val start get() = if (flip) androidx.glance.layout.Alignment.Horizontal.End else androidx.glance.layout.Alignment.Horizontal.Start

    /**
     * The launcher's text views take the phone's direction, so mixed text (numbers,
     * «quotes», "هـ") would be ordered as if the line were in the phone's language.
     * A leading RLM/LRM gives each line the app's direction.
     */
    fun t(s: String): String = (if (appRtl) "\u200F" else "\u200E") + s

    /** Items of a row in reading order, laid out for the launcher. */
    fun <T> order(items: List<T>): List<T> = if (flip) items.reversed() else items

    companion object {
        fun of(ctx: Context): Dir {
            val appRtl = ctx.container.config.value.appearance.lang == com.mahm0udnasr.sword.core.model.Lang.AR
            return Dir(appRtl, hostRtl(ctx))
        }

        /**
         * The launcher's direction = the phone's language. Not Resources.getSystem():
         * on Android 13+ the app's own language shows through it (seen on One UI 9:
         * Arabic app, English phone, and it said RTL).
         */
        private fun hostRtl(ctx: Context): Boolean {
            val sys = if (android.os.Build.VERSION.SDK_INT >= 33) {
                ctx.getSystemService(android.app.LocaleManager::class.java).systemLocales[0]
            } else {
                android.content.res.Resources.getSystem().configuration.locales[0]
            } ?: java.util.Locale.getDefault()
            return android.text.TextUtils.getLayoutDirectionFromLocale(sys) == android.view.View.LAYOUT_DIRECTION_RTL
        }
    }
}

/** A Row whose parts are given in reading order and laid out for the app's language. */
@androidx.compose.runtime.Composable
internal fun DirRow(
    dir: Dir,
    modifier: androidx.glance.GlanceModifier = androidx.glance.GlanceModifier,
    vertical: androidx.glance.layout.Alignment.Vertical = androidx.glance.layout.Alignment.CenterVertically,
    parts: List<@androidx.compose.runtime.Composable androidx.glance.layout.RowScope.() -> Unit>,
) {
    androidx.glance.layout.Row(modifier, verticalAlignment = vertical) {
        dir.order(parts).forEach { it() }
    }
}
