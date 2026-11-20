package com.mahm0udnasr.sword.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mahm0udnasr.sword.admin.SwordDeviceAdmin

/** What full protection needs (Phase 9). Onboarding walks them in this order; the status card lists them. */
enum class CheckId {
    /** "Pause app activity if unused" off. Its switch is on our App info page, so it comes before the service. */
    UNUSED,

    /** Android 13+: a sideloaded app's accessibility switch is greyed out until "Allow restricted settings". */
    RESTRICTED,
    ACCESSIBILITY,
    NOTIFICATIONS,
    BATTERY,
    EXACT_ALARMS,
    ADMIN,
}

/** @property ok null = can't be read (restricted settings): shown as advice, never as ❌ */
data class Check(val id: CheckId, val ok: Boolean?, val required: Boolean)

object Checks {
    fun read(ctx: Context): List<Check> = buildList {
        val pm = ctx.packageManager
        val a11y = HealthCheck.serviceEnabled(ctx)
        val admin = SwordDeviceAdmin.isActive(ctx)
        if (Build.VERSION.SDK_INT >= 30) {
            // Android never pauses an app with an active accessibility service or device admin
            // (One UI 9: the switch is then off and greyed out), so either one counts as done.
            val exempt = runCatching { pm.isAutoRevokeWhitelisted }.getOrDefault(false) || a11y || admin
            add(Check(CheckId.UNUSED, exempt, required = false))
        }
        // Only installs from a file/browser hit restricted settings; once the service is on it's done.
        if (sideloaded(ctx)) add(Check(CheckId.RESTRICTED, if (a11y) true else null, required = false))
        add(Check(CheckId.ACCESSIBILITY, a11y, required = true))
        add(Check(CheckId.NOTIFICATIONS, notificationsOk(ctx), required = true))
        add(Check(CheckId.BATTERY, ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName), required = true))
        if (Build.VERSION.SDK_INT in 31..32) {
            add(Check(CheckId.EXACT_ALARMS, ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(), required = false))
        }
        add(Check(CheckId.ADMIN, admin, required = false))
    }

    fun notificationsOk(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Installed from a file or a browser download (Android 13+ restricted settings apply). */
    private fun sideloaded(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val src = runCatching { ctx.packageManager.getInstallSourceInfo(ctx.packageName).packageSource }.getOrDefault(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED)
        return src == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || src == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    }

    /**
     * The screen that fixes it (notifications on Android 13+ are a permission request instead, see the UI).
     * No FLAG_ACTIVITY_NEW_TASK here: Android's "Activate device admin" screen closes at once when it
     * can't see which app asked (verified on One UI 9). Start it from an Activity; [open] adds the flag
     * only for a non-Activity context.
     */
    @SuppressLint("BatteryLife")
    fun fixIntent(ctx: Context, id: CheckId): Intent? {
        val pkg = Uri.fromParts("package", ctx.packageName, null)
        return when (id) {
            CheckId.UNUSED -> if (Build.VERSION.SDK_INT >= 31) Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            CheckId.RESTRICTED -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            CheckId.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            CheckId.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            CheckId.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            CheckId.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
            CheckId.ADMIN -> SwordDeviceAdmin.activateIntent(ctx)
        }
    }

    /** Start the fix screen of [id]. */
    fun open(ctx: Context, id: CheckId) {
        val i = fixIntent(ctx, id) ?: return
        if (ctx.findActivity() == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    private fun Context.findActivity(): android.app.Activity? {
        var c: Context? = this
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }

    /** These two open our App info page, which the Settings guard closes while the service runs. */
    fun opensAppInfo(id: CheckId) = id == CheckId.UNUSED || id == CheckId.RESTRICTED
}
