package fr.husi.net

import fr.husi.proto.v1.WebDAVDownloadResponse
import fr.husi.test.FakeCoreClient
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
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
        val head = checkNotNull(messages.first().head)
        val protoServer = checkNotNull(head.server)
        assertEquals(server.url, protoServer.url)
        assertEquals(server.username, protoServer.username)
        assertEquals(server.password, protoServer.password)
        assertEquals(1080, checkNotNull(protoServer.socks5).port)
        assertEquals("backup.json", head.name)
        assertEquals(content.size.toLong(), head.size)

        val chunks = messages.drop(1)
        assertTrue(chunks.size > 1, "content should span several chunks")
        assertTrue(chunks.all { it.chunk != null })
        val uploaded =
            Buffer()
                .also { buffer -> chunks.forEach { buffer.write(checkNotNull(it.chunk)) } }
                .readByteArray()
        assertContentEquals(content, uploaded)
    }

    @Test
    fun `upload of empty content sends the head only`() = runTest {
        client.upload(server, "backup.json", ByteArray(0))

        assertEquals(1, coreClient.lastWebDAVUpload.size)
    }

    @Test
    fun `download joins the chunks`() = runTest {
        coreClient.webdavDownloadResponses = listOf("{\"ver", "sion\":3}").map { part ->
            WebDAVDownloadResponse(chunk = part.encodeUtf8())
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
