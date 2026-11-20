package com.mahm0udnasr.sword.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.core.rules.Verdict
import com.mahm0udnasr.sword.ui.MainActivity
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.formatDuration
import com.mahm0udnasr.sword.ui.format.ltr
import com.mahm0udnasr.sword.ui.format.prayerName
import com.mahm0udnasr.sword.ui.format.tr

/**
 * The persistent status notification (Q7: yes, always; step 10.2): what is
 * being timed and how much is left today, the prayer lock, or "Protected".
 * Quiet (low importance, no sound); tap opens the Overview. The user can hide
 * it in Android's notification settings. Posted only while the service runs.
 */
object StatusNotification {
    private const val CHANNEL = "status"
    private const val ID = 2

    /** The last text posted, so identical updates (every 30 s) aren't re-posted. */
    private var last: Pair<String, String>? = null

    /**
     * @param targetId the target being timed, or null
     * @param prayer the active prayer lock (name resource + end), or null
     */
    fun update(ctx: Context, cfg: Config, targetId: String?, verdict: Verdict?, prayer: Pair<Int, Long>?) {
        val target = targetId?.let { id -> cfg.targets.firstOrNull { it.id == id } }
        val title: String
        val text: String
        when {
            prayer != null -> {
                title = ctx.getString(R.string.x_nav_prayer)
                text = ctx.tr(R.string.salah_locked, "name" to ctx.getString(prayer.first), "time" to ltr(ctx.formatClock(prayer.second)))
            }
            target != null && verdict is Verdict.Allowed -> {
                title = target.name
                text = when {
                    verdict.paused && verdict.blockAt != null -> ctx.tr(R.string.status_paused, "time" to ltr(ctx.formatClock(verdict.blockAt!!)))
                    verdict.remainingSec != null -> ctx.tr(R.string.status_left, "dur" to ltr(ctx.formatDuration(verdict.remainingSec!!)))
                    verdict.blockKind == BlockKind.SCHEDULE && verdict.blockAt != null -> ctx.tr(R.string.status_until, "time" to ltr(ctx.formatClock(verdict.blockAt!!)))
                    else -> ctx.getString(R.string.status_noLimit)
                }
            }
            else -> {
                title = ctx.getString(R.string.status_protected)
                val n = cfg.targets.count { it.enabled }
                text = if (n == 0) ctx.getString(R.string.status_nothingOn) else ctx.tr(R.string.status_onCount, "n" to n)
            }
        }
        if (last == title to text) return
        if (!Checks.notificationsOk(ctx)) return
        last = title to text
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.status_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(com.mahm0udnasr.sword.R.drawable.ic_stat_sword)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }

    fun clear(ctx: Context) {
        last = null
        runCatching { NotificationManagerCompat.from(ctx).cancel(ID) }
    }

    /** Name resource of a prayer, for [update]. */
    fun prayerRes(p: com.mahm0udnasr.sword.core.prayer.Prayer): Int = prayerName(p)
}
