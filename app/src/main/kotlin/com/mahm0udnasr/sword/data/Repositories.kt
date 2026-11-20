package com.mahm0udnasr.sword.data

import androidx.datastore.core.DataMigration
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.model.State
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * Schema migrations for `config.json` (step 2.4). Each step takes a config of
 * version n and returns version n + 1.
 */
object ConfigMigrations {
    /** index i migrates version i + 1 → i + 2. */
    internal val steps: List<(Config) -> Config> = listOf(
        // 1 → 2 (Phase 12): the Short-videos target, switched off.
        { c -> withShortsTarget(c) },
    )

    fun migrate(c: Config): Config {
        var cur = c
        while (cur.schemaVersion < Defaults.SCHEMA_VERSION) {
            val step = steps.getOrNull(cur.schemaVersion - 1)
            cur = (step?.invoke(cur) ?: cur).copy(schemaVersion = cur.schemaVersion + 1)
        }
        return cur
    }

    val dataMigration: DataMigration<Config> = object : DataMigration<Config> {
        override suspend fun shouldMigrate(currentData: Config): Boolean = currentData.schemaVersion < Defaults.SCHEMA_VERSION
        override suspend fun migrate(currentData: Config): Config = ConfigMigrations.migrate(currentData)
        override suspend fun cleanUp() {}
    }
}

/**
 * User choices (`config.json`). On first run (no file yet) the value is
 * [firstRun]: the preset targets, all switched off (step 2.3).
 */
class ConfigRepository(dir: File, scope: CoroutineScope, firstRun: Config) {
    private val store = JsonStore(
        File(dir, "config.json"), Config.serializer(), firstRun, scope,
        migrations = listOf(ConfigMigrations.dataMigration),
    )
    val flow = store.flow
    val value: Config get() = store.value
    suspend fun awaitLoaded() = store.awaitLoaded()
    suspend fun update(transform: (Config) -> Config) = store.update(transform)
}

/** Live data (`state.json`): usage, session, pauses, caches. */
class StateRepository(dir: File, scope: CoroutineScope) {
    private val store = JsonStore(File(dir, "state.json"), State.serializer(), State(), scope)
    val flow = store.flow
    val value: State get() = store.value
    suspend fun awaitLoaded() = store.awaitLoaded()
    suspend fun update(transform: (State) -> State) = store.update(transform)
}
