package com.mahm0udnasr.sword.system

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Automatic date & time (D12, step 8.5). While it is off and the user requires
 * it, limit/schedule targets are blocked (reason clock): otherwise moving the
 * date forward would hand out a new day's budget.
 */
object AutoTime {
    fun isOn(ctx: Context): Boolean =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, 1) == 1 }.getOrDefault(true)

    /** Is the clock trusted, given the user's protection switch? */
    fun trusted(ctx: Context, requireAutoTime: Boolean): Boolean = !requireAutoTime || isOn(ctx)

    /** Calls [onChange] on the main thread whenever the switch changes (no broadcast is sent for it). */
    fun observe(ctx: Context, onChange: (Boolean) -> Unit): ContentObserver {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange(isOn(ctx))
        }
        ctx.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AUTO_TIME), false, o)
        return o
    }
}
