package com.mahm0udnasr.sword.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot or an app update (step 8.7): keep the health check scheduled
 * and check once a little later. The rest needs no receiver: the accessibility
 * service is bound again after the first unlock, and on connecting it discards
 * the stale session (Tracker.start) and reschedules every timer and alarm.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> runCatching {
                HealthCheck.schedule(context)
                HealthCheck.checkSoon(context, 3)
            }
        }
    }
}
