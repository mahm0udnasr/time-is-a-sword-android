package com.mahm0udnasr.sword.ui.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.mahm0udnasr.sword.BuildConfig
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.data.CommitPipeline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.mahm0udnasr.sword.container
import com.mahm0udnasr.sword.core.rules.dayKey
import com.mahm0udnasr.sword.service.DebugBus
import java.time.Instant
import java.time.ZoneId

/**
 * Hidden debug screen (replaces the extension's slDebug(), step 3.6). Developer
 * text, so English and left-to-right on purpose; only the title is translated.
 */
class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val running by DebugBus.running.collectAsState()
                    val fg by DebugBus.foreground.collectAsState()
                    val screen by DebugBus.screen.collectAsState()
                    val flush by DebugBus.lastFlush.collectAsState()
                    val events by DebugBus.events.collectAsState()
                    val dump by DebugBus.dump.collectAsState()
                    val unsupported by DebugBus.unsupportedBrowsers.collectAsState()
                    val cfg by c.config.flow.collectAsState()
                    val st by c.state.flow.collectAsState()
                    Scaffold { pad ->
                        SelectionContainer {
                            Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
                                Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.titleLarge)
                                val names = cfg.targets.associate { it.id to it.name }
                                Section("Service") {
                                    Line("running", running.toString())
                                    Line("screen", "interactive=${screen.interactive} unlocked=${screen.unlocked}")
                                    Line("prayer lock", run {
                                        val pc = applicationContext.container.prayerClock
                                        val l = pc.active()
                                        (l?.let { "${it.prayer} until ${clock(it.end)}" } ?: "none") + " · next boundary " + (pc.nextBoundary()?.let(::clock) ?: "—")
                                    })
                                    Line("unsupported browsers", unsupported.joinToString().ifEmpty { "none" })
                                    Line("protection", run {
                                        val ctx = this@DebugActivity
                                        val g = com.mahm0udnasr.sword.core.guard.guardActive(cfg.protection, st.guardSuspendedUntil, System.currentTimeMillis())
                                        "guard=${if (g) "on" else "off"}${st.guardSuspendedUntil?.let { " (suspended until ${clock(it)})" } ?: ""}" +
                                            " · autoTime=${com.mahm0udnasr.sword.system.AutoTime.isOn(ctx)} (required=${cfg.protection.requireAutoTime})" +
                                            " · admin=${com.mahm0udnasr.sword.admin.SwordDeviceAdmin.isActive(ctx)}" +
                                            " · a11y enabled=${com.mahm0udnasr.sword.system.HealthCheck.serviceEnabled(ctx)}"
                                    })
                                }
                                Section("Foreground") {
                                    Line("package", fg.pkg ?: "—")
                                    Line("visible", fg.visible.joinToString())
                                    Line("title", fg.title ?: "—")
                                    Line("ignored", fg.ignored.toString())
                                    Line("browser", fg.browser.toString())
                                    Line("host", "${fg.host ?: "—"} (${fg.hostSource ?: "-"})")
                                    Line("target", fg.targetId?.let { "${names[it]} ($it)" } ?: "—")
                                }
                                Section("Session") {
                                    val s = st.session
                                    Line("target", s?.let { names[it.targetId] ?: it.targetId } ?: "—")
                                    Line("since", s?.since?.let(::clock) ?: "—")
                                    Line("verdict", flush?.verdict?.toString() ?: "—")
                                    Line("next wake", listOfNotNull(flush?.blockAt, flush?.nextWarnAt).minOrNull()?.let(::clock) ?: "—")
                                }
                                Section("Today (seconds)") {
                                    val today = st.usage[dayKey(System.currentTimeMillis(), ZoneId.systemDefault())]
                                    if (today == null || today.seconds.isEmpty()) Line("", "nothing yet")
                                    today?.seconds?.forEach { (id, sec) -> Line(names[id] ?: id, String.format(java.util.Locale.ROOT, "%.1f", sec)) }
                                    today?.let { Line("pauses used", it.pausesUsed.toString()) }
                                }
                                if (BuildConfig.DEBUG) {
                                    Section("Test rules (debug build only)") {
                                        val scope = rememberCoroutineScope()
                                        cfg.targets.filter { it.enabled }.forEach { t ->
                                            Text(t.name, style = MaterialTheme.typography.bodyMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                fun set(rule: Rule) = scope.launch { debugApply(c, c.config.value.copy(targets = c.config.value.targets.map { if (it.id == t.id) it.copy(rules = listOf(rule)) else it })) }
                                                OutlinedButton(onClick = { set(Rule.default(Mode.BLOCK)) }) { Text("Block") }
                                                OutlinedButton(onClick = { set(Rule.default(Mode.LIMIT).copy(limitMinutes = 2)) }) { Text("2 min") }
                                                OutlinedButton(onClick = { set(Rule.default(Mode.LIMIT).copy(limitMinutes = 60)) }) { Text("60 min") }
                                                OutlinedButton(onClick = {
                                                    scope.launch { c.state.update { s -> s.copy(usage = s.usage.mapValues { (_, d) -> d.copy(seconds = d.seconds - t.id, warned = d.warned.filterKeys { !it.startsWith(t.id) }) }) } }
                                                }) { Text("Reset") }
                                            }
                                        }
                                    }
                                }
                                Section("Nodes") {
                                    Button(onClick = { DebugBus.requestDump?.invoke() }, enabled = running) { Text(stringResource(R.string.debug_dump)) }
                                    Text(stringResource(R.string.debug_dump_hint), style = MaterialTheme.typography.bodySmall)
                                    if (dump.isNotEmpty()) Mono(dump)
                                }
                                Section("Last 50 events") { Mono(events.joinToString("\n")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** DEBUG BUILDS ONLY: apply a config change without the friction step (it arrives in Phase 5). */
private suspend fun debugApply(c: com.mahm0udnasr.sword.AppContainer, draft: com.mahm0udnasr.sword.core.model.Config) {
    check(BuildConfig.DEBUG)
    val r = c.commit.save(draft)
    if (r is CommitPipeline.Result.NeedsFriction) c.commit.applyConfirmed(r.pending)
}

private fun clock(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0).toString()

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
    content()
}

@Composable
private fun Line(k: String, v: String) = Text(if (k.isEmpty()) v else "$k: $v", fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
private fun Mono(text: String) = Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
