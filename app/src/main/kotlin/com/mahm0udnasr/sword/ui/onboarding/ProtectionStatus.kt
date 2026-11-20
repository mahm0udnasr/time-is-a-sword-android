package com.mahm0udnasr.sword.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.guard.guardActive
import com.mahm0udnasr.sword.system.Check
import com.mahm0udnasr.sword.system.CheckId
import com.mahm0udnasr.sword.system.Checks
import com.mahm0udnasr.sword.system.HealthCheck
import com.mahm0udnasr.sword.ui.components.CardTitle
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.settings.suspendProtection
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.launch

/** Texts of one check. */
data class CheckText(val title: Int, val hint: Int, val fix: Int)

fun CheckId.text(): CheckText = when (this) {
    CheckId.UNUSED -> CheckText(R.string.chk_unused, R.string.chk_unusedHint, R.string.chk_unusedFix)
    CheckId.RESTRICTED -> CheckText(R.string.chk_restricted, R.string.chk_restrictedHint, R.string.chk_restrictedFix)
    CheckId.ACCESSIBILITY -> CheckText(R.string.chk_a11y, R.string.chk_a11yHint, R.string.chk_a11yFix)
    CheckId.NOTIFICATIONS -> CheckText(R.string.chk_notif, R.string.chk_notifHint, R.string.chk_notifFix)
    CheckId.BATTERY -> CheckText(R.string.chk_battery, R.string.chk_batteryHint, R.string.chk_batteryFix)
    CheckId.EXACT_ALARMS -> CheckText(R.string.chk_alarms, R.string.chk_alarmsHint, R.string.chk_alarmsFix)
    CheckId.ADMIN -> CheckText(R.string.admin_title, R.string.chk_adminHint, R.string.admin_activate)
}

/** The checks, re-read whenever the screen comes back (the user fixes them in Android's Settings). */
@Composable
fun rememberChecks(): List<Check> {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    return remember(tick) { Checks.read(ctx) }
}

/**
 * Runs the fix of a check. Notifications are a permission request first;
 * App info steps need protection off for 5 minutes once the service runs,
 * because the guard closes that page (and it also holds Force stop).
 */
@Composable
fun rememberFixer(c: AppContainer, committer: Committer): (CheckId) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askedNotif by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askedNotif = true }
    return { id ->
        when {
            id == CheckId.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 && !askedNotif && !Checks.notificationsOk(ctx) ->
                notif.launch(Manifest.permission.POST_NOTIFICATIONS)
            Checks.opensAppInfo(id) && guardWouldBlock(ctx, c) -> scope.launch {
                if (suspendProtection(ctx, c, committer)) open(ctx, id)
            }
            else -> open(ctx, id)
        }
    }
}

private fun guardWouldBlock(ctx: Context, c: AppContainer) =
    HealthCheck.serviceEnabled(ctx) && guardActive(c.config.value.protection, c.state.value.guardSuspendedUntil, System.currentTimeMillis())

private fun open(ctx: Context, id: CheckId) = Checks.open(ctx, id)

@Composable
fun CheckMark(ok: Boolean?, required: Boolean) {
    val t = LocalSwordColors.current
    when (ok) {
        true -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.chk_ok), tint = t.accent, modifier = Modifier.size(22.dp))
        false -> Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.chk_missing), tint = if (required) t.danger else t.gold, modifier = Modifier.size(22.dp))
        null -> Icon(Icons.Outlined.Info, null, tint = t.muted, modifier = Modifier.size(22.dp))
    }
}

/** Settings › Protection status (step 9.9): every item ✅/❌ with its fix. */
@Composable
fun ProtectionStatusCard(c: AppContainer, committer: Committer) {
    val t = LocalSwordColors.current
    val checks = rememberChecks()
    val fix = rememberFixer(c, committer)
    SwordCard {
        CardTitle(stringResource(R.string.status_title))
        checks.forEach { ch ->
            val txt = ch.id.text()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CheckMark(ch.ok, ch.required)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(txt.title), color = t.text, fontSize = 14.sp)
                    if (ch.ok != true) Text(stringResource(txt.hint), color = t.muted, fontSize = 12.sp)
                }
                if (ch.ok != true) {
                    OutlinedButton(onClick = { fix(ch.id) }, modifier = Modifier.padding(start = 4.dp)) {
                        Text(stringResource(R.string.chk_fix), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
