package fr.husi.net

import fr.husi.proto.v1.HTTPFetchHead
import fr.husi.proto.v1.HTTPFetchResponse
import fr.husi.test.FakeCoreClient
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GrpcHttpFetcherTest {

    private val coreClient = FakeCoreClient()
    private val fetcher = GrpcHttpFetcher(coreClient)
    private val tempDir: File = createTempDirectory("husi-grpc-http-test").toFile()

    @AfterTest
    fun removeTempDir() {
        tempDir.deleteRecursively()
    }

    private fun head(contentLength: Long, headers: Map<String, String> = emptyMap()) =
        HTTPFetchResponse(head = HTTPFetchHead(headers = headers, content_length = contentLength))

    private fun chunk(bytes: ByteArray): HTTPFetchResponse =
        HTTPFetchResponse(chunk = bytes.toByteString())

    @Test
    fun `request carries every option to the host`() = runTest {
        coreClient.httpFetchResponses = listOf(head(0))

        fetcher.fetchText(
            HttpFetchRequest(
                url = "https://example.invalid/sub",
                userAgent = "husi-test",
                headers = mapOf("Authorization" to "Bearer token"),
                noOverallDeadline = true,
                restrictedTls = true,
                pinnedSha256 = "ab",
                socks5 = Socks5Proxy(port = 2080, username = "user", password = "pass"),
                ageIdentities = "AGE-SECRET-KEY-1",
            ),
        )

        val sent = checkNotNull(coreClient.lastHttpFetch)
        assertEquals("https://example.invalid/sub", sent.url)
        assertEquals("husi-test", sent.headers["User-Agent"])
        assertEquals("Bearer token", sent.headers["Authorization"])
        assertEquals(true, sent.no_overall_deadline)
        assertEquals(true, sent.restricted_tls)
        assertEquals("ab", sent.pinned_sha256)
        assertEquals(2080, checkNotNull(sent.socks5).port)
        assertEquals("user", checkNotNull(sent.socks5).username)
        assertEquals("pass", checkNotNull(sent.socks5).password)
        assertEquals("AGE-SECRET-KEY-1", sent.age_identities)
    }

    @Test
    fun `request without a proxy leaves socks5 unset`() = runTest {
        coreClient.httpFetchResponses = listOf(head(0))

        fetcher.fetchText(
            HttpFetchRequest(url = "https://example.invalid", userAgent = "husi-test"),
        )

        assertEquals(false, (coreClient.lastHttpFetch?.socks5 != null))
    }

    @Test
    fun `fetchText joins chunks and looks headers up case-insensitively`() = runTest {
        coreClient.httpFetchResponses = listOf(
            head(-1, mapOf("Subscription-Userinfo" to "upload=1")),
            chunk("hu".encodeToByteArray()),
            chunk("si".encodeToByteArray()),
        )

        val response = fetcher.fetchText(
            HttpFetchRequest(url = "https://example.invalid", userAgent = "husi-test"),
        )

        assertEquals("husi", response.content)
        assertEquals("upload=1", response.header("subscription-userinfo"))
        assertNull(response.header("Content-Type"))
    }

    @Test
    fun `download writes chunks to the target and reports progress`() = runTest {
        val first = ByteArray(3) { 1 }
        val second = ByteArray(5) { 2 }
        coreClient.httpFetchResponses = listOf(head(8), chunk(first), chunk(second))
        val target = tempDir.resolve("rules.tar.gz")
        val progress = mutableListOf<Pair<Long, Long>>()

        fetcher.download(
            HttpFetchRequest(url = "https://example.invalid", userAgent = "husi-test"),
            target,
        ) { copiedBytes, contentLength -> progress += copiedBytes to contentLength }

        assertContentEquals(first + second, target.readBytes())
        assertEquals(listOf(3L to 8L, 8L to 8L), progress)
    }

    @Test
    fun `a stream that fails surfaces the error`() = runTest {
        coreClient.httpFetchResponses = listOf(head(8))
        coreClient.httpFetchThrowable = IOException("HTTP 404 Not Found")

        assertFailsWith<IOException> {
            fetcher.fetchText(
                HttpFetchRequest(url = "https://example.invalid", userAgent = "husi-test"),
            )
        }
    }

    @Test
    fun `a stream without a head fails`() = runTest {
        coreClient.httpFetchResponses = emptyList()

        assertFailsWith<IllegalStateException> {
            fetcher.fetchText(
                HttpFetchRequest(url = "https://example.invalid", userAgent = "husi-test"),
            )
        }
    }
}
