package com.mahm0udnasr.sword.ui.salah

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.prayer.Coords
import com.mahm0udnasr.sword.core.prayer.Methods
import com.mahm0udnasr.sword.core.prayer.Prayer
import com.mahm0udnasr.sword.core.prayer.PrayerSettings
import com.mahm0udnasr.sword.core.prayer.PrayerToggle
import com.mahm0udnasr.sword.core.prayer.adjustedTime
import com.mahm0udnasr.sword.core.prayer.hasLocation
import com.mahm0udnasr.sword.core.prayer.normalizePrayer
import com.mahm0udnasr.sword.core.prayer.todayTimes
import com.mahm0udnasr.sword.prayer.AladhanApi
import com.mahm0udnasr.sword.prayer.PrayerFetcher
import com.mahm0udnasr.sword.core.prayer.PrayerCache
import com.mahm0udnasr.sword.core.prayer.cfgKey
import com.mahm0udnasr.sword.core.prayer.usesCityTimes
import com.mahm0udnasr.sword.prayer.LocationHelper
import com.mahm0udnasr.sword.ui.components.AppAvatar
import com.mahm0udnasr.sword.ui.components.CardTitle
import com.mahm0udnasr.sword.ui.components.InstalledApps
import com.mahm0udnasr.sword.ui.components.LocalSnackbar
import com.mahm0udnasr.sword.ui.components.Ltr
import com.mahm0udnasr.sword.ui.components.PageHeader
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.formatDuration
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.prayerName
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.targets.AppPicker
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

/** The form, with the two minute fields kept as typed text (validated on Save, like the extension). */
private data class Form(
    val enabled: Boolean,
    val address: String,
    val coords: Coords?,
    val timezone: String?,
    val method: Int,
    val school: Int,
    val before: String,
    val after: String,
    val prayers: Map<Prayer, PrayerToggle>,
) {
    fun settings(): PrayerSettings = PrayerSettings(
        enabled, address.trim(), coords, timezone, method, school,
        before.trim().toIntOrNull() ?: -1, after.trim().toIntOrNull() ?: -1, prayers,
    )

    companion object {
        fun of(p: PrayerSettings) = Form(p.enabled, p.address, p.coords, p.timezone, p.method, p.school, p.before.toString(), p.after.toString(), p.prayers)
    }
}

private val METHOD_LABEL = mapOf(
    5 to R.string.x_method_5, 4 to R.string.x_method_4, 3 to R.string.x_method_3, 2 to R.string.x_method_2,
    1 to R.string.x_method_1, 9 to R.string.x_method_9, 10 to R.string.x_method_10, 16 to R.string.x_method_16,
    13 to R.string.x_method_13, 11 to R.string.x_method_11,
)

/** Salah First (port of options/prayer.js, step 7.2) + the apps open during prayer (step 7.1). */
@Composable
fun SalahScreen(c: AppContainer, committer: Committer) {
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current

    var form by remember { mutableStateOf(Form.of(cfg.prayer)) }
    var dirty by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cfg.prayer) { if (!dirty) form = Form.of(cfg.prayer) }
    fun edit(f: Form) {
        form = f
        dirty = true
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    suspend fun save(f0: Form) {
        var f = f0
        val raw = f.settings()
        val errs = buildList {
            if (raw.enabled && raw.coords == null && raw.address.isEmpty()) add(ctx.getString(R.string.x_prayer_needAddress))
            if (raw.before !in 0..60 || raw.after !in 0..120) add(ctx.getString(R.string.x_prayer_badNumbers))
        }
        errors = errs
        if (errs.isNotEmpty()) return
        val next = normalizePrayer(f.settings())
        // A typed city: its times come from Aladhan, a month at a time (fetched before saving, so an
        // unknown city is reported and nothing changes). "Use my location" is calculated on the phone.
        var fetched: PrayerCache? = null
        val cache = c.state.value.prayerCache
        if (usesCityTimes(next) && cache?.cfg != cfgKey(next)) {
            busy = ctx.getString(R.string.x_prayer_fetching)
            val out = c.prayerFetcher.fetch(next, cache)
            busy = null
            when (out) {
                is PrayerFetcher.Outcome.Ok -> fetched = out.cache
                is PrayerFetcher.Outcome.Error -> { errors = listOf(ctx.fetchErrorText(out.result)); return }
            }
        }
        when (committer.commit(c.config.value.copy(prayer = next), ctx.getString(R.string.x_prayer_loosenTitle))) {
            Committer.Outcome.CANCELLED -> snack.showSnackbar(ctx.getString(R.string.x_cancelled))
            else -> {
                fetched?.let { c.prayerFetcher.store(it) }
                dirty = false
                form = Form.of(c.config.value.prayer)
                snack.showSnackbar(ctx.getString(R.string.x_prayer_saved))
            }
        }
    }

    fun locate() = scope.launch {
        busy = ctx.getString(R.string.x_prayer_locating)
        val r = LocationHelper.locate(ctx)
        busy = null
        when (r) {
            is LocationHelper.Result.Ok -> {
                val f = form.copy(coords = r.coords, address = "", timezone = null)
                edit(f)
                snack.showSnackbar(ctx.getString(R.string.x_prayer_located))
                save(f) // like the extension: save right away (friction still applies if it's on)
            }
            LocationHelper.Result.Denied -> snack.showSnackbar(ctx.getString(R.string.salah_locDenied))
            LocationHelper.Result.Off -> snack.showSnackbar(ctx.getString(R.string.salah_locOff))
            LocationHelper.Result.Failed -> snack.showSnackbar(ctx.getString(R.string.salah_locFailed))
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else scope.launch { snack.showSnackbar(ctx.getString(R.string.salah_locDenied)) }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(stringResource(R.string.x_nav_prayer), stringResource(R.string.salah_sub))

        StatusCard(c, cfg.prayer, now)

        SwordCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.x_prayer_enable), color = t.text, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.salah_enableHint), color = t.muted, fontSize = 12.sp)
                }
                Switch(form.enabled, onCheckedChange = { edit(form.copy(enabled = it)) })
            }
        }

        SwordCard {
            CardTitle(stringResource(R.string.x_prayer_location))
            val coords = form.coords
            if (coords != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MyLocation, null, tint = t.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.x_prayer_here) + " · " +
                            ltr(String.format(Locale.ROOT, "%.2f, %.2f", coords.lat, coords.lng)),
                        color = t.text, modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = { edit(form.copy(coords = null, timezone = null, address = "")) }) {
                    Text(stringResource(R.string.x_prayer_typeCity))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (LocationHelper.hasPermission(ctx)) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = busy == null,
                ) {
                    Icon(Icons.Outlined.MyLocation, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.x_prayer_locate))
                }
                OutlinedTextField(
                    form.address, { edit(form.copy(address = it.take(120))) },
                    label = { Text(stringResource(R.string.x_prayer_or)) },
                    placeholder = { Text(stringResource(R.string.x_prayer_addressPh)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Picker(
                stringResource(R.string.x_prayer_method), Methods.SUPPORTED, form.method,
                { stringResource(METHOD_LABEL[it] ?: R.string.x_method_5) },
            ) { edit(form.copy(method = it)) }
            Picker(
                stringResource(R.string.x_prayer_school), listOf(0, 1), form.school,
                { stringResource(if (it == 1) R.string.x_prayer_school1 else R.string.x_prayer_school0) },
            ) { edit(form.copy(school = it)) }
            Text(stringResource(R.string.salah_privacy), color = t.muted, fontSize = 12.sp)
        }

        SwordCard {
            CardTitle(stringResource(R.string.x_prayer_timing))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple(R.string.x_prayer_before, form.before) { v: String -> edit(form.copy(before = v)) },
                    Triple(R.string.x_prayer_after, form.after) { v: String -> edit(form.copy(after = v)) },
                ).forEach { (label, value, set) ->
                    OutlinedTextField(
                        value, { set(it.filter(Char::isDigit).take(3)) },
                        label = { Text(stringResource(label), fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        TodayCard(c, form, now, refreshing = busy != null, onRefresh = {
            scope.launch {
                busy = ctx.getString(R.string.x_prayer_fetching)
                val p = normalizePrayer(c.config.value.prayer)
                if (!usesCityTimes(p)) return@launch
                val out = c.prayerFetcher.fetch(p, null)
                busy = null
                when (out) {
                    is PrayerFetcher.Outcome.Ok -> { c.prayerFetcher.store(out.cache); snack.showSnackbar(ctx.getString(R.string.x_prayer_fetched)) }
                    is PrayerFetcher.Outcome.Error -> { c.prayerFetcher.store(out.recorded); snack.showSnackbar(ctx.fetchErrorText(out.result)) }
                }
            }
        }) { edit(it) }

        errors.forEach { Text(it, color = t.danger, fontSize = 13.sp) }
        busy?.let { Text(it, color = t.muted, fontSize = 13.sp) }
        Button(
            onClick = { scope.launch { save(form) } },
            enabled = busy == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.x_prayer_save)) }

        AllowlistCard(c, committer)
        Spacer(Modifier.height(24.dp))
    }
}

/** "Next prayer: Asr 14:38 · the lock starts in 12 min" / "Everything is closed for Dhuhr until 11:55". */
@Composable
private fun StatusCard(c: AppContainer, saved: PrayerSettings, now: Long) {
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val lock = c.prayerClock.active(now)
    val lines = when {
        !hasLocation(saved) -> listOf(stringResource(R.string.x_ps_noAddress))
        !saved.enabled -> listOf(stringResource(R.string.x_ps_off))
        lock != null -> listOf(tr(R.string.salah_locked, "name" to stringResource(prayerName(lock.prayer)), "time" to ltr(ctx.formatClock(lock.end))))
        else -> c.prayerClock.next(now)?.let { n ->
            listOf(
                tr(R.string.x_ps_next, "name" to stringResource(prayerName(n.prayer)), "time" to ltr(ctx.formatClock(n.at))),
                tr(R.string.salah_nextIn, "dur" to ctx.formatDuration((n.start - now) / 1000.0)),
            )
        } ?: listOf(stringResource(R.string.x_ps_noNext))
    }.let { base ->
        val err = c.state.value.prayerCache?.takeIf { usesCityTimes(normalizePrayer(saved)) && it.cfg == cfgKey(normalizePrayer(saved)) }?.error
        if (err != null && hasLocation(saved)) base + tr(R.string.x_prayer_fetchError, "msg" to err) else base
    }
    SwordCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (lock != null) Icons.Outlined.Lock else Icons.Outlined.NightsStay, null,
                tint = if (lock != null) t.danger else t.accent,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (lock != null) t.dangerSoft else t.accentSoft).padding(8.dp).size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lines.first(), color = t.text, fontWeight = FontWeight.SemiBold)
                lines.drop(1).forEach { Text(it, color = t.muted, fontSize = 13.sp) }
            }
        }
    }
}

/** Today's five prayers: name, time, lock window, ± minutes, switch (calculated live from the form). */
@Composable
private fun TodayCard(c: AppContainer, form: Form, now: Long, refreshing: Boolean, onRefresh: () -> Unit, onChange: (Form) -> Unit) {
    val t = LocalSwordColors.current
    val st by c.state.flow.collectAsStateWithLifecycle()
    val p = normalizePrayer(form.settings().let { it.copy(before = it.before.coerceAtLeast(0), after = it.after.coerceAtLeast(0)) })
    // Live from the form: calculated for "Use my location", from the saved Aladhan cache for a typed city.
    val day = remember(p, now / 60_000, st.prayerCache) {
        todayTimes(p, c.prayerClock.days(now, p), now, ZoneId.systemDefault())
    }
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val saved = normalizePrayer(cfg.prayer)
    SwordCard {
        CardTitle(stringResource(R.string.x_prayer_today)) {
            // A typed city's times come from the internet: let the user fetch them again.
            if (usesCityTimes(saved)) {
                TextButton(onClick = onRefresh, enabled = !refreshing) { Text(stringResource(R.string.x_prayer_refresh), fontSize = 13.sp) }
            }
        }
        if (day?.approx == true) Text("≈ " + stringResource(R.string.x_prayer_approx), color = t.gold, fontSize = 12.sp)
        if (day == null) {
            Text(stringResource(R.string.salah_noTimes), color = t.muted, fontSize = 13.sp)
        }
        Prayer.entries.forEach { k ->
            val tg = form.prayers[k] ?: PrayerToggle()
            val at = day?.let { adjustedTime(it, k, p) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(prayerName(k)), color = if (tg.on) t.text else t.muted, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Ltr { Text(at ?: "—", color = if (tg.on) t.text else t.muted) }
                    }
                    if (at != null) {
                        Text(
                            tr(R.string.x_prayer_window, "from" to ltr(shift(at, -p.before)), "to" to ltr(shift(at, p.after))),
                            color = t.muted, fontSize = 12.sp,
                        )
                    }
                }
                // ± minutes (−60…60): a stepper works with every keyboard (no minus sign needed).
                Ltr {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onChange(form.withAdjust(k, tg.adjust - 1)) }, enabled = tg.adjust > -60) {
                            Icon(Icons.Outlined.Remove, tr(R.string.x_prayer_adjustLabel, "name" to stringResource(prayerName(k))))
                        }
                        Text(
                            if (tg.adjust > 0) "+${tg.adjust}" else tg.adjust.toString(),
                            color = if (tg.adjust == 0) t.muted else t.accent, modifier = Modifier.widthIn(min = 28.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = { onChange(form.withAdjust(k, tg.adjust + 1)) }, enabled = tg.adjust < 60) {
                            Icon(Icons.Outlined.Add, tr(R.string.x_prayer_adjustLabel, "name" to stringResource(prayerName(k))))
                        }
                    }
                }
                Switch(tg.on, onCheckedChange = { on ->
                    onChange(form.copy(prayers = form.prayers + (k to tg.copy(on = on))))
                })
            }
        }
    }
}

private fun Form.withAdjust(k: Prayer, v: Int) = copy(prayers = prayers + (k to (prayers[k] ?: PrayerToggle()).copy(adjust = v.coerceIn(-60, 60))))

/** "HH:MM" moved by [mins], wrapping around midnight (the extension's shift()). */
private fun shift(hm: String, mins: Int): String {
    val (h, m) = hm.split(":").map(String::toInt)
    val total = ((h * 60 + m + mins) % 1440 + 1440) % 1440
    return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60)
}

@Composable
private fun <T> Picker(label: String, options: List<T>, selected: T, text: @Composable (T) -> String, onPick: (T) -> Unit) {
    val t = LocalSwordColors.current
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, color = t.muted, fontSize = 13.sp)
        Column {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(text(selected), modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { o -> DropdownMenuItem({ Text(text(o)) }, { open = false; onPick(o) }) }
            }
        }
    }
}

/** Apps open during prayer: the phone's essentials (fixed) and the user's own (step 7.1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllowlistCard(c: AppContainer, committer: Committer) {
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current
    var defaults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(cfg.targets) {
        defaults = withContext(Dispatchers.IO) {
            c.prayerAllowlist.refresh()
            c.prayerAllowlist.defaults().map { it to InstalledApps.label(ctx, it) }.distinctBy { it.second }.sortedBy { it.second.lowercase() }
        }
    }
    fun commit(list: List<String>) = scope.launch {
        val out = committer.commit(
            c.config.value.copy(protection = c.config.value.protection.copy(prayerAllowlist = list)),
            ctx.getString(R.string.allow_loosenTitle),
        )
        if (out == Committer.Outcome.CANCELLED) snack.showSnackbar(ctx.getString(R.string.x_cancelled))
    }

    SwordCard {
        CardTitle(stringResource(R.string.allow_title))
        Text(stringResource(R.string.allow_hint), color = t.muted, fontSize = 12.sp)
        Text(stringResource(R.string.allow_defaults), color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            defaults.forEach { (_, label) ->
                Text(
                    label, fontSize = 12.sp, color = t.accent,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(t.accentSoft).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(stringResource(R.string.allow_yours), color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        val mine = cfg.protection.prayerAllowlist
        if (mine.isEmpty()) Text(stringResource(R.string.allow_none), color = t.muted, fontSize = 13.sp)
        mine.forEach { pkg ->
            val label = InstalledApps.label(ctx, pkg)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(pkg, label, 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(label, color = t.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { commit(mine - pkg) }) {
                    Icon(Icons.Outlined.Close, tr(R.string.allow_remove, "app" to label), tint = t.muted)
                }
            }
        }
        TextButton(onClick = { picking = true }) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.allow_add))
        }
    }
    if (picking) {
        AppPicker(emptyList(), onDismiss = { picking = false }) { pkgs ->
            picking = false
            commit((cfg.protection.prayerAllowlist + pkgs).distinct())
        }
    }
}

/** What to tell the user when a city's times couldn't be fetched. */
private fun android.content.Context.fetchErrorText(r: AladhanApi.Result): String = when (r) {
    is AladhanApi.Result.NotFound -> getString(R.string.salah_cityNotFound)
    AladhanApi.Result.Offline -> getString(R.string.salah_offline)
    is AladhanApi.Result.Failed -> tr(R.string.x_prayer_fetchError, "msg" to r.message)
    is AladhanApi.Result.Ok -> ""
}
