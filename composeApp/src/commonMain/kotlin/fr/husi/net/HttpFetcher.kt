package fr.husi.net

import fr.husi.database.DataStore
import org.koin.core.context.GlobalContext
import java.io.File

data class Socks5Proxy(
    val port: Int,
    val username: String,
    val password: String,
)

data class HttpFetchRequest(
    val url: String,
    val userAgent: String,
    val headers: Map<String, String> = emptyMap(),
    /**
     * Leaves the exchange without an overall deadline, for large downloads (app update
     * packages, rule set archives) that can take minutes on a slow connection.
     * Connection setup still times out, and a stalled transfer still fails.
     */
    val noOverallDeadline: Boolean = false,
    /** Forces TLS 1.3. */
    val restrictedTls: Boolean = false,
    /** Hex SHA-256 of a leaf certificate accepted even when self-signed (OOCv1). */
    val pinnedSha256: String? = null,
    val socks5: Socks5Proxy? = null,
    /** age identities that decrypt an armored response body. */
    val ageIdentities: String? = null,
)

class HttpTextResponse(
    val content: String,
    private val headerLookup: (name: String) -> String?,
) {
    /** First value of response header [name], matched case-insensitively. */
    fun header(name: String): String? = headerLookup(name)
}

typealias DownloadProgress = (copiedBytes: Long, contentLength: Long) -> Unit

interface HttpFetcher {
    suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse

    suspend fun download(
        request: HttpFetchRequest,
        target: File,
        onProgress: DownloadProgress = { _, _ -> },
    )
}

fun resolveHttpFetcher(): HttpFetcher = GlobalContext.get().get()

suspend fun localSocks5Proxy(): Socks5Proxy? {
    if (!DataStore.serviceState.connected) return null
    return Socks5Proxy(
        port = DataStore.mixedPort.get(),
        username = DataStore.inboundUsername.get(),
        password = DataStore.inboundPassword.get(),
    )
}
