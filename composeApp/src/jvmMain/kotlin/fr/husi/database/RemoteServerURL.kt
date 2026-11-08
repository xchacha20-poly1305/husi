package fr.husi.database

import fr.husi.ktx.blankAsNull
import io.github.xchacha20_poly1305.kpuri.Url
import io.github.xchacha20_poly1305.kpuri.buildUrl

/**
 * Normalizes a user-entered remote API address into the URL the core dials.
 *
 * Accepts `host`, `host:port` and explicit `http(s)://` addresses; a missing scheme means
 * `http`, and a missing port is left to the core (80 for `http`, 443 for `https`).
 * The stored form is a complete URL, so no caller has to patch a scheme back in.
 *
 * Returns `null` when the address is not a plain `http(s)` endpoint: another scheme, credentials,
 * a path, a query or a fragment all mean the user typed something we would silently ignore.
 */
fun normalizeRemoteServerURL(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val absolute = if (trimmed.contains("://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    val url = runCatching { Url.parse(absolute) }.getOrNull() ?: return null

    val scheme = url.scheme ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (!url.username.isNullOrEmpty() || !url.password.isNullOrEmpty()) return null
    if (url.path.isNotEmpty() && url.path != "/") return null
    if (!url.fragment.isNullOrEmpty() || url.encodedQuery != null) return null
    val host = url.host.blankAsNull() ?: return null

    return buildUrl(scheme) {
        this.host = host
        port = url.port
    }.toString()
}
