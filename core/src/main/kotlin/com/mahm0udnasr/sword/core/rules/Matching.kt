package com.mahm0udnasr.sword.core.rules

import com.mahm0udnasr.sword.core.model.Target
import java.net.IDN

/*
 * Which target does an app or a website belong to? (port of hostOf/hostMatches/
 * findSite in rules.js and normalizeDomain in storage.js)
 *
 * JS uses the WHATWG URL parser; here a small parser covers what matters:
 * scheme, user info, port, IPv6 brackets, trailing dots, IDN → punycode.
 */

private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
private val LABEL = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")

/** The host of an absolute URL ("scheme://…"), lower-cased and in punycode, or null. */
private fun parseHost(url: String): Pair<String, String>? {
    val s = url.trim()
    val scheme = SCHEME.find(s)?.groupValues?.get(1)?.lowercase() ?: return null
    var rest = s.substring(scheme.length + 1)
    if (!rest.startsWith("//")) return null
    rest = rest.substring(2)
    var authority = rest.takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
    authority = authority.substringAfterLast('@')
    val host = if (authority.startsWith("[")) {
        authority.substringBefore(']') + "]" // IPv6 literal
    } else {
        authority.substringBefore(':')
    }
    if (host.isEmpty() || host.any { it.isWhitespace() || it in "<>^|%\"" }) return null
    val ascii = try {
        if (host.startsWith("[")) host else IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return scheme to ascii.lowercase()
}

/** Strip trailing dots and one leading "www.". */
private fun tidy(host: String) = host.trimEnd('.').removePrefix("www.")

/** The hostname of an http(s) URL without a leading "www.", or null. */
fun hostOf(url: String): String? {
    val (scheme, host) = parseHost(url) ?: return null
    if (scheme != "http" && scheme != "https") return null
    return tidy(host).ifEmpty { null }
}

/**
 * Turn the text of a browser's URL bar into a host. URL bars usually show no
 * scheme ("m.youtube.com/watch?v=1"), so "https://" is added first.
 */
fun hostOfUrlBar(text: String): String? {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    return hostOf(if (SCHEME.containsMatchIn(t) && t.contains("://")) t else "https://$t")
}

/** True if host is exactly the domain or a subdomain of it. x.com ≠ box.com. */
fun hostMatches(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

/**
 * Normalize what a user typed into a bare domain, or null if invalid.
 *   "https://www.YouTube.com/watch?v=1" → "youtube.com"
 *   "m.facebook.com."                   → "m.facebook.com"
 * Only a leading "www." is stripped; other subdomains (web., m.) are kept.
 */
fun normalizeDomain(input: String?): String? {
    if (input == null) return null
    var s = input.trim().lowercase()
    if (s.isEmpty()) return null
    if (!Regex("^[a-z][a-z0-9+.-]*://").containsMatchIn(s)) s = "http://$s"
    val host = tidy(parseHost(s)?.second ?: return null)
    val parts = host.split('.')
    if (parts.size < 2 || !parts.all { LABEL.matches(it) || it.startsWith("xn--") }) return null
    return host
}

/** A website match: the target and which of its domains matched. */
data class HostMatch(val target: Target, val domain: String)

/**
 * The target a host belongs to, over all domains of all targets. When several
 * match (whatsapp.com and web.whatsapp.com) the longest domain wins.
 * Disabled targets are included; callers decide what "disabled" means.
 */
fun findTargetByHost(host: String?, targets: List<Target>): HostMatch? {
    if (host.isNullOrEmpty()) return null
    var best: HostMatch? = null
    for (t in targets) {
        for (d in t.domains) {
            if (hostMatches(host, d) && (best == null || d.length > best.domain.length)) best = HostMatch(t, d)
        }
    }
    return best
}

/** Port of findSite(url, sites). */
fun findTargetByUrl(url: String, targets: List<Target>): HostMatch? = findTargetByHost(hostOf(url), targets)

/** The target that lists this package. Enabled targets win over disabled ones. */
fun findTargetByPackage(pkg: String?, targets: List<Target>): Target? {
    if (pkg.isNullOrEmpty()) return null
    val all = targets.filter { pkg in it.packages }
    return all.firstOrNull { it.enabled } ?: all.firstOrNull()
}
