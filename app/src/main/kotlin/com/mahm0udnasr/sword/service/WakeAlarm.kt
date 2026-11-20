package com.mahm0udnasr.sword.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Backup for the tracker's coroutine timer (plan §8.3 "exact moments", step 6.4):
 * an exact alarm at the next block moment, warning or pause end, so blocking
 * happens on time even if the process was frozen. The receiver just asks the
 * running service to flush; if the service isn't running there is nothing to do.
 */
object WakeAlarm {
    /** One alarm per kind (different request codes, so they don't replace each other). */
    const val TRACKER = 7
    const val PRAYER = 8

    /** Set by the service while it is connected. */
    @Volatile
    var onWake: (() -> Unit)? = null

    fun schedule(ctx: Context, at: Long?, kind: Int = TRACKER) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, kind, Intent(ctx, WakeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            am.cancel(pi)
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }
}

class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { WakeAlarm.onWake?.invoke() }
    }
}
