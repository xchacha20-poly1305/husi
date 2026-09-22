package fr.husi.libcore

import fr.husi.ktx.USER_AGENT
import org.koin.core.context.GlobalContext

/**
 * Passed to [HTTPRequest.setTimeout] to leave a request with no deadline covering the
 * whole exchange, for large downloads (app update packages, rule set archives) that can
 * take minutes on a slow connection. Connection setup still times out, and a stalled
 * transfer still fails.
 */
internal const val NO_OVERALL_TIMEOUT_MS = 0

interface HttpClientFactory {
    fun newHttpClient(): HTTPClient
    fun parseURL(urlString: String): URL
    val userAgent: String
}

internal object LibcoreHttpClientFactory : HttpClientFactory {
    override fun newHttpClient(): HTTPClient = Libcore.newHttpClient()
    override fun parseURL(urlString: String): URL = Libcore.parseURL(urlString)
    override val userAgent: String get() = USER_AGENT
}

internal fun resolveHttpClientFactory(): HttpClientFactory {
    return GlobalContext.getOrNull()?.get() ?: LibcoreHttpClientFactory
}
