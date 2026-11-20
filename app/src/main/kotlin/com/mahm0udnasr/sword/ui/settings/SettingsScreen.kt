package com.mahm0udnasr.sword.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.provider.Settings
import com.mahm0udnasr.sword.admin.SwordDeviceAdmin
import com.mahm0udnasr.sword.core.model.Protection
import com.mahm0udnasr.sword.system.AutoTime
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.ltr
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.BuildConfig
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Lang
import com.mahm0udnasr.sword.core.model.Theme
import com.mahm0udnasr.sword.ui.components.CardTitle
import com.mahm0udnasr.sword.ui.components.LocalSnackbar
import com.mahm0udnasr.sword.ui.components.PageHeader
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.debug.DebugActivity
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.launch

/** Appearance (never needs friction), protection (looser = friction), recovery, about. */
@Composable
fun SettingsScreen(c: AppContainer, committer: Committer, onAbout: () -> Unit) {
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(stringResource(R.string.nav_settings))

        SwordCard {
            CardTitle(stringResource(R.string.settings_appearance))
            val themes = listOf(Theme.SYSTEM to Icons.Outlined.PhoneAndroid, Theme.LIGHT to Icons.Outlined.LightMode, Theme.DARK to Icons.Outlined.DarkMode)
            val labels = listOf(R.string.x_theme_system, R.string.x_theme_light, R.string.x_theme_dark)
            SingleChoiceSegmentedButtonRow {
                themes.forEachIndexed { i, (th, icon) ->
                    SegmentedButton(
                        selected = cfg.appearance.theme == th,
                        onClick = { scope.launch { committer.commit(c.config.value.copy(appearance = c.config.value.appearance.copy(theme = th))) } },
                        shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(labels[i]), fontSize = 12.sp, maxLines = 1) },
                    )
                }
            }
            Text(stringResource(R.string.settings_language), color = t.muted, fontSize = 13.sp)
            SingleChoiceSegmentedButtonRow {
                listOf(Lang.AR to "العربية", Lang.EN to "English").forEachIndexed { i, (lang, label) ->
                    SegmentedButton(
                        selected = cfg.appearance.lang == lang,
                        onClick = { scope.launch { committer.commit(c.config.value.copy(appearance = c.config.value.appearance.copy(lang = lang))) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        label = { Text(label) },
                    )
                }
            }
        }

        com.mahm0udnasr.sword.ui.onboarding.ProtectionStatusCard(c, committer)
        ProtectionCard(c, committer)

        WidgetsCard()
        BackupCard(c, committer)

        // About: author, links, licences, recovery steps, version (×7 → Debug).
        SwordCard(Modifier.clickable(onClick = onAbout)) {
            CardTitle(stringResource(R.string.settings_about))
            Text(stringResource(R.string.about_open), color = t.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Protection (Phase 8): the three switches (off = looser = friction), the
 * legitimate "turn off protection for 5 minutes" (step 8.3) and Device admin (8.4).
 */
@Composable
private fun ProtectionCard(c: AppContainer, committer: Committer) {
    val cfg by c.config.flow.collectAsStateWithLifecycle()
    val st by c.state.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current

    // Android-side facts change outside the app: re-read them whenever the screen comes back.
    var adminOn by remember { mutableStateOf(SwordDeviceAdmin.isActive(ctx)) }
    var autoTimeOn by remember { mutableStateOf(AutoTime.isOn(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        adminOn = SwordDeviceAdmin.isActive(ctx)
        autoTimeOn = AutoTime.isOn(ctx)
        now = System.currentTimeMillis()
        onPauseOrDispose {}
    }
    val suspendedUntil = st.guardSuspendedUntil?.takeIf { it > now }
    LaunchedEffect(suspendedUntil) {
        // Tick until the suspension ends, so the card updates by itself.
        while (suspendedUntil != null && System.currentTimeMillis() < suspendedUntil) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    fun commitProtection(p: Protection) = scope.launch {
        val out = committer.commit(c.config.value.copy(protection = p), ctx.getString(R.string.protection_loosenTitle))
        if (out == Committer.Outcome.CANCELLED) snack.showSnackbar(ctx.getString(R.string.x_cancelled))
    }

    SwordCard {
        CardTitle(stringResource(R.string.protection_title))
        SwitchRow(stringResource(R.string.protection_guard), stringResource(R.string.protection_guardHint), cfg.protection.guardEnabled) {
            commitProtection(c.config.value.protection.copy(guardEnabled = it))
        }
        SwitchRow(stringResource(R.string.protection_autoTime), stringResource(R.string.protection_autoTimeHint), cfg.protection.requireAutoTime) {
            commitProtection(c.config.value.protection.copy(requireAutoTime = it))
        }
        if (cfg.protection.requireAutoTime && !autoTimeOn) {
            Text(stringResource(R.string.protection_autoTimeIsOff), color = t.danger, fontSize = 13.sp)
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) }) { Text(stringResource(R.string.block_clockOpen)) }
        }
        SwitchRow(stringResource(R.string.protection_browsers), stringResource(R.string.protection_browsersHint), cfg.protection.blockUnsupportedBrowsers) {
            commitProtection(c.config.value.protection.copy(blockUnsupportedBrowsers = it))
        }
    }

    SwordCard {
        CardTitle(stringResource(R.string.admin_title))
        Text(stringResource(if (adminOn) R.string.admin_on else R.string.admin_off), color = t.muted, fontSize = 13.sp)
        if (adminOn) {
            OutlinedButton(onClick = {
                scope.launch {
                    if (deactivateAdmin(ctx, committer)) adminOn = SwordDeviceAdmin.isActive(ctx) else snack.showSnackbar(ctx.getString(R.string.x_cancelled))
                }
            }) { Text(stringResource(R.string.admin_deactivate)) }
        } else {
            // Stricter: no friction. Android shows its own confirmation.
            Button(onClick = { runCatching { ctx.startActivity(SwordDeviceAdmin.activateIntent(ctx)) } }) { Text(stringResource(R.string.admin_activate)) }
        }
    }

    if (cfg.protection.guardEnabled) {
        SwordCard {
            if (suspendedUntil != null) {
                Text(tr(R.string.protection_suspended, "time" to ltr(ctx.formatClock(suspendedUntil))), color = t.danger, fontSize = 14.sp)
                Button(onClick = { scope.launch { resumeProtection(c) } }) { Text(stringResource(R.string.protection_resume)) }
            } else {
                Text(tr(R.string.protection_suspend, "n" to GUARD_SUSPEND_MIN), color = t.text)
                Text(stringResource(R.string.protection_suspendHint), color = t.muted, fontSize = 12.sp)
                OutlinedButton(onClick = {
                    scope.launch {
                        if (suspendProtection(ctx, c, committer)) {
                            now = System.currentTimeMillis()
                        } else {
                            snack.showSnackbar(ctx.getString(R.string.x_cancelled))
                        }
                    }
                }) { Text(tr(R.string.protection_suspend, "n" to GUARD_SUSPEND_MIN)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalSwordColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = t.text)
            Text(hint, color = t.muted, fontSize = 12.sp)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

/** Export / import (step 10.7) through Android's file picker; import = a normal save (looser parts need friction). */
@Composable
private fun BackupCard(c: AppContainer, committer: Committer) {
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current
    val export = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(com.mahm0udnasr.sword.data.Backup.export(c.config.value).encodeToByteArray()) }
                }
            }.isSuccess
            snack.showSnackbar(ctx.getString(if (ok) R.string.backup_exported else R.string.backup_writeFailed))
        }
    }
    val import = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
            }.getOrNull()
            val next = text?.let { com.mahm0udnasr.sword.data.Backup.parse(it) }
            if (next == null) {
                snack.showSnackbar(ctx.getString(R.string.backup_invalid))
                return@launch
            }
            val msg = when (committer.commit(next, ctx.getString(R.string.backup_loosenTitle))) {
                Committer.Outcome.NO_CHANGE -> R.string.backup_same
                Committer.Outcome.APPLIED -> R.string.backup_imported
                Committer.Outcome.CANCELLED -> R.string.x_cancelled
            }
            snack.showSnackbar(ctx.getString(msg))
        }
    }
    SwordCard {
        CardTitle(stringResource(R.string.backup_title))
        Text(stringResource(R.string.backup_hint), color = t.muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { export.launch(com.mahm0udnasr.sword.data.Backup.fileName()) }) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.backup_import)) }
        }
    }
}

/** Home-screen widgets: ask the launcher to place one (it shows its own confirmation). */
@Composable
private fun WidgetsCard() {
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    val awm = remember { android.appwidget.AppWidgetManager.getInstance(ctx) }
    if (!awm.isRequestPinAppWidgetSupported) return
    fun pin(cls: Class<*>) = runCatching { awm.requestPinAppWidget(android.content.ComponentName(ctx, cls), null, null) }
    SwordCard {
        CardTitle(stringResource(R.string.w_cardTitle))
        Text(stringResource(R.string.w_cardHint), color = t.muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pin(com.mahm0udnasr.sword.widget.TodayWidgetReceiver::class.java) }) { Text(stringResource(R.string.w_todayName)) }
            OutlinedButton(onClick = { pin(com.mahm0udnasr.sword.widget.PrayerWidgetReceiver::class.java) }) { Text(stringResource(R.string.w_prayerName)) }
        }
    }
}
