package com.mahm0udnasr.sword.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.system.CheckId
import com.mahm0udnasr.sword.system.Oem
import com.mahm0udnasr.sword.ui.components.InstalledApps
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.theme.Amiri
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.launch

/** Minutes a day for the "quick rule" of the targets step. */
private const val QUICK_LIMIT_MIN = 30

private sealed interface Page {
    data object Welcome : Page
    data class Step(val id: CheckId) : Page
    data object Targets : Page
    data object Done : Page
}

/**
 * First-run wizard (Phase 9): welcome, then each protection item in the order
 * that works (App info steps before the service, because the guard closes App
 * info once the service runs), targets, and a summary. Every step can be
 * skipped; the Settings status card shows what is still missing.
 */
@Composable
fun OnboardingScreen(c: AppContainer, committer: Committer, onDone: () -> Unit) {
    val t = LocalSwordColors.current
    val checks = rememberChecks()
    val fix = rememberFixer(c, committer)
    // The steps are fixed when the wizard starts, so fixing one doesn't shift the pages.
    // Items already done when it starts are left out (e.g. "unused" once the service or admin is on).
    val steps = remember { checks.filter { it.ok != true }.map { it.id } }
    val pages = remember(steps) { listOf(Page.Welcome) + steps.map { Page.Step(it) } + Page.Targets + Page.Done }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[index.coerceIn(pages.indices)]
    val next = { if (index < pages.lastIndex) index++ }
    BackHandler(enabled = index > 0) { index-- }

    Box(Modifier.fillMaxSize().background(t.bg).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            LinearProgressIndicator(
                progress = { (index + 1f) / pages.size },
                color = t.accent, trackColor = t.accentSoft,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    Page.Welcome -> Welcome()
                    is Page.Step -> {
                        val ch = checks.firstOrNull { it.id == page.id }
                        StepPage(page.id, ch?.ok, ch?.required ?: false) { fix(page.id) }
                    }
                    Page.Targets -> TargetsPage(c, committer, onApplied = next)
                    Page.Done -> DonePage(checks.map { it.id to it.ok }, checks.associate { it.id to it.required })
                }
            }
            // Bottom bar: back, and next / skip / finish.
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) TextButton(onClick = { index-- }) { Text(stringResource(R.string.ob_back)) }
                Spacer(Modifier.weight(1f))
                val ok = (page as? Page.Step)?.let { p -> checks.firstOrNull { it.id == p.id }?.ok } ?: true
                when (page) {
                    Page.Done -> Button(onClick = {
                        c.appScope.launch { c.state.update { it.copy(onboardingDone = true) } }
                        onDone()
                    }) { Text(stringResource(R.string.ob_finish)) }
                    Page.Welcome -> Button(onClick = next) { Text(stringResource(R.string.ob_start)) }
                    Page.Targets -> TextButton(onClick = next) { Text(stringResource(R.string.ob_skip)) }
                    else -> if (ok == false) {
                        TextButton(onClick = next) { Text(stringResource(R.string.ob_skip)) }
                    } else {
                        Button(onClick = next) { Text(stringResource(R.string.ob_next)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    val t = LocalSwordColors.current
    Text(stringResource(R.string.x_app_name), color = t.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.ob_welcome), color = t.text, fontSize = 16.sp, lineHeight = 26.sp)
    // The hadith at the top of the extension's README; always right-to-left.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            Modifier.fillMaxWidth().background(t.accentSoft, RoundedCornerShape(16.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("نعمتان مغبون فيهما كثير من الناس: الصحة والفراغ", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 44.sp, color = t.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("رواه البخاري", fontFamily = Amiri, fontSize = 17.sp, color = t.gold)
        }
    }
    Text(stringResource(R.string.ob_privacy), color = t.muted, fontSize = 14.sp, lineHeight = 22.sp)
    Text(stringResource(R.string.ob_steps), color = t.muted, fontSize = 14.sp, lineHeight = 22.sp)
}

@Composable
private fun StepPage(id: CheckId, ok: Boolean?, required: Boolean, onFix: () -> Unit) {
    val t = LocalSwordColors.current
    val txt = id.text()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CheckMark(ok, required)
        Text(stringResource(txt.title), color = t.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
    Text(stringResource(txt.hint), color = t.text, fontSize = 15.sp, lineHeight = 24.sp)
    stepDetails(id)?.let { Text(stringResource(it), color = t.muted, fontSize = 14.sp, lineHeight = 22.sp) }
    if (id == CheckId.BATTERY) {
        SwordCard { Text(stringResource(Oem.guide()), color = t.text, fontSize = 14.sp, lineHeight = 22.sp) }
    }
    when (ok) {
        true -> Text(stringResource(R.string.ob_done), color = t.accent, fontWeight = FontWeight.SemiBold)
        else -> Button(onClick = onFix) { Text(stringResource(txt.fix)) }
    }
    if (!required && ok == false) Text(stringResource(R.string.ob_optional), color = t.muted, fontSize = 12.sp)
}

/** Longer how-to under the hint, where one helps. */
private fun stepDetails(id: CheckId): Int? = when (id) {
    CheckId.UNUSED -> R.string.ob_unusedSteps
    CheckId.RESTRICTED -> R.string.ob_restrictedSteps
    CheckId.ACCESSIBILITY -> R.string.ob_a11ySteps
    CheckId.ADMIN -> R.string.ob_adminSteps
    else -> null
}

/** Step 9.8: switch on some presets now, with one quick rule (stricter, so no friction). */
@Composable
private fun TargetsPage(c: AppContainer, committer: Committer, onApplied: () -> Unit) {
    val t = LocalSwordColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val offered = remember(cfg) {
        cfg.targets.filter { tg -> !tg.enabled && tg.packages.any { InstalledApps.isInstalled(ctx, it) } }
    }
    val chosen = remember { mutableStateListOf<String>() }
    var limit by rememberSaveable { mutableStateOf(false) }

    Text(stringResource(R.string.ob_targets), color = t.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.ob_targetsHint), color = t.text, fontSize = 15.sp, lineHeight = 24.sp)
    if (offered.isEmpty()) {
        Text(stringResource(R.string.ob_targetsNone), color = t.muted, fontSize = 14.sp)
        return
    }
    SwordCard {
        offered.forEach { tg ->
            Row(
                Modifier.fillMaxWidth().toggleable(tg.id in chosen, role = androidx.compose.ui.semantics.Role.Checkbox) { on -> if (on) chosen.add(tg.id) else chosen.remove(tg.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(tg.id in chosen, onCheckedChange = null)
                Text(tg.name, color = t.text)
            }
        }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(!limit, onClick = { limit = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
            Text(stringResource(R.string.x_mode_block), maxLines = 1)
        }
        SegmentedButton(limit, onClick = { limit = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
            Text(tr(R.string.ob_quickLimit, "n" to QUICK_LIMIT_MIN), maxLines = 1)
        }
    }
    OutlinedButton(
        enabled = chosen.isNotEmpty(),
        onClick = {
            scope.launch {
                val rule = if (limit) Rule.default(Mode.LIMIT).copy(limitMinutes = QUICK_LIMIT_MIN) else Rule.default(Mode.BLOCK)
                val cur = c.config.value
                val draft = cur.copy(targets = cur.targets.map { tg -> if (tg.id in chosen) tg.copy(enabled = true, rules = listOf(rule)) else tg })
                if (committer.commit(draft) != Committer.Outcome.CANCELLED) onApplied()
            }
        },
    ) { Text(tr(R.string.ob_targetsApply, "n" to chosen.size)) }
}

@Composable
private fun DonePage(items: List<Pair<CheckId, Boolean?>>, required: Map<CheckId, Boolean>) {
    val t = LocalSwordColors.current
    val missing = items.any { (id, ok) -> ok == false && required[id] == true }
    Text(stringResource(if (missing) R.string.ob_doneMissing else R.string.ob_doneAll), color = t.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    SwordCard {
        items.forEach { (id, ok) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CheckMark(ok, required[id] == true)
                Text(stringResource(id.text().title), color = t.text, fontSize = 14.sp)
            }
        }
    }
    Text(stringResource(R.string.ob_doneHint), color = t.muted, fontSize = 14.sp, lineHeight = 22.sp)
}
