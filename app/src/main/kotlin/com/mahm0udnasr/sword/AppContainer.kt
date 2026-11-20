package com.mahm0udnasr.sword

import android.app.Application
import android.content.Context
import com.mahm0udnasr.sword.data.CommitPipeline
import com.mahm0udnasr.sword.data.ConfigRepository
import com.mahm0udnasr.sword.data.Reminders
import com.mahm0udnasr.sword.data.StateRepository
import com.mahm0udnasr.sword.data.firstRunConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Manual DI (step 2.2). One instance per process; the service and the UI share
 * the same repositories and in-memory flows (the app runs in one process).
 */
class AppContainer(context: Context) {
    /** Lives as long as the process. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataDir = File(context.filesDir, "datastore")

    val config = ConfigRepository(dataDir, appScope, firstRunConfig(context.assets.readText("presets.json")))
    val state = StateRepository(dataDir, appScope)
    val commit = CommitPipeline(config)
    val reminders = Reminders(context.assets)
    val pauses = com.mahm0udnasr.sword.data.Pauses(config, state)

    /** Salah First: times and lock windows in memory (Phase 7). */
    val prayerClock = com.mahm0udnasr.sword.prayer.PrayerClock({ config.value.prayer }, cache = { state.value.prayerCache })
    val prayerFetcher = com.mahm0udnasr.sword.prayer.PrayerFetcher(config, state)
    val prayerAllowlist = com.mahm0udnasr.sword.prayer.PrayerAllowlist(context.applicationContext) { config.value }
}

private fun android.content.res.AssetManager.readText(name: String) = open(name).use { it.readBytes().decodeToString() }

class SwordApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Health check (step 8.6); KEEP: scheduling again is harmless.
        runCatching { com.mahm0udnasr.sword.system.HealthCheck.schedule(this) }
    }
}

/** The container from any context. */
val Context.container: AppContainer get() = (applicationContext as SwordApp).container
