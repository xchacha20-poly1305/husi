package fr.husi.test

import fr.husi.net.WebDAVClient
import fr.husi.net.WebDAVEntry
import fr.husi.net.WebDAVServer

/**
 * In-memory replacement for [WebDAVClient] used by tests: one directory whose
 * files are the [files] map. [listing] overrides what [list] answers, to model
 * sizes and modification times a real server reports.
 */
class FakeWebDAVClient : WebDAVClient {

    val files = mutableMapOf<String, ByteArray>()

    /** When non-null, [list] answers this instead of describing [files]. */
    var listing: List<WebDAVEntry>? = null

    /** When non-null, the next call throws this instead of answering. */
    var nextThrowable: Throwable? = null

    /** The server of every call, in order. */
    val servers = mutableListOf<WebDAVServer>()

    override suspend fun list(server: WebDAVServer): List<WebDAVEntry> {
        record(server)
        return listing ?: files.map { (name, content) ->
            WebDAVEntry(name = name, size = content.size.toLong(), modified = 0L)
        }
    }

    override suspend fun upload(server: WebDAVServer, name: String, content: ByteArray) {
        record(server)
        files[name] = content
    }

    override suspend fun download(server: WebDAVServer, name: String): ByteArray {
        record(server)
        return files.getValue(name)
    }

    override suspend fun delete(server: WebDAVServer, name: String) {
        record(server)
        files.remove(name) ?: error("$name does not exist")
    }

    private fun record(server: WebDAVServer) {
        servers += server
        nextThrowable?.let {
            nextThrowable = null
            throw it
        }
    }
}
