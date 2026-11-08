package fr.husi.net

import com.google.protobuf.ByteString
import fr.husi.core.CoreClient
import fr.husi.proto.v1.hTTPFetchSocks5
import fr.husi.proto.v1.webDAVDeleteRequest
import fr.husi.proto.v1.webDAVDownloadRequest
import fr.husi.proto.v1.webDAVListRequest
import fr.husi.proto.v1.webDAVServer
import fr.husi.proto.v1.webDAVUploadHead
import fr.husi.proto.v1.webDAVUploadRequest
import kotlinx.coroutines.flow.flow

class GrpcWebDAVClient(private val coreClient: CoreClient) : WebDAVClient {

    override suspend fun list(server: WebDAVServer): List<WebDAVEntry> {
        val response = coreClient.webdavList(webDAVListRequest { this.server = protoServer(server) })
        return response.entriesList.map { entry ->
            WebDAVEntry(
                name = entry.name,
                size = entry.size,
                modified = entry.modifiedUnixMs,
            )
        }
    }

    override suspend fun upload(server: WebDAVServer, name: String, content: ByteArray) {
        val requests = flow {
            emit(
                webDAVUploadRequest {
                    head = webDAVUploadHead {
                        this.server = protoServer(server)
                        this.name = name
                        size = content.size.toLong()
                    }
                },
            )
            for (offset in content.indices step UPLOAD_CHUNK_SIZE) {
                val length = minOf(UPLOAD_CHUNK_SIZE, content.size - offset)
                emit(webDAVUploadRequest { chunk = ByteString.copyFrom(content, offset, length) })
            }
        }
        coreClient.webdavUpload(requests)
    }

    override suspend fun download(server: WebDAVServer, name: String): ByteArray {
        val request = webDAVDownloadRequest {
            this.server = protoServer(server)
            this.name = name
        }
        var content = ByteString.EMPTY
        coreClient.webdavDownload(request).collect { response ->
            content = content.concat(response.chunk)
        }
        return content.toByteArray()
    }

    override suspend fun delete(server: WebDAVServer, name: String) {
        coreClient.webdavDelete(
            webDAVDeleteRequest {
                this.server = protoServer(server)
                this.name = name
            },
        )
    }

    private fun protoServer(server: WebDAVServer) = webDAVServer {
        url = server.url
        username = server.username
        password = server.password
        server.socks5?.let { proxy ->
            socks5 = hTTPFetchSocks5 {
                port = proxy.port
                username = proxy.username
                password = proxy.password
            }
        }
    }

    private companion object {
        /** Keeps each message far below gRPC's 4 MiB limit. */
        const val UPLOAD_CHUNK_SIZE = 16 * 1024
    }
}
