package com.mahm0udnasr.sword.ui.targets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.ui.components.AppAvatar
import com.mahm0udnasr.sword.ui.components.InstalledApp
import com.mahm0udnasr.sword.ui.components.InstalledApps
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors

/**
 * Installed-apps picker (step 5.5): icons, labels, search, multi-select.
 * An app already in another target is shown but can't be picked (one target per app).
 * @param except a target whose own apps may be re-picked (the editor's target)
 */
@Composable
fun AppPicker(targets: List<Target>, except: String? = null, onDismiss: () -> Unit, onPick: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { apps = InstalledApps.list(ctx) }
    val owner = targets.filter { it.id != except }.flatMap { tg -> tg.packages.map { it to tg.name } }.toMap()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = t.bg) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.picker_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.text)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(R.string.picker_search)) })
                val list = apps
                if (list == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    val q = query.trim().lowercase()
                    val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
                    if (shown.isEmpty()) Text(stringResource(R.string.picker_none), color = t.muted)
                    LazyColumn(Modifier.weight(1f)) {
                        items(shown, key = { it.pkg }) { app ->
                            val taken = owner[app.pkg]
                            Row(
                                Modifier.fillMaxWidth().toggleable(app.pkg in chosen || taken != null, enabled = taken == null, role = androidx.compose.ui.semantics.Role.Checkbox) { on ->
                                    if (on) chosen.add(app.pkg) else chosen.remove(app.pkg)
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppAvatar(app.pkg, app.label, 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = if (taken == null) t.text else t.muted)
                                    Text(taken?.let { tr(R.string.picker_inTarget, "name" to ltr(it)) } ?: app.pkg, color = t.muted, fontSize = 11.sp, maxLines = 1)
                                }
                                Checkbox(app.pkg in chosen || taken != null, onCheckedChange = null, enabled = taken == null)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.x_cancel)) }
                    Button(onClick = { onPick(chosen.toList()) }, enabled = chosen.isNotEmpty()) { Text(tr(R.string.picker_add, "n" to chosen.size)) }
                }
            }
        }
    }
}
