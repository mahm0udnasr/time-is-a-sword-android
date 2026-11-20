package com.mahm0udnasr.sword.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.makeGroup
import com.mahm0udnasr.sword.core.model.newId
import com.mahm0udnasr.sword.ui.components.CardTitle
import com.mahm0udnasr.sword.ui.components.EmptyState
import com.mahm0udnasr.sword.ui.components.LocalSnackbar
import com.mahm0udnasr.sword.ui.components.PageHeader
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.format.describeRules
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.rules.RuleDraft
import com.mahm0udnasr.sword.ui.rules.RuleEditor
import com.mahm0udnasr.sword.ui.rules.readRules
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.launch

/** Groups: shared budgets (step 5.7). */
@Composable
fun GroupsScreen(c: AppContainer, committer: Committer, onOpen: (String) -> Unit) {
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current
    var newName by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader(stringResource(R.string.x_nav_groups), stringResource(R.string.x_groups_sub)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newName, { newName = it.take(40); error = null }, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.x_groups_add)) })
                Button(onClick = {
                    val n = newName.trim()
                    error = when {
                        n.isEmpty() -> ctx.getString(R.string.x_err_groupNameEmpty)
                        cfg.groups.any { it.name.equals(n, ignoreCase = true) } -> ctx.tr(R.string.x_err_groupNameDup, "name" to n)
                        else -> null
                    }
                    if (error == null) {
                        scope.launch {
                            committer.commit(c.config.value.copy(groups = c.config.value.groups + makeGroup(newId("g", System.currentTimeMillis()), n)))
                            newName = ""
                            snack.showSnackbar(ctx.tr(R.string.x_group_added, "name" to n))
                        }
                    }
                }) { Text(stringResource(R.string.x_add)) }
            }
            error?.let { Text(it, color = t.danger, fontSize = 13.sp) }
        }
        if (cfg.groups.isEmpty()) item { EmptyState(Icons.Outlined.Layers, stringResource(R.string.x_groups_empty)) }
        items(cfg.groups, key = { it.id }) { g ->
            val members = cfg.targets.filter { it.groupId == g.id }
            SwordCard(Modifier.clickable { onOpen(g.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Layers, null, tint = t.accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(g.name, fontWeight = FontWeight.SemiBold, color = if (g.enabled) t.text else t.muted)
                        Text(ctx.describeRules(g.rules), color = t.muted, fontSize = 12.sp)
                        Text(
                            if (members.isEmpty()) stringResource(R.string.x_group_noSites) else members.joinToString(ctx.getString(R.string.x_list_sep)) { ltr(it.name) },
                            color = t.muted, fontSize = 12.sp,
                        )
                    }
                    Switch(g.enabled, onCheckedChange = { on ->
                        scope.launch {
                            val out = committer.commit(
                                c.config.value.copy(groups = c.config.value.groups.map { if (it.id == g.id) it.copy(enabled = on) else it }),
                                ctx.tr(R.string.x_group_switchOffTitle, "name" to g.name),
                            )
                            snack.showSnackbar(
                                if (out == Committer.Outcome.CANCELLED) ctx.getString(R.string.x_cancelled)
                                else ctx.tr(if (on) R.string.x_group_switchedOn else R.string.x_group_switchedOff, "name" to g.name),
                            )
                        }
                    })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun GroupEditorScreen(c: AppContainer, committer: Committer, id: String, onDone: () -> Unit) {
    val original = remember { c.config.value.groups.firstOrNull { it.id == id } }
    if (original == null) {
        onDone()
        return
    }
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current
    val targets = remember { c.config.value.targets }
    var name by remember { mutableStateOf(original.name) }
    val members = remember { targets.filter { it.groupId == id }.map { it.id }.toMutableStateList() }
    val rules = remember { original.rules.map(RuleDraft::of).toMutableStateList() }
    val errors = remember { mutableStateListOf<String>() }

    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(tr(R.string.x_group_edit, "name" to original.name))
        SwordCard {
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.x_group_name)) })
        }
        SwordCard {
            CardTitle(stringResource(R.string.group_members))
            if (targets.isEmpty()) Text(stringResource(R.string.x_group_addSitesFirst), color = t.muted, fontSize = 13.sp)
            targets.forEach { tg ->
                val other = tg.groupId != null && tg.groupId != id
                Row(Modifier.fillMaxWidth().toggleable(tg.id in members, role = androidx.compose.ui.semantics.Role.Checkbox) { on -> if (on) members.add(tg.id) else members.remove(tg.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(tg.id in members, onCheckedChange = null)
                    Text(tg.name, color = if (tg.enabled) t.text else t.muted, modifier = Modifier.padding(start = 8.dp))
                    if (other) Text(" " + stringResource(R.string.x_group_movesHere), color = t.muted, fontSize = 12.sp)
                }
            }
        }
        SwordCard {
            CardTitle(stringResource(R.string.x_group_sharedRules))
            RuleEditor(rules, null)
        }
        errors.forEach { Text("• $it", color = t.danger, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                scope.launch {
                    val cur = c.config.value
                    val out = committer.commit(
                        cur.copy(groups = cur.groups.filter { it.id != id }, targets = cur.targets.map { if (it.groupId == id) it.copy(groupId = null) else it }),
                        ctx.tr(R.string.x_group_removeTitle, "name" to original.name),
                    )
                    if (out != Committer.Outcome.CANCELLED) {
                        snack.showSnackbar(ctx.tr(R.string.x_group_removed, "name" to original.name))
                        onDone()
                    } else {
                        snack.showSnackbar(ctx.getString(R.string.x_cancelled))
                    }
                }
            }) { Text(stringResource(R.string.target_delete), color = t.danger) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.x_cancel)) }
            Button(onClick = {
                errors.clear()
                val (parsed, ruleErrors) = ctx.readRules(rules, allowEmpty = false)
                val n = name.trim()
                if (n.isEmpty()) errors += ctx.getString(R.string.x_err_groupNameMissing)
                if (c.config.value.groups.any { it.id != id && it.name.equals(n, ignoreCase = true) }) errors += ctx.tr(R.string.x_err_groupNameDup, "name" to n)
                errors += ruleErrors
                if (errors.isNotEmpty()) return@Button
                scope.launch {
                    val cur = c.config.value
                    val draft = cur.copy(
                        groups = cur.groups.map { if (it.id == id) it.copy(name = n, rules = parsed) else it },
                        targets = cur.targets.map {
                            when {
                                it.id in members -> it.copy(groupId = id)
                                it.groupId == id -> it.copy(groupId = null)
                                else -> it
                            }
                        },
                    )
                    val out = committer.commit(draft, ctx.tr(R.string.x_group_loosenTitle, "name" to n))
                    if (out == Committer.Outcome.CANCELLED) {
                        errors += ctx.getString(R.string.x_cancelled)
                    } else {
                        snack.showSnackbar(ctx.getString(R.string.x_saved))
                        onDone()
                    }
                }
            }) { Text(stringResource(R.string.x_save)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
