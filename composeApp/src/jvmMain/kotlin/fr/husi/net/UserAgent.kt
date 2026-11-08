package fr.husi.net

import fr.husi.BuildConfig
import fr.husi.core.CoreVersionCache
import fr.husi.core.resolveCoreVersionCache

private const val VERSION_ESCAPE = $$"$version"
private const val BOX_VERSION_ESCAPE = $$"$box_version"
private const val DEFAULT_USER_AGENT = "husi/$VERSION_ESCAPE (sing-box $BOX_VERSION_ESCAPE)"

suspend fun buildUserAgent(
    customUserAgent: String = "",
    coreVersionCache: CoreVersionCache = resolveCoreVersionCache(),
): String {
    val userAgent = customUserAgent.ifBlank { DEFAULT_USER_AGENT }
        .replace(VERSION_ESCAPE, BuildConfig.VERSION_NAME)
    if (BOX_VERSION_ESCAPE !in userAgent) {
        return userAgent
    }
    val boxVersion = coreVersionCache.get()?.singBoxVersion ?: "unknown"
    return userAgent.replace(BOX_VERSION_ESCAPE, boxVersion)
}
