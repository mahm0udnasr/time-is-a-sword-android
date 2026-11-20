package com.mahm0udnasr.sword.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.service.SwordAccessibilityService
import java.util.concurrent.TimeUnit

/**
 * Health check (step 8.6): every 15 minutes and after boot, if our
 * accessibility service is not switched on, a high-priority notification says
 * so ("Protection is off — tap to fix"). It goes away once the service is back.
 *
 * Limitation: a force stop (One UI: swiping the app away in Recents) also
 * cancels this app's scheduled work until the app is opened again, so this
 * catches a crash-disabled or OEM-disabled service, not a force stop. That one
 * is prevented by excludeFromRecents (step 8.6b).
 */
object HealthCheck {
    private const val CHANNEL = "protection"
    private const val NOTIFICATION_ID = 1
    private const val PERIODIC = "health-check"
    private const val ONCE = "health-check-once"

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<Worker>(15, TimeUnit.MINUTES).build())
    }

    /** One check after [delayMin] minutes (after boot the service binds only once the phone is unlocked). */
    fun checkSoon(ctx: Context, delayMin: Long) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            ONCE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<Worker>().setInitialDelay(delayMin, TimeUnit.MINUTES).build(),
        )
    }

    /** Is our accessibility service in Android's enabled list? */
    fun serviceEnabled(ctx: Context): Boolean {
        val mine = ComponentName(ctx, SwordAccessibilityService::class.java)
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { ComponentName.unflattenFromString(it.trim()) == mine }
    }

    fun run(ctx: Context) {
        val on = serviceEnabled(ctx)
        com.mahm0udnasr.sword.service.DebugBus.event("HEALTH service enabled=$on")
        if (on) clear(ctx) else notifyOff(ctx)
    }

    fun clear(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)

    private fun notifyOff(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.health_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(com.mahm0udnasr.sword.R.drawable.ic_stat_sword)
            .setContentTitle(ctx.getString(R.string.health_title))
            .setContentText(ctx.getString(R.string.health_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.health_text)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n) }
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            runCatching { run(applicationContext) }
            return Result.success()
        }
    }
}
