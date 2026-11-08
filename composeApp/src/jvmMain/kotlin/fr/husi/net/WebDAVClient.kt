package fr.husi.net

import org.koin.core.context.GlobalContext

data class WebDAVServer(
    /** The directory that holds the files. */
    val url: String,
    val username: String,
    val password: String,
    val socks5: Socks5Proxy? = null,
)

data class WebDAVEntry(
    val name: String,
    val size: Long,
    /** Epoch milliseconds, or 0 when the server does not report it. */
    val modified: Long,
)

interface WebDAVClient {
    /** Files directly inside the directory; subdirectories are not listed. */
    suspend fun list(server: WebDAVServer): List<WebDAVEntry>

    /**
     * Stores [content] as [name] in the directory, replacing a file of the same name.
     * A missing directory is created, but not its parents.
     */
    suspend fun upload(server: WebDAVServer, name: String, content: ByteArray)

    suspend fun download(server: WebDAVServer, name: String): ByteArray

    suspend fun delete(server: WebDAVServer, name: String)
}

fun resolveWebDAVClient(): WebDAVClient = GlobalContext.get().get()
