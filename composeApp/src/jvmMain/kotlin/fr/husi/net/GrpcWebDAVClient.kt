package fr.husi.net

import fr.husi.core.CoreClient
import fr.husi.proto.v1.HTTPFetchSocks5
import fr.husi.proto.v1.WebDAVDeleteRequest
import fr.husi.proto.v1.WebDAVDownloadRequest
import fr.husi.proto.v1.WebDAVListRequest
import fr.husi.proto.v1.WebDAVUploadHead
import fr.husi.proto.v1.WebDAVUploadRequest
import kotlinx.coroutines.flow.flow
import okio.Buffer
import okio.ByteString.Companion.toByteString
import fr.husi.proto.v1.WebDAVServer as ProtoWebDAVServer

class GrpcWebDAVClient(private val coreClient: CoreClient) : WebDAVClient {

    override suspend fun list(server: WebDAVServer): List<WebDAVEntry> {
        val response = coreClient.webdavList(WebDAVListRequest(server = protoServer(server)))
        return response.entries.map { entry ->
            WebDAVEntry(
                name = entry.name,
                size = entry.size,
                modified = entry.modified_unix_ms,
            )
        }
    }

    override suspend fun upload(server: WebDAVServer, name: String, content: ByteArray) {
        val requests = flow {
            emit(
                WebDAVUploadRequest(
                    head = WebDAVUploadHead(
                        server = protoServer(server),
                        name = name,
                        size = content.size.toLong(),
                    ),
                ),
            )
            for (offset in content.indices step UPLOAD_CHUNK_SIZE) {
                val length = minOf(UPLOAD_CHUNK_SIZE, content.size - offset)
                emit(WebDAVUploadRequest(chunk = content.toByteString(offset, length)))
            }
        }
        coreClient.webdavUpload(requests)
    }

    override suspend fun download(server: WebDAVServer, name: String): ByteArray {
        val request = WebDAVDownloadRequest(server = protoServer(server), name = name)
        val content = Buffer()
        coreClient.webdavDownload(request).collect { response ->
            content.write(response.chunk)
        }
        return content.readByteArray()
    }

    override suspend fun delete(server: WebDAVServer, name: String) {
        coreClient.webdavDelete(
            WebDAVDeleteRequest(server = protoServer(server), name = name),
        )
    }

    private fun protoServer(server: WebDAVServer) =
        ProtoWebDAVServer(
            url = server.url,
            username = server.username,
            password = server.password,
            socks5 = server.socks5?.let { proxy ->
                HTTPFetchSocks5(
                    port = proxy.port,
                    username = proxy.username,
                    password = proxy.password,
                )
            },
        )

    private companion object {
        /** Keeps each message far below gRPC's 4 MiB limit. */
        const val UPLOAD_CHUNK_SIZE = 16 * 1024
    }
}
