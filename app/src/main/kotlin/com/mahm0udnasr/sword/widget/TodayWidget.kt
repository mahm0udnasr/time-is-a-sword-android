package com.mahm0udnasr.sword.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.container
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.system.HealthCheck
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.formatDuration
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.overview.today

/** One target line: name, what's left, and the bar (0..1, null = no limit). */
private data class Line(val name: String, val value: String, val blocked: Boolean, val progress: Float?)

private data class TodayData(
    val total: String,
    val lines: List<Line>,
    val pausesLeft: String,
    val protectionOff: Boolean,
    val empty: Boolean,
)

/**
 * "Today" widget: total time today, each switched-on app/site with what's
 * left (or blocked), pauses left, and a warning if protection is off.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        context.container.config.awaitLoaded()
        context.container.state.awaitLoaded()
        // Built inside the composition: Glance keeps the session alive and an update only
        // recomposes it, so data loaded once outside would stay stale (wrong language,
        // frozen countdown) until the session ends.
        provideContent {
            // Reading the state key makes every Widgets.request() recompose this (see Widgets.update).
            androidx.glance.currentState(Widgets.TICK)
            val ctx = Widgets.localized(context)
            Content(ctx, load(ctx))
        }
    }

    private fun load(ctx: Context): TodayData {
        val c = ctx.container
        val t = today(c.config.value, c.state.value)
        val rows = t.targets.filter { it.hasRuleToday }
            // Most urgent first: blocked, then the least time left, then the rest.
            .sortedWith(compareBy({ !it.verdict.blocked }, { (it.verdict as? Verdict.Allowed)?.remainingSec ?: Double.MAX_VALUE }))
        val lines = rows.map { r ->
            val v = r.verdict
            val value = when {
                v is Verdict.Blocked -> ctx.getString(R.string.w_blocked)
                v is Verdict.Allowed && v.paused && v.blockAt != null -> ctx.tr(R.string.status_paused, "time" to ctx.formatClock(v.blockAt!!))
                v is Verdict.Allowed && v.remainingSec != null -> ctx.tr(R.string.w_left, "dur" to ctx.formatDuration(v.remainingSec!!))
                v is Verdict.Allowed && v.blockKind == BlockKind.SCHEDULE && v.blockAt != null -> ctx.tr(R.string.status_until, "time" to ctx.formatClock(v.blockAt!!))
                else -> ctx.formatDuration(r.usedSec)
            }
            val progress = r.limitSec?.takeIf { it > 0 }?.let { (r.usedSec / it).toFloat().coerceIn(0f, 1f) }
            Line(r.target.name, value, v.blocked, progress)
        }
        return TodayData(
            total = ctx.formatDuration(t.totalSec),
            lines = lines,
            pausesLeft = ctx.tr(R.string.w_pauses, "n" to t.pausesLeft),
            protectionOff = !HealthCheck.serviceEnabled(ctx),
            empty = lines.isEmpty(),
        )
    }

    @Composable
    private fun Content(ctx: Context, d: TodayData) {
        val dir = Dir.of(ctx)
        val size = LocalSize.current
        // Rows that fit: header ~56dp, footer ~28dp, each row ~38dp.
        val rows = ((size.height.value - 84f) / 38f).toInt().coerceIn(1, 6)
        Column(
            GlanceModifier.fillMaxSize().background(WColors.surface).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity(Widgets.openIntent(ctx, "overview"))),
            horizontalAlignment = dir.start,
        ) {
            // Header: [icon] Today ........ 1 h 12 m   (mirrored in Arabic)
            DirRow(
                dir, GlanceModifier.fillMaxWidth(),
                parts = listOf(
                    { Image(ImageProvider(R.drawable.ic_stat_sword), null, GlanceModifier.size(18.dp), colorFilter = androidx.glance.ColorFilter.tint(WColors.accent)) },
                    { Spacer(GlanceModifier.width(6.dp)) },
                    { Text(dir.t(ctx.getString(R.string.w_today)), style = TextStyle(color = WColors.muted, fontSize = 13.sp, textAlign = dir.textAlign), modifier = GlanceModifier.defaultWeight()) },
                    { Text(dir.t(d.total), style = TextStyle(color = WColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)) },
                ),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (d.protectionOff) {
                Text(
                    dir.t(ctx.getString(R.string.banner_off)),
                    style = TextStyle(color = WColors.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = dir.textAlign),
                    modifier = GlanceModifier.fillMaxWidth().background(WColors.dangerSoft).cornerRadius(10.dp).padding(8.dp),
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            if (d.empty) {
                Text(dir.t(ctx.getString(R.string.w_nothing)), style = TextStyle(color = WColors.muted, fontSize = 13.sp, textAlign = dir.textAlign), modifier = GlanceModifier.fillMaxWidth())
            } else {
                d.lines.take(rows).forEach { l -> LineRow(dir, l) }
                if (d.lines.size > rows) {
                    Text(dir.t(ctx.tr(R.string.w_more, "n" to d.lines.size - rows)), style = TextStyle(color = WColors.muted, fontSize = 11.sp, textAlign = dir.textAlign), modifier = GlanceModifier.fillMaxWidth())
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(dir.t(d.pausesLeft), style = TextStyle(color = WColors.muted, fontSize = 11.sp, textAlign = dir.textAlign), modifier = GlanceModifier.fillMaxWidth())
        }
    }

    @Composable
    private fun LineRow(dir: Dir, l: Line) {
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
            // name ........ time left   (mirrored in Arabic)
            DirRow(
                dir, GlanceModifier.fillMaxWidth(),
                parts = listOf(
                    { Text(dir.t(l.name), style = TextStyle(color = WColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = dir.textAlign), maxLines = 1, modifier = GlanceModifier.defaultWeight()) },
                    { Spacer(GlanceModifier.width(8.dp)) },
                    { Text(dir.t(l.value), style = TextStyle(color = if (l.blocked) WColors.danger else WColors.muted, fontSize = 12.sp), maxLines = 1) },
                ),
            )
            if (l.progress != null) {
                Spacer(GlanceModifier.height(4.dp))
                // A bar fills from the reading start. When the launcher runs the other way,
                // draw the empty part as the bar's progress so the filled part starts on the right side.
                if (dir.flip) {
                    LinearProgressIndicator(
                        progress = 1f - l.progress,
                        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                        color = WColors.accentSoft,
                        backgroundColor = if (l.blocked) WColors.danger else WColors.accent,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = l.progress,
                        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                        color = if (l.blocked) WColors.danger else WColors.accent,
                        backgroundColor = WColors.accentSoft,
                    )
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
