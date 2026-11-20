package com.mahm0udnasr.sword.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Is the phone "in use"? Screen interactive **and** unlocked (plan §8.3, step 3.2).
 * Registered dynamically inside the service. The keyguard can change without
 * a broadcast (Smart Lock, face unlock), so [refresh] is also called on every
 * accessibility event and tick.
 */
class ScreenState(private val context: Context) {
    data class Screen(val interactive: Boolean, val unlocked: Boolean) {
        val active: Boolean get() = interactive && unlocked
    }

    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Screen> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            refresh()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun refresh(): Screen = read().also { _state.value = it }

    private fun read() = Screen(interactive = power.isInteractive, unlocked = !keyguard.isKeyguardLocked)
}
