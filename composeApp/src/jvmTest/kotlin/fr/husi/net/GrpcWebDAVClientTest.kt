package fr.husi.net

import com.google.protobuf.ByteString
import fr.husi.proto.v1.WebDAVUploadRequest
import fr.husi.proto.v1.webDAVDownloadResponse
import fr.husi.test.FakeCoreClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrpcWebDAVClientTest {

    private val coreClient = FakeCoreClient()
    private val client = GrpcWebDAVClient(coreClient)
    private val server = WebDAVServer(
        url = "https://dav.example.invalid/husi",
        username = "user",
        password = "secret",
        socks5 = Socks5Proxy(port = 1080, username = "proxy", password = "proxy-secret"),
    )

    @Test
    fun `upload sends the head, then the content in chunks`() = runTest {
        val content = ByteArray(40 * 1024) { index -> index.toByte() }

        client.upload(server, "backup.json", content)

        val messages = coreClient.lastWebDAVUpload
        val head = messages.first().head
        assertEquals(server.url, head.server.url)
        assertEquals(server.username, head.server.username)
        assertEquals(server.password, head.server.password)
        assertEquals(1080, head.server.socks5.port)
        assertEquals("backup.json", head.name)
        assertEquals(content.size.toLong(), head.size)

        val chunks = messages.drop(1)
        assertTrue(chunks.size > 1, "content should span several chunks")
        assertTrue(chunks.all { it.payloadCase == WebDAVUploadRequest.PayloadCase.CHUNK })
        val uploaded = chunks.fold(ByteString.EMPTY) { joined, message -> joined.concat(message.chunk) }
        assertContentEquals(content, uploaded.toByteArray())
    }

    @Test
    fun `upload of empty content sends the head only`() = runTest {
        client.upload(server, "backup.json", ByteArray(0))

        assertEquals(1, coreClient.lastWebDAVUpload.size)
    }

    @Test
    fun `download joins the chunks`() = runTest {
        coreClient.webdavDownloadResponses = listOf("{\"ver", "sion\":3}").map { part ->
            webDAVDownloadResponse { chunk = ByteString.copyFromUtf8(part) }
        }

        val content = client.download(server, "backup.json")

        assertEquals("{\"version\":3}", content.decodeToString())
        assertEquals("backup.json", coreClient.lastWebDAVDownload?.name)
        assertEquals(server.url, coreClient.lastWebDAVDownload?.server?.url)
    }

    @Test
    fun `delete names the file on the server`() = runTest {
        client.delete(server, "backup.json")

        val request = coreClient.lastWebDAVDelete
        assertEquals("backup.json", request?.name)
        assertEquals(server.url, request?.server?.url)
        assertEquals(server.password, request?.server?.password)
    }
}
