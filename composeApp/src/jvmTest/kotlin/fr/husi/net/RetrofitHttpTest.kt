package fr.husi.net

import fr.husi.GroupType
import fr.husi.SubscriptionType
import fr.husi.bg.GitHubApi
import fr.husi.bg.RELEASE_LIST_PAGE_SIZE
import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import fr.husi.group.OOCResponse
import fr.husi.group.OpenOnlineConfigApi
import fr.husi.group.OpenOnlineConfigUpdater
import fr.husi.group.SIP008Api
import fr.husi.ktx.deleteRecursivelyBlocking
import fr.husi.test.HusiHttpKoinTest
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import retrofit2.create
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class RetrofitHttpTest {

    private val unusedRoots = PlatformFile(File("/nonexistent/husi-retrofit-test-roots.pem"))

    private fun createRetrofitWithStub(
        responder: (Request) -> Response,
    ): Pair<retrofit2.Retrofit, List<Request>> {
        val recorded = mutableListOf<Request>()
        val stubFactory = StubCallFactory { request ->
            recorded += request
            responder(request)
        }
        val appClient = AppHttpClient(
            rootsFile = unusedRoots,
            directCallFactory = { stubFactory },
        )
        return createAppRetrofit(appClient) to recorded
    }

    @Test
    fun `GitHubApi latestRelease and releases decode JSON and send headers`() = runTest {
        val (retrofit, recorded) = createRetrofitWithStub { request ->
            val body = when (request.url.encodedPath) {
                "/repos/xchacha20-poly1305/husi/releases/latest" -> {
                    """{"tag_name":"1.2.3","body":"notes","draft":false,"html_url":"https://example.invalid/1.2.3","assets":[]}"""
                }
                "/repos/xchacha20-poly1305/husi/releases" -> {
                    """[{"tag_name":"2.0.0-beta1","body":"beta","draft":false,"html_url":"https://example.invalid/2.0.0","assets":[]}]"""
                }
                else -> error("Unexpected path: ${request.url}")
            }
            jsonResponse(request, body = body)
        }
        val api = retrofit.create<GitHubApi>()

        val latest = api.latestRelease(
            owner = "xchacha20-poly1305",
            repo = "husi",
            userAgent = "husi-test/1.0",
            authorization = "Bearer tok",
        ).requireOkBody()

        assertEquals("1.2.3", latest.tagName)
        assertEquals("husi-test/1.0", recorded[0].header("User-Agent"))
        assertEquals("Bearer tok", recorded[0].header("Authorization"))

        val list = api.releases(
            owner = "xchacha20-poly1305",
            repo = "husi",
            userAgent = "husi-test/1.0",
        ).requireOkBody()

        assertEquals(listOf("2.0.0-beta1"), list.map { it.tagName })
        assertEquals(RELEASE_LIST_PAGE_SIZE.toString(), recorded[1].url.queryParameter("per_page"))
        assertNull(recorded[1].header("Authorization"))
    }

    @Test
    fun `SIP008Api and OpenOnlineConfigApi propagate HttpRequestOptions tag and decode JSON`() = runTest {
        val recorded = mutableListOf<Request>()
        val stubFactory = StubCallFactory { request ->
            recorded += request
            val body = when (request.url.encodedPath) {
                "/sip008.json" -> """{"bytes_used":100,"bytes_remaining":200,"servers":[{"server":"1.2.3.4","server_port":8388}]}"""
                "/secret/ooc/v1/uid" -> """{"protocols":["shadowsocks"],"username":"alice","shadowsocks":[{"name":"node1","address":"5.6.7.8","port":443}]}"""
                else -> error("Unexpected path: ${request.url}")
            }
            jsonResponse(request, body = body)
        }
        val retrofit = createAppRetrofit(stubFactory)

        val sip008Api = retrofit.create<SIP008Api>()
        val sip008 = sip008Api.fetch(
            url = "https://example.invalid/sip008.json".toHttpUrl(),
            userAgent = "ua-sip008",
            authorization = "Basic dXNlcjpwYXNz",
            options = HttpRequestOptions(restrictedTls = true),
        ).requireOkBody()

        assertEquals(100L, sip008.bytesUsed)
        assertEquals(1, sip008.servers.size)
        assertEquals(
            HttpRequestOptions(restrictedTls = true),
            recorded[0].tag(HttpRequestOptions::class.java),
        )

        val oocApi = retrofit.create<OpenOnlineConfigApi>()
        val ooc = oocApi.fetch(
            url = "https://example.invalid/secret/ooc/v1/uid".toHttpUrl(),
            userAgent = "ua-ooc",
            authorization = "Basic b29jOnBhc3M=",
            options = HttpRequestOptions(restrictedTls = true, pinnedSha256 = "abcd"),
        ).requireOkBody()

        assertEquals("alice", ooc.username)
        assertEquals("node1", ooc.shadowsocks.single().name)
        assertEquals("Basic b29jOnBhc3M=", recorded[1].header("Authorization"))
        assertEquals(
            HttpRequestOptions(restrictedTls = true, pinnedSha256 = "abcd"),
            recorded[1].tag(HttpRequestOptions::class.java),
        )
    }

    @Test
    fun `RetrofitHttpFetcher handles credentials, headers, streaming download, and statusError preview`() = runBlocking {
        val tempDir = PlatformFile(createTempDirectory("husi-retrofit-fetcher-").toString())
        try {
            val payload = ByteArray(32) { it.toByte() }
            val (retrofit, recorded) = createRetrofitWithStub { request ->
                when (request.url.encodedPath) {
                    "/sub" -> textResponse(
                        request = request,
                        code = HttpStatus.OK,
                        body = "ok-body",
                        headers = mapOf("Subscription-Userinfo" to "upload=1; download=2; total=10"),
                    )
                    "/archive.bin" -> Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_2)
                        .code(HttpStatus.OK)
                        .message("OK")
                        .body(payload.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                    "/fail-long" -> textResponse(
                        request = request,
                        code = HttpStatus.FORBIDDEN,
                        message = "Forbidden",
                        body = "x".repeat(STATUS_ERROR_PREVIEW_BYTES + 20),
                    )
                    else -> error("Unexpected path: ${request.url}")
                }
            }
            val fetcher = RetrofitHttpFetcher(retrofit.create())

            val textResponse = fetcher.fetchText(
                HttpFetchRequest(
                    url = "https://user:pass@example.invalid/sub",
                    userAgent = "ua-raw",
                    headers = mapOf("X-Custom" to "val"),
                ),
            )
            assertEquals("ok-body", textResponse.content)
            assertEquals("upload=1; download=2; total=10", textResponse.header("subscription-userinfo"))
            assertEquals("https://example.invalid/sub", recorded[0].url.toString())
            assertEquals(Credentials.basic("user", "pass", UTF_8), recorded[0].header("Authorization"))
            assertEquals("ua-raw", recorded[0].header("User-Agent"))
            assertEquals("val", recorded[0].header("X-Custom"))

            val target = PlatformFile(File(tempDir.toString(), "out.bin"))
            val progressEvents = mutableListOf<Pair<Long, Long>>()
            fetcher.download(
                request = HttpFetchRequest(
                    url = "https://example.invalid/archive.bin",
                    userAgent = "ua-dl",
                    noOverallDeadline = true,
                ),
                target = target,
            ) { copied, total ->
                progressEvents += copied to total
            }
            assertTrue(payload.contentEquals(target.readBytes()))
            assertEquals(32L to 32L, progressEvents.last())

            val error = assertFailsWith<IOException> {
                fetcher.fetchText(
                    HttpFetchRequest(
                        url = "https://example.invalid/fail-long",
                        userAgent = "ua-fail",
                    ),
                )
            }
            assertEquals("HTTP 403 Forbidden: ${"x".repeat(STATUS_ERROR_PREVIEW_BYTES)} ...", error.message)
        } finally {
            tempDir.deleteRecursivelyBlocking()
        }
    }

    @Test
    fun `RetrofitWebDAVClient issues PROPFIND, MKCOL, PUT, GET, DELETE with sameOriginRedirects`() = runBlocking {
        val seenMethods = mutableListOf<String>()
        val seenDepths = mutableListOf<String?>()
        val seenOptions = mutableListOf<HttpRequestOptions?>()
        var directoryExists = false
        var storedPayload = ByteArray(0)

        val (retrofit, _) = createRetrofitWithStub { request ->
            seenMethods += request.method
            seenDepths += request.header("Depth")
            seenOptions += request.tag(HttpRequestOptions::class.java)
            when (request.method) {
                HttpMethod.PROPFIND -> {
                    val depth = request.header("Depth")
                    if (depth == "0" && !directoryExists) {
                        textResponse(request, code = HttpStatus.NOT_FOUND, message = "Not Found", body = "")
                    } else {
                        val xml = """
                            <?xml version="1.0" encoding="utf-8"?>
                            <d:multistatus xmlns:d="DAV:">
                              <d:response>
                                <d:href>/dav/</d:href>
                                <d:propstat>
                                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                                  <d:status>HTTP/1.1 200 OK</d:status>
                                </d:propstat>
                              </d:response>
                              <d:response>
                                <d:href>/dav/husi_backup_1.json</d:href>
                                <d:propstat>
                                  <d:prop><d:resourcetype/><d:getcontentlength>4</d:getcontentlength></d:prop>
                                  <d:status>HTTP/1.1 200 OK</d:status>
                                </d:propstat>
                              </d:response>
                            </d:multistatus>
                        """.trimIndent()
                        textResponse(request, code = HttpStatus.MULTI_STATUS, message = "Multi-Status", body = xml)
                    }
                }
                HttpMethod.MKCOL -> {
                    directoryExists = true
                    textResponse(request, code = HttpStatus.CREATED, message = "Created", body = "")
                }
                HttpMethod.PUT -> {
                    val buffer = Buffer()
                    request.body?.writeTo(buffer)
                    storedPayload = buffer.readByteArray()
                    textResponse(request, code = HttpStatus.CREATED, message = "Created", body = "")
                }
                HttpMethod.GET -> {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_2)
                        .code(HttpStatus.OK)
                        .message("OK")
                        .body(storedPayload.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                }
                HttpMethod.DELETE -> {
                    textResponse(request, code = HttpStatus.NO_CONTENT, message = "No Content", body = "")
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }
        val client = RetrofitWebDAVClient(retrofit.create())
        val server = WebDAVServer(
            url = "https://dav.example/dav",
            username = "u",
            password = "p",
        )

        val payload = byteArrayOf(1, 2, 3, 4)
        client.upload(server, "husi_backup_1.json", payload)
        val entries = client.list(server)
        val downloaded = client.download(server, "husi_backup_1.json")
        client.delete(server, "husi_backup_1.json")

        assertEquals(
            listOf(HttpMethod.PROPFIND, HttpMethod.MKCOL, HttpMethod.PUT, HttpMethod.PROPFIND, HttpMethod.GET, HttpMethod.DELETE),
            seenMethods,
        )
        assertContains(seenDepths, "0")
        assertContains(seenDepths, "1")
        assertTrue(seenOptions.all { it?.sameOriginRedirects == true })
        assertEquals("husi_backup_1.json", entries.single().name)
        assertEquals(4L, entries.single().size)
        assertTrue(payload.contentEquals(downloaded))
    }

    private fun jsonResponse(request: Request, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(HttpStatus.OK)
            .message("OK")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun textResponse(
        request: Request,
        code: Int,
        message: String = "OK",
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message(message)
            .body(body.toResponseBody("text/plain; charset=utf-8".toMediaType()))
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
            executed = true
            try {
                responseCallback.onResponse(this, responder(request))
            } catch (e: IOException) {
                responseCallback.onFailure(this, e)
            }
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

    @Test
    fun `RetrofitHttpFetcher cancellation interrupts stalled body read after headers`() = runBlocking {
        val headersSent = CompletableDeferred<Unit>()
        withStallingHttpServer(onHeadersSent = { headersSent.complete(Unit) }) { baseUrl ->
            val retrofit = createAppRetrofit(AppHttpClient(rootsFile = unusedRoots))
            val fetcher = RetrofitHttpFetcher(retrofit.create())
            var caught: Throwable? = null
            val job = launch {
                try {
                    fetcher.fetchText(
                        HttpFetchRequest(
                            url = "$baseUrl/stalled",
                            userAgent = "ua-cancel",
                        ),
                    )
                } catch (t: Throwable) {
                    caught = t
                    throw t
                }
            }

            headersSent.await()
            delay(50)
            val elapsed = measureTime {
                withTimeout(2.seconds) {
                    job.cancelAndJoin()
                }
            }

            assertTrue(caught is CancellationException, "Expected CancellationException, got $caught")
            assertTrue(elapsed < 2.seconds, "Cancellation took $elapsed")
        }
    }

    @Test
    fun `withHttpDeadline interrupts stalled RetrofitHttpFetcher body read after headers`() = runBlocking {
        val headersSent = CompletableDeferred<Unit>()
        withStallingHttpServer(onHeadersSent = { headersSent.complete(Unit) }) { baseUrl ->
            val retrofit = createAppRetrofit(AppHttpClient(rootsFile = unusedRoots))
            val fetcher = RetrofitHttpFetcher(retrofit.create())

            val elapsed = measureTime {
                assertFailsWith<SocketTimeoutException> {
                    withHttpDeadline(200.milliseconds) {
                        fetcher.fetchText(
                            HttpFetchRequest(
                                url = "$baseUrl/stalled",
                                userAgent = "ua-deadline",
                                noOverallDeadline = true,
                            ),
                        )
                    }
                }
            }

            assertTrue(headersSent.isCompleted)
            assertTrue(elapsed < 2.seconds, "Deadline interruption took $elapsed")
        }
    }

    @Test
    fun `RetrofitWebDAVClient download cancellation interrupts stalled body read after headers`() = runBlocking {
        val headersSent = CompletableDeferred<Unit>()
        withStallingHttpServer(onHeadersSent = { headersSent.complete(Unit) }) { baseUrl ->
            val retrofit = createAppRetrofit(AppHttpClient(rootsFile = unusedRoots))
            val client = RetrofitWebDAVClient(retrofit.create())
            val server = WebDAVServer(url = "$baseUrl/dav", username = "", password = "")
            var caught: Throwable? = null
            val job = launch {
                try {
                    client.download(server, "backup.json")
                } catch (t: Throwable) {
                    caught = t
                    throw t
                }
            }

            headersSent.await()
            delay(50)
            val elapsed = measureTime {
                withTimeout(2.seconds) {
                    job.cancelAndJoin()
                }
            }

            assertTrue(caught is CancellationException, "Expected CancellationException, got $caught")
            assertTrue(elapsed < 2.seconds, "Cancellation took $elapsed")
        }
    }

    @Test
    fun `non-2xx response stops reading error body after preview limit`() = runBlocking {
        var bytesPulled = 0L
        var closed = false
        val totalSize = 2L * 1024L * 1024L
        val countingSource = object : Source {
            private var remaining = totalSize

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining <= 0L) return -1L
                val toRead = minOf(byteCount, remaining)
                sink.writeUtf8("a".repeat(toRead.toInt()))
                remaining -= toRead
                bytesPulled += toRead
                return toRead
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                closed = true
            }
        }
        val (retrofit, _) = createRetrofitWithStub { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(HttpStatus.NOT_FOUND)
                .message("Not Found")
                .body(
                    countingSource.buffer().asResponseBody(
                        "text/plain; charset=utf-8".toMediaType(),
                        totalSize,
                    ),
                )
                .build()
        }
        val fetcher = RetrofitHttpFetcher(retrofit.create())

        val error = assertFailsWith<IOException> {
            fetcher.fetchText(
                HttpFetchRequest(
                    url = "https://example.invalid/missing",
                    userAgent = "ua-err",
                ),
            )
        }

        assertEquals("HTTP 404 Not Found: ${"a".repeat(STATUS_ERROR_PREVIEW_BYTES)} ...", error.message)
        assertTrue(closed)
        assertTrue(bytesPulled <= 8192L, "Expected bounded read, but pulled $bytesPulled bytes")
    }

    @Test
    fun `RetrofitWebDAVClient upload and delete succeed when 2xx response bodies are truncated`() = runBlocking {
        val recordedMethods = Collections.synchronizedList(mutableListOf<String>())
        withTruncatedResponseServer(
            onRequest = { method -> recordedMethods += method },
            responder = { method ->
                when (method) {
                    HttpMethod.PROPFIND -> HttpStatus.MULTI_STATUS to "Multi-Status"
                    HttpMethod.PUT -> HttpStatus.CREATED to "Created"
                    HttpMethod.DELETE -> HttpStatus.OK to "OK"
                    else -> error("Unexpected method: $method")
                }
            },
        ) { baseUrl ->
            val retrofit = createAppRetrofit(AppHttpClient(rootsFile = unusedRoots))
            val client = RetrofitWebDAVClient(retrofit.create())
            val server = WebDAVServer(url = "$baseUrl/dav", username = "", password = "")

            client.upload(server, "backup.json", byteArrayOf(1, 2, 3))
            client.delete(server, "backup.json")
        }

        assertEquals(listOf(HttpMethod.PROPFIND, HttpMethod.PUT, HttpMethod.DELETE), recordedMethods)
    }

    @Test
    fun `RetrofitWebDAVClient upload succeeds when PROPFIND 404 and MKCOL 405 bodies are truncated`() = runBlocking {
        val recordedMethods = Collections.synchronizedList(mutableListOf<String>())
        withTruncatedResponseServer(
            onRequest = { method -> recordedMethods += method },
            responder = { method ->
                when (method) {
                    HttpMethod.PROPFIND -> HttpStatus.NOT_FOUND to "Not Found"
                    HttpMethod.MKCOL -> HttpStatus.METHOD_NOT_ALLOWED to "Method Not Allowed"
                    HttpMethod.PUT -> HttpStatus.CREATED to "Created"
                    else -> error("Unexpected method: $method")
                }
            },
        ) { baseUrl ->
            val retrofit = createAppRetrofit(AppHttpClient(rootsFile = unusedRoots))
            val client = RetrofitWebDAVClient(retrofit.create())
            val server = WebDAVServer(url = "$baseUrl/dav", username = "", password = "")

            client.upload(server, "backup.json", byteArrayOf(1, 2, 3))
        }

        assertEquals(listOf(HttpMethod.PROPFIND, HttpMethod.MKCOL, HttpMethod.PUT), recordedMethods)
    }

    @Test
    fun `RetrofitWebDAVClient upload does not wait when PROPFIND 404 and MKCOL 405 bodies stall`() = runBlocking {
        val recordedMethods = Collections.synchronizedList(mutableListOf<String>())
        val elapsed = measureTime {
            withTruncatedResponseServer(
                holdOpen = true,
                onRequest = { method -> recordedMethods += method },
                responder = { method ->
                    when (method) {
                        HttpMethod.PROPFIND -> HttpStatus.NOT_FOUND to "Not Found"
                        HttpMethod.MKCOL -> HttpStatus.METHOD_NOT_ALLOWED to "Method Not Allowed"
                        HttpMethod.PUT -> HttpStatus.CREATED to "Created"
                        else -> error("Unexpected method: $method")
                    }
                },
            ) { baseUrl ->
                val retrofit = createAppRetrofit(AppHttpClient(rootsFile = unusedRoots))
                val client = RetrofitWebDAVClient(retrofit.create())
                val server = WebDAVServer(url = "$baseUrl/dav", username = "", password = "")

                client.upload(server, "backup.json", byteArrayOf(1, 2, 3))
            }
        }

        assertEquals(listOf(HttpMethod.PROPFIND, HttpMethod.MKCOL, HttpMethod.PUT), recordedMethods)
        assertTrue(elapsed < 2.seconds, "Upload waited $elapsed on stalled 404/405 bodies")
    }

    private suspend fun withTruncatedResponseServer(
        holdOpen: Boolean = false,
        onRequest: (method: String) -> Unit,
        responder: (method: String) -> Pair<Int, String>,
        block: suspend (baseUrl: String) -> Unit,
    ) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = Collections.synchronizedList(mutableListOf<Socket>())
        val acceptThread = thread(isDaemon = true) {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    accepted += socket
                    thread(isDaemon = true) {
                        socket.use { clientSocket ->
                            try {
                                val input = clientSocket.getInputStream()
                                val headerBuffer = Buffer()
                                while (true) {
                                    val next = input.read()
                                    if (next == -1) return@thread
                                    headerBuffer.writeByte(next)
                                    val size = headerBuffer.size
                                    if (size >= 4 &&
                                        headerBuffer[size - 4] == '\r'.code.toByte() &&
                                        headerBuffer[size - 3] == '\n'.code.toByte() &&
                                        headerBuffer[size - 2] == '\r'.code.toByte() &&
                                        headerBuffer[size - 1] == '\n'.code.toByte()
                                    ) {
                                        break
                                    }
                                }
                                val headerLines = headerBuffer.readUtf8().split("\r\n")
                                val method = headerLines.first().substringBefore(' ')
                                val contentLength = headerLines
                                    .drop(1)
                                    .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                                    ?.substringAfter(':')
                                    ?.trim()
                                    ?.toIntOrNull()
                                    ?: 0
                                var remaining = contentLength
                                val scratch = ByteArray(1024)
                                while (remaining > 0) {
                                    val read = input.read(scratch, 0, minOf(remaining, scratch.size))
                                    if (read == -1) break
                                    remaining -= read
                                }

                                onRequest(method)
                                val (code, reason) = responder(method)
                                val rawResponse = buildString {
                                    append("HTTP/1.1 $code $reason\r\n")
                                    append("Content-Length: 50\r\n")
                                    append("Connection: close\r\n")
                                    append("\r\n")
                                }
                                clientSocket.getOutputStream().write(rawResponse.toByteArray(UTF_8))
                                clientSocket.getOutputStream().flush()
                                if (holdOpen) {
                                    while (!clientSocket.isClosed && input.read() != -1) {
                                        // Hold socket open without sending the response body.
                                    }
                                }
                            } catch (_: IOException) {
                                // Ignore socket errors on teardown.
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                // Server socket closed on teardown.
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}")
        } finally {
            runCatching { server.close() }
            synchronized(accepted) {
                accepted.forEach { runCatching { it.close() } }
            }
            acceptThread.join(1_000)
        }
    }

    private suspend fun withStallingHttpServer(
        onHeadersSent: () -> Unit,
        block: suspend (baseUrl: String) -> Unit,
    ) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = Collections.synchronizedList(mutableListOf<Socket>())
        val acceptThread = thread(isDaemon = true) {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    accepted += socket
                    thread(isDaemon = true) {
                        try {
                            val reader = socket.getInputStream().bufferedReader()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            val header = buildString {
                                append("HTTP/1.1 200 OK\r\n")
                                append("Content-Length: 1000000\r\n")
                                append("Content-Type: text/plain; charset=utf-8\r\n")
                                append("\r\n")
                                append("hello")
                            }
                            socket.getOutputStream().write(header.toByteArray(UTF_8))
                            socket.getOutputStream().flush()
                            onHeadersSent()
                            while (!socket.isClosed && socket.getInputStream().read() != -1) {
                                // Hold connection open until client cancels or server closes.
                            }
                        } catch (_: IOException) {
                            // Closed by client cancel or test teardown.
                        }
                    }
                }
            } catch (_: IOException) {
                // Server socket closed on teardown.
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}")
        } finally {
            runCatching { server.close() }
            synchronized(accepted) {
                accepted.forEach { runCatching { it.close() } }
            }
            acceptThread.join(1_000)
        }
    }
}

class OpenOnlineConfigUpdaterAuthTest : HusiHttpKoinTest() {

    @Test
    fun `OpenOnlineConfigUpdater extracts URL credentials into Basic Authorization header`() = runBlocking {
        val tokenJson = """
            {
              "version": 1,
              "baseUrl": "https://user:pass@example.invalid",
              "secret": "sec",
              "userId": "uid"
            }
        """.trimIndent()
        val group = ProxyGroup(id = 1L, name = "OOC", type = GroupType.SUBSCRIPTION)
        val subscription = SubscriptionBean().apply {
            type = SubscriptionType.OOCv1
            token = tokenJson
        }
        fakeOOC.nextResponse = OOCResponse(
            protocols = listOf("shadowsocks"),
            username = "user",
        )

        OpenOnlineConfigUpdater.doUpdate(
            proxyGroup = group,
            subscription = subscription,
            byUser = true,
            warnings = mutableListOf(),
        )

        val call = fakeOOC.calls.single()
        assertEquals("https://example.invalid/sec/ooc/v1/uid", call.url.toString())
        assertEquals(Credentials.basic("user", "pass", UTF_8), call.authorization)
    }
}
