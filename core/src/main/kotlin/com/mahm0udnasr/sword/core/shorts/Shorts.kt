package com.mahm0udnasr.sword.core.shorts

import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.model.Target
import com.mahm0udnasr.sword.core.rules.hostMatches
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Short videos (Phase 12): the short-video *part* of an app or site (YouTube
 * Shorts, Instagram Reels…) and apps that are only short videos (TikTok…).
 * The sections come from `assets/shorts.json` so they can be fixed without code.
 */
@Serializable
data class ShortsSection(
    val id: String,
    val name: Map<String, String> = emptyMap(),
    /** "inApp" (the player inside a bigger app) or "wholeApp". */
    val kind: String = IN_APP,
    val packages: List<String> = emptyList(),
    /** In-app: view ids of the full-screen player (short names, without "pkg:id/"). */
    val ids: List<String> = emptyList(),
    /** In-app: a *selected* node whose content description starts with one of these (apps without view ids). */
    val selectedDesc: List<String> = emptyList(),
    /** "host/path-prefix" in a browser, e.g. "youtube.com/shorts/". */
    val web: List<String> = emptyList(),
    val verified: Boolean = false,
) {
    val wholeApp: Boolean get() = kind == WHOLE_APP

    companion object {
        const val IN_APP = "inApp"
        const val WHOLE_APP = "wholeApp"
    }
}

@Serializable
data class ShortsRules(val sections: List<ShortsSection> = emptyList()) {
    fun byId(id: String?): ShortsSection? = sections.firstOrNull { it.id == id }

    /** Packages whose windows must be searched for an in-app player. */
    val inAppPackages: Set<String> by lazy { sections.filter { !it.wholeApp }.flatMap { it.packages }.toSet() }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): ShortsRules = json.decodeFromString(serializer(), text)
    }
}

/** The one Short-videos target (pinned in Apps & sites). */
const val SHORTS_TARGET_ID = "p_shorts"

/** Q11: when first switched on it limits short videos to this many minutes a day. */
const val SHORTS_DEFAULT_LIMIT_MIN = 15

/** The Short-videos target as first created: off, every section on, a daily limit (Q11). */
fun defaultShortsTarget(sectionIds: List<String>): Target = Target(
    id = SHORTS_TARGET_ID,
    name = "Short videos",
    enabled = false,
    sections = sectionIds,
    rules = listOf(Rule.default(Mode.LIMIT).copy(limitMinutes = SHORTS_DEFAULT_LIMIT_MIN)),
)

/** Section ids known when the target is created by a migration (the app adds new ones from shorts.json). */
val DEFAULT_SECTION_IDS = listOf("youtube_shorts", "instagram_reels", "facebook_reels", "snapchat_spotlight", "tiktok", "other_short_apps")

/**
 * The in-app short-video player in front, from what the window shows.
 * @param ids full view ids seen ("com.google.android.youtube:id/reel_watch_player")
 * @param selectedDescs content descriptions of *selected* nodes
 */
fun matchInApp(rules: ShortsRules, pkg: String, ids: Set<String>, selectedDescs: List<String>): ShortsSection? =
    rules.sections.firstOrNull { s ->
        !s.wholeApp && pkg in s.packages && (
            s.ids.any { "$pkg:id/$it" in ids } ||
                selectedDescs.any { d -> s.selectedDesc.any { d.startsWith(it, ignoreCase = true) } }
            )
    }

/** An app that is only short videos. */
fun matchWholeApp(rules: ShortsRules, pkg: String): ShortsSection? =
    rules.sections.firstOrNull { it.wholeApp && pkg in it.packages }

/** A short-video page in a browser: host (subdomains too) + path prefix. */
fun matchWeb(rules: ShortsRules, host: String?, path: String?): ShortsSection? {
    if (host == null) return null
    val p = path?.ifEmpty { "/" } ?: "/"
    return rules.sections.firstOrNull { s ->
        s.web.any { entry ->
            val slash = entry.indexOf('/')
            val h = if (slash < 0) entry else entry.substring(0, slash)
            val prefix = if (slash < 0) "/" else entry.substring(slash)
            hostMatches(host, h) && p.startsWith(prefix, ignoreCase = true)
        }
    }
}
