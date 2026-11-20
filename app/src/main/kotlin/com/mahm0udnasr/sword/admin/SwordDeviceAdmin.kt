package com.mahm0udnasr.sword.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.service.DebugBus

/**
 * Device admin (plan §10.3, step 8.4). Uses no policies: being active is enough
 * for Android to refuse uninstalling until it is switched off, and its page in
 * Settings is guarded. Switching it off inside the app goes through friction.
 */
class SwordDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        DebugBus.event("ADMIN enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        DebugBus.event("ADMIN disabled")
    }

    /** Shown by Android in the "deactivate" dialog. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disableWarning)

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, SwordDeviceAdmin::class.java)

        fun isActive(ctx: Context): Boolean =
            runCatching { ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(ctx)) }.getOrDefault(false)

        /** Android's own "Activate device admin app?" screen. */
        fun activateIntent(ctx: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(ctx))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ctx.getString(R.string.admin_explain))

        /** Only after the friction step (a looser change). */
        fun deactivate(ctx: Context) {
            runCatching { ctx.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(ctx)) }
        }
    }
}
