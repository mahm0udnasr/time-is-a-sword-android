package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.rules.normalizeDomain
import kotlinx.serialization.Serializable

/** One entry of assets/presets.json. */
@Serializable
data class Preset(val id: String, val name: String, val packages: List<String> = emptyList(), val domains: List<String> = emptyList())

/**
 * The first-run config: every preset as a target, all **switched off**, so
 * installing never cuts the user off; switching one on is a stricter change,
 * so it is always instant. No groups, Salah First off (port of ensureSeeded()).
 * Preset ids are stable ("p_instagram"), so the same preset is recognizable later.
 */
fun firstRunConfig(presetsJson: String): Config {
    val presets = StoreJson.decodeFromString<List<Preset>>(presetsJson)
    val targets = presets.map { p ->
        makeTarget(
            id = p.id,
            name = p.name,
            packages = p.packages.distinct(),
            domains = p.domains.mapNotNull(::normalizeDomain).distinct(),
            enabled = false,
        )
    }
    return Config(targets = targets)
}
