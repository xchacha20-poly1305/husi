package fr.husi.libcore

import fr.husi.ktx.USER_AGENT

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
