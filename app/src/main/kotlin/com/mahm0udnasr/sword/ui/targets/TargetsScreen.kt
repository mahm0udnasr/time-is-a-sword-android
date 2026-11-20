package com.mahm0udnasr.sword.ui.targets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.model.newId
import com.mahm0udnasr.sword.core.rules.normalizeDomain
import com.mahm0udnasr.sword.data.Preset
import com.mahm0udnasr.sword.data.StoreJson
import com.mahm0udnasr.sword.ui.components.AppAvatar
import com.mahm0udnasr.sword.ui.components.Badge
import com.mahm0udnasr.sword.ui.components.EmptyState
import com.mahm0udnasr.sword.ui.components.InstalledApps
import com.mahm0udnasr.sword.ui.components.LocalSnackbar
import com.mahm0udnasr.sword.ui.components.PageHeader
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.components.UsageBar
import com.mahm0udnasr.sword.ui.format.describeRules
import com.mahm0udnasr.sword.ui.format.formatDuration
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.overview.today
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.launch

/** The list of targets (the extension's "Sites" tab, apps + websites, step 5.5). */
@Composable
fun TargetsScreen(c: AppContainer, committer: Committer, onOpen: (String) -> Unit) {
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val st by c.state.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current
    var query by rememberSaveable { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var siteDialog by rememberSaveable { mutableStateOf(false) }
    var presetDialog by rememberSaveable { mutableStateOf(false) }
    val today = today(cfg, st)
    val q = query.trim().lowercase()
    // Switched-on targets first; the stable sort keeps the list's own order inside each part.
    val shown = today.targets.filter { r ->
        q.isEmpty() || r.target.name.lowercase().contains(q) || r.target.domains.any { q in it } ||
            r.target.packages.any { q in it || InstalledApps.label(ctx, it).lowercase().contains(q) }
    }.sortedByDescending { it.target.enabled }

    fun added(text: String) = scope.launch { snack.showSnackbar(text) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PageHeader(stringResource(R.string.nav_targets), stringResource(R.string.targets_sub)) }
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.targets_search)) },
                )
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.Apps, if (q.isEmpty()) stringResource(R.string.targets_empty) else tr(R.string.targets_noMatch, "q" to query.trim()))
                }
            }
            items(shown, key = { it.target.id }) { r ->
                val target = r.target
                val group = cfg.groups.firstOrNull { it.id == target.groupId }
                SwordCard(Modifier.clickable { onOpen(target.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppAvatar(target.packages.firstOrNull { InstalledApps.isInstalled(ctx, it) }, target.domains.firstOrNull() ?: target.name)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(target.name, fontWeight = FontWeight.SemiBold, color = if (target.enabled) t.text else t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                group?.let { Badge(it.name) }
                            }
                            Text(
                                ctx.describeRules(target.rules, stringResource(if (group != null) R.string.x_rules_groupOnly else R.string.x_rules_trackedOnly)),
                                color = t.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(target.enabled, onCheckedChange = { on ->
                            scope.launch {
                                val draft = c.config.value.copy(targets = c.config.value.targets.map { if (it.id == target.id) it.copy(enabled = on) else it })
                                val out = committer.commit(draft, ctx.tr(R.string.target_switchOffTitle, "name" to ltr(target.name)))
                                snack.showSnackbar(
                                    if (out == Committer.Outcome.CANCELLED) ctx.getString(R.string.x_cancelled)
                                    else ctx.tr(if (on) R.string.x_site_switchedOn else R.string.x_site_switchedOff, "site" to ltr(target.name)),
                                )
                            }
                        })
                    }
                    if (r.limitSec != null) {
                        Row { Text("${ctx.formatDuration(r.usedSec)} / ${ctx.formatDuration(r.limitSec)}", color = t.muted, fontSize = 12.sp) }
                        UsageBar(r.usedSec, r.limitSec)
                    } else if (r.usedSec >= 1) {
                        Text(tr(R.string.x_usage_today, "time" to ctx.formatDuration(r.usedSec)), color = t.muted, fontSize = 12.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.x_add)) })
            DropdownMenu(addMenu, { addMenu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.add_app)) }, { addMenu = false; picker = true }, leadingIcon = { Icon(Icons.Outlined.Apps, null) })
                DropdownMenuItem({ Text(stringResource(R.string.add_site)) }, { addMenu = false; siteDialog = true }, leadingIcon = { Icon(Icons.Outlined.Language, null) })
                DropdownMenuItem({ Text(stringResource(R.string.add_preset)) }, { addMenu = false; presetDialog = true }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) })
            }
        }
    }

    if (picker) {
        AppPicker(cfg.targets, onDismiss = { picker = false }) { pkgs ->
            picker = false
            scope.launch {
                // Each chosen app becomes its own target, switched on and blocked (like adding a site in the extension).
                val now = System.currentTimeMillis()
                val news = pkgs.map { makeTarget(newId("t", now), InstalledApps.label(ctx, it), packages = listOf(it)) }
                committer.commit(c.config.value.copy(targets = c.config.value.targets + news))
                added(if (news.size == 1) ctx.tr(R.string.target_added, "name" to ltr(news[0].name)) else ctx.tr(R.string.targets_added, "n" to news.size))
            }
        }
    }
    if (siteDialog) {
        AddSiteDialog(cfg.targets, onDismiss = { siteDialog = false }) { domain ->
            siteDialog = false
            scope.launch {
                val target = makeTarget(newId("t", System.currentTimeMillis()), domain, domains = listOf(domain))
                committer.commit(c.config.value.copy(targets = c.config.value.targets + target))
                added(ctx.tr(R.string.x_site_added, "site" to ltr(domain)))
            }
        }
    }
    if (presetDialog) {
        val presets = remember { StoreJson.decodeFromString<List<Preset>>(ctx.assets.open("presets.json").use { it.readBytes().decodeToString() }) }
        val missing = presets.filter { p -> cfg.targets.none { it.id == p.id } }
        PresetDialog(missing, onDismiss = { presetDialog = false }) { p ->
            presetDialog = false
            scope.launch {
                val target = makeTarget(p.id, p.name, packages = p.packages, domains = p.domains.mapNotNull(::normalizeDomain))
                committer.commit(c.config.value.copy(targets = c.config.value.targets + target))
                added(ctx.tr(R.string.target_added, "name" to ltr(p.name)))
            }
        }
    }
}

/** Where a domain is already listed, if anywhere. */
fun owningTarget(domain: String, targets: List<Target>, except: String? = null): Target? =
    targets.firstOrNull { it.id != except && domain in it.domains }

@Composable
private fun AddSiteDialog(targets: List<Target>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_site)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.mahm0udnasr.sword.ui.components.Ltr {
                    OutlinedTextField(text, { text = it; error = null }, singleLine = true, placeholder = { Text("youtube.com") }, isError = error != null)
                }
                error?.let { Text(it, color = LocalSwordColors.current.danger, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = normalizeDomain(text)
                val owner = d?.let { owningTarget(it, targets) }
                error = when {
                    d == null -> ctx.getString(R.string.x_err_badDomain)
                    owner != null -> ctx.tr(R.string.err_dupDomainIn, "site" to ltr(d), "name" to ltr(owner.name))
                    else -> null
                }
                if (error == null && d != null) onAdd(d)
            }) { Text(stringResource(R.string.x_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.x_cancel)) } },
    )
}

@Composable
private fun PresetDialog(missing: List<Preset>, onDismiss: () -> Unit, onPick: (Preset) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_preset)) },
        text = {
            if (missing.isEmpty()) {
                Text(stringResource(R.string.presets_none))
            } else {
                LazyColumn { items(missing) { p -> Text(p.name, Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp)) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.x_cancel)) } },
    )
}
