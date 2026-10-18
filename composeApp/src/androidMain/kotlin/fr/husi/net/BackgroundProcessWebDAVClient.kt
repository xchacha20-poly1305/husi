package fr.husi.net

import fr.husi.bg.withBackgroundProcess

class BackgroundProcessWebDAVClient(private val delegate: WebDAVClient) : WebDAVClient {

    override suspend fun list(server: WebDAVServer): List<WebDAVEntry> =
        withBackgroundProcess { delegate.list(server) }

    override suspend fun upload(server: WebDAVServer, name: String, content: ByteArray) =
        withBackgroundProcess { delegate.upload(server, name, content) }

    override suspend fun download(server: WebDAVServer, name: String): ByteArray =
        withBackgroundProcess { delegate.download(server, name) }

    override suspend fun delete(server: WebDAVServer, name: String) =
        withBackgroundProcess { delegate.delete(server, name) }
}
