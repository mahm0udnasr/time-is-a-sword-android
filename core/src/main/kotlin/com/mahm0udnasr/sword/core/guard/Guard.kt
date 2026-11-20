package com.mahm0udnasr.sword.core.guard

import com.mahm0udnasr.sword.core.model.Protection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Settings guard (plan §10.2, step 8.2): which Settings pages of *this app*
 * are protected. Data-driven (`assets/guard.json`) because OEMs word their
 * Settings differently and updates change them.
 *
 * A page is protected when the window belongs to a watched package, some text
 * on it contains one of the app's labels, and some text or view id is one of
 * the page's markers.
 */
@Serializable
data class GuardRules(
    /** Settings apps (and the package installer's uninstall dialog). */
    val packages: List<String> = emptyList(),
    val pages: List<GuardPage> = emptyList(),
) {
    /** Adds markers known only at run time (e.g. this app's own accessibility description). */
    fun withContains(page: String, markers: List<String>): GuardRules =
        copy(pages = pages.map { if (it.name == page) it.copy(contains = it.contains + markers) else it })

    /** Replaces "{label}" in every marker with each of the app's labels. */
    fun withLabels(labels: List<String>): GuardRules {
        fun expand(m: String) = if ("{label}" in m) labels.map { m.replace("{label}", it) } else listOf(m)
        return copy(pages = pages.map { p -> p.copy(equals = p.equals.flatMap(::expand), contains = p.contains.flatMap(::expand)) })
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): GuardRules = json.decodeFromString(serializer(), text)
    }
}

/**
 * One protected page.
 * @property equals a node's whole text equals one of these (ignoring case and spaces).
 *   Exact, because "Activate" is inside "Deactivate".
 * @property contains a node's text contains one of these (long, specific texts)
 * @property ids a node's view id is one of these
 */
@Serializable
data class GuardPage(
    val name: String,
    val equals: List<String> = emptyList(),
    val contains: List<String> = emptyList(),
    val ids: List<String> = emptyList(),
)

/** What a window node shows. */
data class NodeText(val text: String? = null, val desc: String? = null, val id: String? = null)

/** @return the protected page shown in this window ("accessibility", "appInfo", "admin", …), or null */
fun guardPage(pkg: String, nodes: List<NodeText>, labels: List<String>, rules: GuardRules): String? {
    if (pkg !in rules.packages) return null
    val texts = nodes.flatMap { listOfNotNull(it.text, it.desc) }.map(::normText).filter { it.isNotEmpty() }
    val wanted = labels.map(::normText).filter { it.isNotEmpty() }
    if (wanted.none { l -> texts.any { it.contains(l) } }) return null
    val ids = nodes.mapNotNull { it.id }.toSet()
    return rules.pages.firstOrNull { p ->
        val eq = p.equals.map(::normText)
        val co = p.contains.map(::normText).filter { it.isNotEmpty() }
        texts.any { t -> t in eq || co.any { t.contains(it) } } || p.ids.any { it in ids }
    }?.name
}

/** Is the guard on right now? Off while switched off, or during the 5-minute "turn off protection" window. */
fun guardActive(p: Protection, suspendedUntil: Long?, now: Long): Boolean =
    p.guardEnabled && (suspendedUntil == null || now >= suspendedUntil)

/** Lower case, bidi marks removed, every kind of space collapsed to one. */
internal fun normText(s: String): String =
    s.replace(BIDI, "").replace(SPACES, " ").trim().lowercase()

private val BIDI = Regex("[‎‏‪-‮⁦-⁩؜]")
private val SPACES = Regex("[ \t\n\r   ]+")
