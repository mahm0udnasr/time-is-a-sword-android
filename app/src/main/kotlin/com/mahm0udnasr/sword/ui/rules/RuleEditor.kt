package com.mahm0udnasr.sword.ui.rules

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.model.TimeWindow
import com.mahm0udnasr.sword.ui.components.Ltr
import com.mahm0udnasr.sword.ui.format.dayName
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.modeLabel
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors

/** One rule card while editing; minutes stay text so a half-typed number isn't lost. */
data class RuleDraft(
    val days: Set<Int>,
    val mode: Mode,
    val minutes: String,
    val windows: List<TimeWindow>,
) {
    companion object {
        fun of(r: Rule) = RuleDraft(r.days, r.mode, r.limitMinutes.toString(), r.windows)
    }
}

private val DEFAULT_WINDOW = TimeWindow("13:00", "13:30")

/**
 * Parse and validate (port of read() in rule-editor.js). The first rule containing
 * today applies; to keep that unambiguous a day may appear in one rule only.
 * @return the rules, or the error messages
 */
fun Context.readRules(drafts: List<RuleDraft>, allowEmpty: Boolean): Pair<List<Rule>, List<String>> {
    val errors = LinkedHashSet<String>()
    if (!allowEmpty && drafts.isEmpty()) errors += getString(R.string.x_err_atLeastOneRule)
    val seen = mutableSetOf<Int>()
    val rules = drafts.mapIndexed { i, d ->
        val n = if (drafts.size > 1) tr(R.string.x_err_rulePrefix, "n" to i + 1) else ""
        if (d.days.isEmpty()) errors += n + getString(R.string.x_err_pickDay)
        for (day in d.days.sorted()) {
            if (!seen.add(day)) errors += tr(R.string.x_err_dayTwice, "day" to dayName(day))
        }
        val minutes = d.minutes.trim().toIntOrNull()
        if (d.mode.hasLimit && (minutes == null || minutes !in 0..1440)) errors += n + getString(R.string.x_err_minutes)
        if (d.mode.hasSchedule) {
            if (d.windows.isEmpty()) errors += n + getString(R.string.x_err_oneRange)
            d.windows.filter { it.start == it.end }.forEach { errors += n + tr(R.string.x_err_sameTime, "t" to ltr(it.start)) }
        }
        Rule(
            days = d.days.toSortedSet(),
            mode = d.mode,
            limitMinutes = if (d.mode.hasLimit) minutes ?: 20 else (minutes ?: 20),
            windows = if (d.mode.hasSchedule) d.windows else emptyList(),
        )
    }
    return rules to errors.toList()
}

/** The rules editor shared by the target and group editors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditor(drafts: SnapshotStateList<RuleDraft>, emptyText: String?) {
    val t = LocalSwordColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (drafts.isEmpty() && emptyText != null) Text(emptyText, color = t.muted, fontSize = 13.sp)
        drafts.forEachIndexed { i, d ->
            RuleCard(d, onChange = { drafts[i] = it }, onRemove = { drafts.removeAt(i) })
        }
        TextButton(onClick = {
            // Pre-select the days no other rule uses yet.
            val used = drafts.flatMap { it.days }.toSet()
            drafts += RuleDraft(Defaults.ALL_DAYS - used, Mode.LIMIT, "20", emptyList())
        }) { Text(stringResource(R.string.x_re_addRule)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(d: RuleDraft, onChange: (RuleDraft) -> Unit, onRemove: () -> Unit) {
    val t = LocalSwordColors.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().border(1.dp, t.accentSoft, RoundedCornerShape(14.dp)).background(t.bg, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = d.days.size == 7,
                    onClick = { onChange(d.copy(days = if (d.days.size == 7) emptySet() else Defaults.ALL_DAYS)) },
                    label = { Text(stringResource(R.string.re_everyDay)) },
                )
                (0..6).forEach { day ->
                    FilterChip(
                        selected = day in d.days,
                        onClick = { onChange(d.copy(days = if (day in d.days) d.days - day else d.days + day)) },
                        label = { Text(ctx.dayName(day)) },
                    )
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, stringResource(R.string.x_re_removeRule), tint = t.muted) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            var open by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { open = true }) {
                    Text(stringResource(modeLabel(d.mode)))
                    Icon(Icons.Outlined.ExpandMore, null)
                }
                DropdownMenu(open, { open = false }) {
                    Mode.entries.forEach { m ->
                        DropdownMenuItem({ Text(stringResource(modeLabel(m))) }, {
                            open = false
                            onChange(d.copy(mode = m, windows = if (m.hasSchedule && d.windows.isEmpty()) listOf(DEFAULT_WINDOW) else d.windows))
                        })
                    }
                }
            }
            if (d.mode.hasLimit) {
                OutlinedTextField(
                    value = d.minutes, onValueChange = { v -> onChange(d.copy(minutes = v.filter(Char::isDigit).take(4))) },
                    modifier = Modifier.width(90.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(stringResource(R.string.x_re_minPerDay), color = t.muted, fontSize = 13.sp)
            }
        }
        if (d.mode.hasSchedule) {
            Text(stringResource(R.string.x_re_allowedBetween), color = t.muted, fontSize = 13.sp)
            d.windows.forEachIndexed { i, w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton(w.start) { v -> onChange(d.copy(windows = d.windows.toMutableList().also { it[i] = w.copy(start = v) })) }
                    Text(stringResource(R.string.x_re_to), color = t.muted)
                    TimeButton(w.end) { v -> onChange(d.copy(windows = d.windows.toMutableList().also { it[i] = w.copy(end = v) })) }
                    IconButton(onClick = { onChange(d.copy(windows = d.windows.toMutableList().also { it.removeAt(i) })) }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.x_re_removeHours), tint = t.muted)
                    }
                }
            }
            TextButton(onClick = { onChange(d.copy(windows = d.windows + DEFAULT_WINDOW)) }) { Text(stringResource(R.string.x_re_addHours)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(hm: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Ltr { OutlinedButton(onClick = { open = true }) { Text(hm) } }
    if (open) {
        val (h, m) = hm.split(':').map { it.toInt() }
        val state = rememberTimePickerState(h, m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onPick(String.format(java.util.Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                }) { Text(stringResource(R.string.x_save)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.x_cancel)) } },
            text = { Ltr { TimePicker(state) } },
        )
    }
}
