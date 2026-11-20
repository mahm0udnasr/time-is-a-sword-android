package com.mahm0udnasr.sword.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.State
import com.mahm0udnasr.sword.core.rules.RuleReason
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.core.rules.logicalDate
import com.mahm0udnasr.sword.ui.components.Badge
import com.mahm0udnasr.sword.ui.components.CardTitle
import com.mahm0udnasr.sword.ui.components.EmptyState
import com.mahm0udnasr.sword.ui.components.largeFont
import com.mahm0udnasr.sword.ui.components.PageHeader
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.components.UsageBar
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.formatDuration
import com.mahm0udnasr.sword.ui.format.formatWhen
import com.mahm0udnasr.sword.ui.format.locale
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Overview: today at a glance + the last 7 days (port of renderOverview() and chart.js, step 5.4). */
@Composable
fun OverviewScreen(c: AppContainer, committer: com.mahm0udnasr.sword.ui.friction.Committer) {
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val st by c.state.flow.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val today = today(cfg, st, now)
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snack = com.mahm0udnasr.sword.ui.components.LocalSnackbar.current

    val rows = today.targets
        .filter { (it.target.enabled && (it.hasRuleToday || it.groupName != null)) || it.usedSec >= 1 }
        .sortedWith(compareByDescending<TargetToday> { it.usedSec }.thenBy { it.target.name.lowercase() })

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(stringResource(R.string.x_nav_overview), stringResource(R.string.x_overview_sub)) }
        // Protection off (e.g. after Safe Mode, or a crash): say so first, with the fix.
        item { ProtectionOffBanner() }
        item {
            val stats = listOf(
                Triple(Icons.Outlined.Schedule, stringResource(R.string.x_stat_time), ctx.formatDuration(today.totalSec)),
                Triple(Icons.Outlined.Block, stringResource(R.string.x_stat_blocked), today.targets.count { it.verdict.blocked }.toString()),
                Triple(Icons.Outlined.Pause, stringResource(R.string.x_stat_pauses), today.pausesLeft.toString()),
            )
            // Large fonts (step 10.5): three tiles side by side would break words, so stack them.
            if (largeFont()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { stats.forEach { (i, l, v) -> Stat(Modifier.fillMaxWidth(), i, l, v, wide = true) } }
            } else {
                // Same height for all three, whatever wraps (IntrinsicSize.Max = the tallest tile).
                Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { (i, l, v) -> Stat(Modifier.weight(1f).fillMaxHeight(), i, l, v) }
                }
            }
        }
        item {
            SwordCard {
                CardTitle(stringResource(R.string.x_today)) {
                    Text(tr(R.string.x_newDayAt, "time" to ctx.formatClock(today.resetAt)), color = t.muted, fontSize = 12.sp)
                }
                val groupRows = today.groups.filter { it.limitSec != null }
                if (groupRows.isEmpty() && rows.isEmpty()) EmptyState(Icons.Outlined.HourglassEmpty, stringResource(R.string.x_today_empty))
                groupRows.forEach { g ->
                    UsageRow(
                        name = g.group.name, badge = stringResource(R.string.x_badge_group),
                        value = "${ctx.formatDuration(g.usedSec)} / ${ctx.formatDuration(g.limitSec!!)}",
                        blocked = g.usedSec >= g.limitSec, used = g.usedSec, limit = g.limitSec,
                    )
                }
                rows.forEach { r ->
                    val v = r.verdict
                    val value = when {
                        v is Verdict.Blocked -> {
                            val reason = stringResource(
                                when (v.reason) {
                                    RuleReason.BLOCKED -> R.string.x_reason_blocked
                                    RuleReason.LIMIT -> R.string.x_reason_limit
                                    RuleReason.SCHEDULE -> R.string.x_reason_schedule
                                },
                            )
                            v.until?.let { tr(R.string.today_blockedUntil, "reason" to reason, "when" to ctx.formatWhen(it)) } ?: reason
                        }
                        r.limitSec != null -> "${ctx.formatDuration(r.usedSec)} / ${ctx.formatDuration(r.limitSec)}"
                        else -> ctx.formatDuration(r.usedSec)
                    }
                    val badge = if (v is Verdict.Allowed && v.paused) stringResource(R.string.x_badge_paused) else r.groupName
                    UsageRow(r.target.name, badge, value, v.blocked, r.usedSec, r.limitSec)
                    // Step 6.3: pause a blocked target from here too (its own rules only, while pauses are left).
                    if (v is Verdict.Blocked && today.pausesLeft > 0 && c.prayerClock.active(now) == null) {
                        TextButton(onClick = {
                            scope.launch {
                                val out = com.mahm0udnasr.sword.ui.friction.runPauseFlow(ctx, c, committer, r.target.id)
                                snack.showSnackbar(out.message)
                            }
                        }) { Text(tr(R.string.x_pp_pauseBtn, "n" to minOf(5, Defaults.PAUSE_MAX_MINUTES), "left" to today.pausesLeft), fontSize = 12.sp) }
                    }
                }
            }
        }
        item { WeekCard(cfg, st) }
        item { androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Stat(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, wide: Boolean = false) {
    val t = LocalSwordColors.current
    if (wide) {
        SwordCard(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = t.muted, modifier = Modifier.size(18.dp))
                Text(label, color = t.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(value, color = t.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    SwordCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = t.muted, modifier = Modifier.size(16.dp))
            Text(label, color = t.muted, fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
        }
        // Numbers on one line across the three tiles, even when a label wraps.
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(value, color = t.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun UsageRow(name: String, badge: String?, value: String, blocked: Boolean, used: Double, limit: Double?) {
    val t = LocalSwordColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (largeFont()) {
            // Large fonts: the name gets the whole line, the value goes under it.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, color = t.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                badge?.let { Badge(it) }
            }
            Text(value, color = if (blocked) t.danger else t.muted, fontSize = 13.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, color = t.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                badge?.let { Badge(it) }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(value, color = if (blocked) t.danger else t.muted, fontSize = 13.sp)
            }
        }
        if (limit != null) UsageBar(used, limit)
    }
}

/** The last 7 logical days, oldest first, with each day's seconds per target. */
private data class DayBar(val date: LocalDate, val total: Double, val top: List<Pair<String, Double>>)

@Composable
private fun WeekCard(cfg: Config, st: State) {
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    var only by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val names = cfg.targets.associate { it.id to it.name }
    val zone = ZoneId.systemDefault()
    val todayDate = logicalDate(System.currentTimeMillis(), zone, Defaults.RESET_HOUR)
    val days = (6 downTo 0).map { i ->
        val d = todayDate.minusDays(i.toLong())
        val sec = st.usage[d.toString()]?.seconds.orEmpty()
        DayBar(d, only?.let { sec[it] ?: 0.0 } ?: sec.values.sum(), sec.entries.filter { it.value >= 1 }.sortedByDescending { it.value }.take(3).map { it.key to it.value })
    }
    val seen = st.usage.values.flatMap { it.seconds.keys }.toSet().filter { it in names }.sortedBy { names[it]?.lowercase() }

    SwordCard {
        CardTitle(stringResource(R.string.x_chart_title)) {
            Box {
                TextButton(onClick = { menu = true }) { Text(only?.let { names[it] } ?: stringResource(R.string.x_chart_allSites)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.x_chart_allSites)) }, { only = null; menu = false })
                    seen.forEach { id -> DropdownMenuItem({ Text(names[id] ?: id) }, { only = id; menu = false }) }
                }
            }
        }
        val measurer = rememberTextMeasurer()
        val tick = TextStyle(color = t.muted, fontSize = 10.sp)
        val value = TextStyle(color = t.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        val max = maxOf(days.maxOf { it.total }, 60.0)
        val step = listOf(60.0, 300.0, 600.0, 900.0, 1800.0, 3600.0, 7200.0, 10800.0, 14400.0, 21600.0).firstOrNull { max / it <= 4 } ?: 21600.0
        val yMax = ceil(max / step) * step
        val dayFmt = DateTimeFormatter.ofPattern("EEE", ctx.locale())
        val todayLabel = stringResource(R.string.x_chart_today)
        Canvas(
            Modifier.fillMaxWidth().height(200.dp).pointerInput(days) {
                detectTapGestures { p ->
                    val left = 56.dp.toPx()
                    val slot = (size.width - left) / 7f
                    val i = ((p.x - left) / slot).toInt().coerceIn(0, 6)
                    selected = i
                }
            },
        ) {
            val left = 56.dp.toPx()
            val bottom = 22.dp.toPx()
            val top = 14.dp.toPx()
            val plotH = size.height - top - bottom
            val slot = (size.width - left) / 7f
            val barW = minOf(36.dp.toPx(), slot * 0.6f)
            fun y(v: Double) = top + plotH - (v / yMax * plotH).toFloat()
            var v = 0.0
            while (v <= yMax + 1) {
                drawLine(if (v == 0.0) t.muted else t.accentSoft, Offset(left, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
                val label = if (v == 0.0) "0" else ctx.formatDuration(v)
                val m = measurer.measure(label, tick)
                drawText(m, topLeft = Offset(left - m.size.width - 6.dp.toPx(), y(v) - m.size.height / 2f))
                v += step
            }
            days.forEachIndexed { idx, d ->
                val i = idx // like the extension's chart: always left-to-right, oldest first
                val cx = left + slot * i + slot / 2
                val isToday = idx == 6
                val lbl = measurer.measure(if (isToday) todayLabel else d.date.format(dayFmt), tick)
                drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, size.height - lbl.size.height))
                if (d.total > 0) {
                    val yTop = y(d.total)
                    drawRoundRect(
                        color = if (isToday || selected == idx) t.accent else t.accent.copy(alpha = 0.55f),
                        topLeft = Offset(cx - barW / 2, yTop), size = Size(barW, top + plotH - yTop), cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    if (isToday) {
                        val m = measurer.measure(ctx.formatDuration(d.total), value)
                        drawText(m, topLeft = Offset(cx - m.size.width / 2f, yTop - m.size.height - 2.dp.toPx()))
                    }
                }
            }
        }
        selected?.let { i ->
            val d = days[i]
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.date.format(DateTimeFormatter.ofPattern("EEEE d MMM", ctx.locale())), fontWeight = FontWeight.SemiBold, color = t.text, fontSize = 13.sp)
                Text(
                    only?.let { tr(R.string.x_chart_on, "time" to ctx.formatDuration(d.total), "site" to (names[it] ?: it)) }
                        ?: tr(R.string.x_chart_inTotal, "time" to ctx.formatDuration(d.total)),
                    color = t.text, fontSize = 13.sp,
                )
                if (only == null) d.top.forEach { (id, s) -> Text("${names[id] ?: id} · ${ctx.formatDuration(s)}", color = t.muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box { content() }

@Composable
private fun ProtectionOffBanner() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val t = com.mahm0udnasr.sword.ui.theme.LocalSwordColors.current
    var on by remember { mutableStateOf(com.mahm0udnasr.sword.system.HealthCheck.serviceEnabled(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        on = com.mahm0udnasr.sword.system.HealthCheck.serviceEnabled(ctx)
        onPauseOrDispose {}
    }
    if (on) return
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().background(t.dangerSoft, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.banner_off), color = t.danger, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = {
            com.mahm0udnasr.sword.system.Checks.open(ctx, com.mahm0udnasr.sword.system.CheckId.ACCESSIBILITY)
        }) { Text(stringResource(R.string.chk_fix)) }
    }
}
