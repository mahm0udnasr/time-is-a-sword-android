package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.model.Config
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Export / import (plan §7.5, step 10.7). Export = the Config as pretty JSON.
 * Import = the file is treated as the "next" config and goes through the
 * commit pipeline: stricter parts apply at once, looser ones need friction.
 */
object Backup {
    private val pretty = Json(from = StoreJson) { prettyPrint = true }

    fun fileName(today: LocalDate = LocalDate.now()) = "time-is-a-sword-backup-$today.json"

    fun export(c: Config): String = pretty.encodeToString(Config.serializer(), c)

    /** @return the config to commit, or null if the file isn't a backup of this app */
    fun parse(text: String): Config? = runCatching {
        val c = StoreJson.decodeFromString(Config.serializer(), text)
        // A backup always has targets or groups; an empty object would wipe everything.
        if (c.targets.isEmpty() && c.groups.isEmpty()) null else ConfigMigrations.migrate(c)
    }.getOrNull()
}
