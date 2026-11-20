package com.mahm0udnasr.sword.ui.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.rules.normalizeDomain
import com.mahm0udnasr.sword.ui.components.AppAvatar
import com.mahm0udnasr.sword.ui.components.CardTitle
import com.mahm0udnasr.sword.ui.components.InstalledApps
import com.mahm0udnasr.sword.ui.components.LocalSnackbar
import com.mahm0udnasr.sword.ui.components.Ltr
import com.mahm0udnasr.sword.ui.components.PageHeader
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.rules.RuleDraft
import com.mahm0udnasr.sword.ui.rules.RuleEditor
import com.mahm0udnasr.sword.ui.rules.readRules
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.launch

/**
 * One target: rename, its apps and websites, its group and its rules (step 5.5/5.6).
 * Everything is a draft until Save; Save goes through the commit pipeline, so
 * removing an app/website or loosening a rule needs the friction step.
 */
@Composable
fun TargetEditorScreen(c: AppContainer, committer: Committer, id: String, onDone: () -> Unit) {
    val original = remember { c.config.value.targets.firstOrNull { it.id == id } }
    if (original == null) {
        onDone()
        return
    }
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current
    var name by remember { mutableStateOf(original.name) }
    val packages = remember { original.packages.toMutableStateList() }
    val domains = remember { original.domains.toMutableStateList() }
    // Short videos (Phase 12): sections instead of apps and websites.
    val isShorts = original.id == com.mahm0udnasr.sword.core.shorts.SHORTS_TARGET_ID
    val sections = remember { original.sections.toMutableStateList() }
    var groupId by remember { mutableStateOf(original.groupId) }
    val rules = remember { original.rules.map(RuleDraft::of).toMutableStateList() }
    var newDomain by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    val errors = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    val groups = c.config.value.groups

    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(tr(R.string.target_title, "name" to ltr(original.name)))

        if (isShorts) ShortsSectionsCard(sections)

        SwordCard {
            if (!isShorts) OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.target_name)) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.target_group), color = t.muted, modifier = Modifier.weight(1f))
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }) {
                        Text(groups.firstOrNull { it.id == groupId }?.name ?: stringResource(R.string.target_noGroup))
                        Icon(Icons.Outlined.ExpandMore, null)
                    }
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.target_noGroup)) }, { groupId = null; open = false })
                        groups.forEach { g -> DropdownMenuItem({ Text(g.name) }, { groupId = g.id; open = false }) }
                    }
                }
            }
        }

        if (!isShorts) SwordCard {
            CardTitle(stringResource(R.string.target_apps)) {
                IconButton(onClick = { picker = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.add_app), tint = t.accent) }
            }
            if (packages.isEmpty()) Text(stringResource(R.string.target_noApps), color = t.muted, fontSize = 13.sp)
            packages.forEach { pkg ->
                val installed = InstalledApps.isInstalled(ctx, pkg)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppAvatar(pkg.takeIf { installed }, InstalledApps.label(ctx, pkg), 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(InstalledApps.label(ctx, pkg), color = t.text)
                        Ltr { Text(if (installed) pkg else "$pkg · ${stringResource(R.string.target_notInstalled)}", color = t.muted, fontSize = 11.sp) }
                    }
                    IconButton(onClick = { packages.remove(pkg) }) { Icon(Icons.Outlined.Close, tr(R.string.allow_remove, "app" to InstalledApps.label(ctx, pkg)), tint = t.muted) }
                }
            }
        }

        if (!isShorts) SwordCard {
            CardTitle(stringResource(R.string.target_sites))
            if (domains.isEmpty()) Text(stringResource(R.string.target_noSites), color = t.muted, fontSize = 13.sp)
            domains.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ltr { Text(d, color = t.text, modifier = Modifier.weight(1f)) }
                    IconButton(onClick = { domains.remove(d) }) { Icon(Icons.Outlined.Close, tr(R.string.allow_remove, "app" to d), tint = t.muted) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ltr {
                    OutlinedTextField(newDomain, { newDomain = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("example.com") })
                }
                TextButton(onClick = {
                    val d = normalizeDomain(newDomain)
                    val owner = d?.let { owningTarget(it, c.config.value.targets, except = id) }
                    errors.clear()
                    when {
                        d == null -> errors += ctx.getString(R.string.x_err_badDomain)
                        owner != null -> errors += ctx.tr(R.string.err_dupDomainIn, "site" to ltr(d), "name" to ltr(owner.name))
                        d !in domains -> { domains += d; newDomain = "" }
                        else -> newDomain = ""
                    }
                }) { Text(stringResource(R.string.x_add)) }
            }
        }

        SwordCard {
            CardTitle(stringResource(R.string.target_rules))
            RuleEditor(rules, stringResource(if (groupId != null) R.string.x_editor_noOwnRule else R.string.x_editor_noRule))
        }

        errors.forEach { Text("• $it", color = t.danger, fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isShorts) TextButton(onClick = {
                scope.launch {
                    busy = true
                    val draft = c.config.value.copy(targets = c.config.value.targets.filter { it.id != id })
                    val out = committer.commit(draft, ctx.tr(R.string.target_removeTitle, "name" to ltr(original.name)))
                    busy = false
                    if (out != Committer.Outcome.CANCELLED) {
                        snack.showSnackbar(ctx.tr(R.string.x_site_removed, "site" to ltr(original.name)))
                        onDone()
                    } else {
                        snack.showSnackbar(ctx.getString(R.string.x_cancelled))
                    }
                }
            }, enabled = !busy) { Text(stringResource(R.string.target_delete), color = t.danger) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.x_cancel)) }
            Button(onClick = {
                errors.clear()
                val (parsed, ruleErrors) = ctx.readRules(rules, allowEmpty = true)
                if (name.isBlank()) errors += ctx.getString(R.string.err_nameEmpty)
                if (isShorts && sections.isEmpty()) errors += ctx.getString(R.string.shorts_needOne)
                if (!isShorts && packages.isEmpty() && domains.isEmpty()) errors += ctx.getString(R.string.err_emptyTarget)
                errors += ruleErrors
                if (errors.isNotEmpty()) return@Button
                scope.launch {
                    busy = true
                    val cur = c.config.value
                    val updated = (cur.targets.firstOrNull { it.id == id } ?: original)
                        .copy(name = name.trim(), packages = packages.toList(), domains = domains.toList(), sections = sections.toList(), groupId = groupId, rules = parsed)
                    val out = committer.commit(cur.copy(targets = cur.targets.map { if (it.id == id) updated else it }), ctx.tr(R.string.target_loosenTitle, "name" to ltr(original.name)))
                    busy = false
                    if (out == Committer.Outcome.CANCELLED) {
                        errors += ctx.getString(R.string.x_cancelled)
                    } else {
                        snack.showSnackbar(ctx.getString(R.string.x_saved))
                        onDone()
                    }
                }
            }, enabled = !busy) { Text(stringResource(R.string.x_save)) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (picker) {
        AppPicker(c.config.value.targets, except = id, onDismiss = { picker = false }) { picked ->
            picker = false
            picked.filter { it !in packages }.forEach { packages += it }
        }
    }
}

/** Short videos: one switch per section (Phase 12). Switching one off is looser (friction on save). */
@androidx.compose.runtime.Composable
private fun ShortsSectionsCard(sections: androidx.compose.runtime.snapshots.SnapshotStateList<String>) {
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    SwordCard {
        CardTitle(stringResource(R.string.shorts_sections))
        Text(stringResource(R.string.shorts_sectionsHint), color = t.muted, fontSize = 12.sp)
        ShortsCatalog.sections(ctx).forEach { s ->
            val installed = s.packages.firstOrNull { InstalledApps.isInstalled(ctx, it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppAvatar(installed, ShortsCatalog.name(ctx, s.id), 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(ShortsCatalog.name(ctx, s.id), color = t.text)
                    val notes = buildList {
                        add(ctx.getString(if (s.wholeApp) R.string.shorts_wholeApp else R.string.shorts_inApp))
                        if (s.web.isNotEmpty()) add(ctx.getString(R.string.shorts_andWeb))
                        if (installed == null) add(ctx.getString(R.string.target_notInstalled))
                        if (!s.verified) add(ctx.getString(R.string.shorts_unverified))
                    }
                    Text(notes.joinToString(" · "), color = t.muted, fontSize = 11.sp)
                }
                androidx.compose.material3.Switch(s.id in sections, onCheckedChange = { on -> if (on) sections.add(s.id) else sections.remove(s.id) })
            }
        }
    }
}
