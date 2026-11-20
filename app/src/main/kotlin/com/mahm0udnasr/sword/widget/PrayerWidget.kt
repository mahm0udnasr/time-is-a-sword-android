package com.mahm0udnasr.sword.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.container
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.normalizePrayer
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.formatDuration
import com.mahm0udnasr.sword.ui.format.prayerName
import com.mahm0udnasr.sword.ui.format.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Slot(val name: String, val time: String, val next: Boolean, val past: Boolean)

private data class PrayerData(
    /** Big line: "العصر 2:51 م" (or the lock). */
    val headline: String,
    /** "بعد 1 س 05 د", "مغلق حتى 12:03", or how to set up. */
    val sub: String,
    val locked: Boolean,
    val slots: List<Slot>,
    val hijri: String,
    val gregorian: String,
    val salahFirst: String,
    val approx: Boolean,
)

/**
 * "Prayer times" widget: the next prayer and how long until it (or the Salah
 * First lock in progress), today's five times with the next one marked, the
 * Hijri and Gregorian dates, and whether Salah First is on.
 */
class PrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(ctx: Context): PrayerData {
        val c = ctx.container
        c.config.awaitLoaded()
        c.state.awaitLoaded()
        val now = System.currentTimeMillis()
        val raw = c.config.value.prayer
        val p = normalizePrayer(raw)
        val days = c.prayerClock.days(now)
        val zone = days.firstOrNull()?.zone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val (hijri, greg) = dates(ctx, date)
        val salahFirst = ctx.getString(if (raw.enabled) R.string.w_salahOn else R.string.w_salahOff)

        // Times including the user's adjustment, today then tomorrow.
        fun timesOf(d: LocalDate) = days.firstOrNull { it.date == d }?.let { day ->
            Prayer.entries.mapNotNull { pr -> day.times[pr]?.let { pr to it + p.toggle(pr).adjust * 60_000L } }
        }.orEmpty()
        val todayTimes = timesOf(date)
        if (todayTimes.isEmpty()) {
            return PrayerData(ctx.getString(R.string.x_nav_prayer), ctx.getString(R.string.w_noLocation), false, emptyList(), hijri, greg, salahFirst, false)
        }
        val upcoming = (todayTimes + timesOf(date.plusDays(1))).firstOrNull { it.second > now }
        val slots = todayTimes.map { (pr, at) ->
            Slot(ctx.getString(prayerName(pr)), ctx.formatClock(at), next = upcoming?.first == pr && upcoming.second == at, past = at <= now)
        }
        val lock = c.prayerClock.active(now)
        val approx = days.any { it.date == date && it.approx }
        return if (lock != null) {
            PrayerData(
                headline = ctx.tr(R.string.x_blocked_prayerNow, "name" to ctx.getString(prayerName(lock.prayer))),
                sub = ctx.tr(R.string.w_lockedUntil, "time" to ctx.formatClock(lock.end)),
                locked = true, slots = slots, hijri = hijri, gregorian = greg, salahFirst = salahFirst, approx = approx,
            )
        } else {
            val (pr, at) = upcoming ?: todayTimes.first()
            PrayerData(
                headline = ctx.getString(prayerName(pr)) + "  " + ctx.formatClock(at),
                sub = ctx.tr(R.string.w_in, "dur" to ctx.formatDuration((at - now) / 1000.0)),
                locked = false, slots = slots, hijri = hijri, gregorian = greg, salahFirst = salahFirst, approx = approx,
            )
        }
    }

    /** "10 جمادى الآخرة 1448 هـ" and "الجمعة 20 نوفمبر" in the app's language (Western digits). */
    private fun dates(ctx: Context, date: LocalDate): Pair<String, String> {
        val loc = ctx.resources.configuration.locales[0] ?: Locale.getDefault()
        val l = Locale.Builder().setLocale(loc).setUnicodeLocaleKeyword("nu", "latn").build()
        val hijri = runCatching {
            DateTimeFormatter.ofPattern("d MMMM y", l).withChronology(HijrahChronology.INSTANCE).format(HijrahDate.from(date)) +
                ctx.getString(R.string.w_hijriSuffix)
        }.getOrDefault("")
        val greg = DateTimeFormatter.ofPattern("EEEE d MMMM", l).format(date)
        return hijri to greg
    }

    @Composable
    private fun Content(ctx: Context, d: PrayerData) {
        val wide = LocalSize.current.width.value >= 250f
        val tall = LocalSize.current.height.value >= 150f
        Column(
            GlanceModifier.fillMaxSize().background(if (d.locked) WColors.accentSoft else WColors.surface).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity(Widgets.openIntent(ctx, "salah"))),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_stat_sword), null, GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(WColors.gold))
                Spacer(GlanceModifier.width(6.dp))
                Text(d.hijri.ifEmpty { d.gregorian }, style = TextStyle(color = WColors.gold, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                if (wide && d.hijri.isNotEmpty()) Text(d.gregorian, style = TextStyle(color = WColors.muted, fontSize = 11.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(d.headline + if (d.approx) " ≈" else "", style = TextStyle(color = WColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(d.sub, style = TextStyle(color = if (d.locked) WColors.accent else WColors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            if (d.slots.isNotEmpty()) {
                Row(GlanceModifier.fillMaxWidth()) {
                    d.slots.forEach { s ->
                        Column(
                            GlanceModifier.defaultWeight().padding(vertical = 6.dp, horizontal = 2.dp)
                                .background(if (s.next) WColors.accent else WColors.bg).cornerRadius(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val color = when {
                                s.next -> WColors.surface
                                s.past -> WColors.muted
                                else -> WColors.text
                            }
                            Text(s.name, style = TextStyle(color = color, fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 1)
                            Text(s.time, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                        }
                        Spacer(GlanceModifier.width(3.dp))
                    }
                }
            }
            if (tall) {
                Spacer(GlanceModifier.height(6.dp))
                Text(d.salahFirst, style = TextStyle(color = WColors.muted, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerWidget()
}
