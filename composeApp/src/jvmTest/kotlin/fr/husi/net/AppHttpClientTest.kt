package fr.husi.net

import fr.husi.CertProvider
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppHttpClientTest {

    private val unusedRoots = PlatformFile(File("/nonexistent/husi-test-roots.pem"))

    @Test
    fun `canUseHttpEngineForCert respects system cert override`() {
        assertTrue(canUseHttpEngineForCert(CertProvider.SYSTEM, allowSystemCert = false))
        assertFalse(canUseHttpEngineForCert(CertProvider.MOZILLA, allowSystemCert = false))
        assertFalse(canUseHttpEngineForCert(CertProvider.CHROME, allowSystemCert = false))
        assertFalse(canUseHttpEngineForCert(CertProvider.SYSTEM_AND_USER, allowSystemCert = false))

        assertTrue(canUseHttpEngineForCert(CertProvider.SYSTEM, allowSystemCert = true))
        assertTrue(canUseHttpEngineForCert(CertProvider.MOZILLA, allowSystemCert = true))
        assertTrue(canUseHttpEngineForCert(CertProvider.CHROME, allowSystemCert = true))
        assertTrue(canUseHttpEngineForCert(CertProvider.SYSTEM_AND_USER, allowSystemCert = true))
    }

    @Test
    fun `direct unpinned unrestricted request uses directCallFactory`() = runTest {
        val seenRequests = mutableListOf<Request>()
        val factory = StubCallFactory { request ->
            seenRequests += request
            responseFor(request, code = HttpStatus.OK, body = "from-http-engine")
        }
        val client = AppHttpClient(
            rootsFile = unusedRoots,
            directCallFactory = { factory },
        )

        val body = client.exchange(
            request = Request.Builder().url("https://example.com/sub").build(),
            proxy = null,
        ) { response, _ ->
            response.body.string()
        }

        assertEquals("from-http-engine", body)
        assertEquals(1, seenRequests.size)
        assertEquals("https://example.com/sub", seenRequests.single().url.toString())
    }

    @Test
    fun `proxy request bypasses directCallFactory`() = runTest {
        var directCalled = false
        val client = AppHttpClient(
            rootsFile = unusedRoots,
            directCallFactory = {
                directCalled = true
                StubCallFactory { error("directCallFactory must not be called") }
            },
        )
        val closedPort = ServerSocket(0).use { it.localPort }

        assertFailsWith<IOException> {
            client.exchange(
                request = Request.Builder().url("http://example.com/sub").build(),
                proxy = LocalHttpProxy(port = closedPort, username = "", password = ""),
            ) { response, _ ->
                response.body.string()
            }
        }
        assertFalse(directCalled)
    }

    @Test
    fun `restrictedTls request bypasses directCallFactory`() = runTest {
        var directCalled = false
        val client = AppHttpClient(
            rootsFile = unusedRoots,
            directCallFactory = {
                directCalled = true
                StubCallFactory { error("directCallFactory must not be called") }
            },
        )

        assertFailsWith<IOException> {
            client.exchange(
                request = Request.Builder().url("https://example.com/sub").build(),
                proxy = null,
                restrictedTls = true,
            ) { response, _ ->
                response.body.string()
            }
        }
        assertFalse(directCalled)
    }

    @Test
    fun `pinnedSha256 request bypasses directCallFactory`() = runTest {
        var directCalled = false
        val client = AppHttpClient(
            rootsFile = unusedRoots,
            directCallFactory = {
                directCalled = true
                StubCallFactory { error("directCallFactory must not be called") }
            },
        )

        assertFailsWith<IOException> {
            client.exchange(
                request = Request.Builder().url("https://example.com/sub").build(),
                proxy = null,
                pinnedSha256 = "0123456789abcdef",
            ) { response, _ ->
                response.body.string()
            }
        }
        assertFalse(directCalled)
    }

    @Test
    fun `cross-origin redirect from directCallFactory strips sensitive headers`() = runTest {
        val seenRequests = mutableListOf<Request>()
        val factory = StubCallFactory { request ->
            seenRequests += request
            if (request.url.host == "origin.example") {
                responseFor(
                    request = request,
                    code = HttpStatus.FOUND,
                    body = "",
                    headers = mapOf("Location" to "https://cdn.example/final"),
                )
            } else {
                responseFor(request = request, code = HttpStatus.OK, body = "ok")
            }
        }
        val client = AppHttpClient(
            rootsFile = unusedRoots,
            directCallFactory = { factory },
        )

        val result = client.exchange(
            request = Request.Builder()
                .url("https://origin.example/start")
                .header("Authorization", "Bearer secret")
                .header("Cookie", "sid=123")
                .build(),
            proxy = null,
        ) { response, _ ->
            response.body.string()
        }

        assertEquals("ok", result)
        assertEquals(2, seenRequests.size)
        assertEquals("Bearer secret", seenRequests[0].header("Authorization"))
        assertEquals("sid=123", seenRequests[0].header("Cookie"))
        assertNull(seenRequests[1].header("Authorization"))
        assertNull(seenRequests[1].header("Cookie"))
    }

    @Test
    fun `sameOriginRedirects rejects cross-origin redirect from directCallFactory`() = runTest {
        val factory = StubCallFactory { request ->
            responseFor(
                request = request,
                code = HttpStatus.FOUND,
                body = "",
                headers = mapOf("Location" to "https://other.example/dav/"),
            )
        }
        val client = AppHttpClient(
            rootsFile = unusedRoots,
            directCallFactory = { factory },
        )

        assertFailsWith<IOException> {
            client.exchange(
                request = Request.Builder().url("https://dav.example/").build(),
                proxy = null,
                sameOriginRedirects = true,
            ) { response, _ ->
                response.body.string()
            }
        }
    }

    private fun responseFor(
        request: Request,
        code: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
            .body(body.toResponseBody("text/plain".toMediaType()))
        for ((name, value) in headers) {
            builder.header(name, value)
        }
        return builder.build()
    }

    private class StubCallFactory(
        private val responder: (Request) -> Response,
    ) : Call.Factory {
        override fun newCall(request: Request): Call = StubCall(request, responder)
    }

    private class StubCall(
        private val request: Request,
        private val responder: (Request) -> Response,
    ) : Call {
        private var executed = false
        private var canceled = false

        override fun request(): Request = request

        override fun execute(): Response {
            executed = true
            return responder(request)
        }

        override fun enqueue(responseCallback: Callback) {
            throw UnsupportedOperationException()
        }

        override fun cancel() {
            canceled = true
        }

        override fun isExecuted(): Boolean = executed

        override fun isCanceled(): Boolean = canceled

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = StubCall(request, responder)

        override fun <T : Any> tag(type: KClass<T>): T? = null

        override fun <T> tag(type: Class<out T>): T? = null

        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T {
            throw UnsupportedOperationException()
        }

        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
            throw UnsupportedOperationException()
        }

        override fun addEventListener(eventListener: EventListener) {
            // No-op.
        }
    }
}
