@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.remote

import com.squareup.wire.GrpcException
import com.squareup.wire.GrpcStatus
import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.SubscribeStatusRequest
import fr.husi.proto.daemon.TailscaleSSHClientMessage
import fr.husi.proto.daemon.TailscaleSSHInput
import fr.husi.proto.daemon.TailscaleSSHReady
import fr.husi.proto.daemon.TailscaleSSHServerMessage
import fr.husi.proto.daemon.Version
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BrowserClientTest {
    @Test
    fun `session uses bearer metadata websocket subscriptions and bounded unary deadlines`() = runTest {
        val server = RecordingServer()
        val client = BrowserClient(server::open)
        assertEquals("test-version", client.connect("https://example.com/api/", "secret").version)
        val probe = server.calls.single()
        assertEquals("https://example.com/api", probe.url)
        assertEquals("Bearer secret", probe.metadata["authorization"])
        assertEquals("/daemon.StartedService/GetVersion", probe.method)
        assertEquals(10.seconds, probe.timeout)
        assertNotNull(probe.request)

        client.service.GetVersion().execute()
        assertEquals(30.seconds, server.calls.last().timeout)
        client.service.GetVersion().apply {
            (timeout as BrowserCallTimeout).duration = 2.seconds
        }.execute()
        assertEquals(2.seconds, server.calls.last().timeout)

        val status = client.service.SubscribeStatus().responses(SubscribeStatusRequest(500000000)).first()
        assertEquals(9007199254740993L, status.uplinkTotal)
        val call = server.calls.last()
        assertEquals(500000000L, SubscribeStatusRequest.ADAPTER.decode(call.sent.single()).interval)
        assertNull(call.request)
        assertNull(call.timeout)
        assertTrue(call.closed)
        client.close()
    }

    @Test
    fun `connection probe closes its own exchange and preserves the active session`() = runTest {
        val active = RecordingServer()
        val probe = RecordingServer()
        val client = BrowserClient { url, secret ->
            if (url == "http://active.example") {
                active.open(url, secret)
            } else {
                probe.open(url, secret)
            }
        }
        client.connect("http://active.example", "")
        assertEquals("test-version", client.testConnection("http://probe.example", ""))
        assertTrue(probe.calls.single().closed)
        client.service.GetVersion().execute()
        assertEquals(2, active.calls.size)
        client.close()
    }

    @Test
    fun `replacing a session closes live and not yet collected streams`() = runTest {
        val old = RecordingServer()
        val replacement = RecordingServer()
        val client = BrowserClient { url, secret ->
            if (url == "http://old.example") {
                old.open(url, secret)
            } else {
                replacement.open(url, secret)
            }
        }
        client.connect("http://old.example", "")
        val oldService = client.service
        val delayedStream = oldService.SubscribeStatus().responses(SubscribeStatusRequest())
        val live = async {
            assertFailsWith<GrpcException> {
                oldService.SubscribeStatus()
                    .responses(SubscribeStatusRequest())
                    .collect {}
            }
        }
        runCurrent()
        assertEquals(2, old.calls.size)
        client.connect("http://new.example", "")
        assertEquals(GrpcStatus.UNAVAILABLE, live.await().grpcStatus)
        assertTrue(old.calls.last().closed)
        val delayedError = assertFailsWith<GrpcException> { delayedStream.first() }
        assertEquals(GrpcStatus.UNAVAILABLE, delayedError.grpcStatus)
        assertEquals("test-version", client.service.GetVersion().execute().version)
        client.close()
    }

    @Test
    fun `late failure from a replaced connection cannot close its replacement`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val old = RecordingServer().also { it.versionGate = gate }
        val replacement = RecordingServer()
        val client = BrowserClient { url, secret ->
            if (url == "http://old.example") {
                old.open(url, secret)
            } else {
                replacement.open(url, secret)
            }
        }
        val connecting = async {
            runCatching {
                client.connect("http://old.example", "")
            }
        }
        runCurrent()
        client.connect("http://new.example", "")
        gate.completeExceptionally(IllegalStateException("old connection failed"))
        assertTrue(connecting.await().isFailure)
        assertEquals("test-version", client.service.GetVersion().execute().version)
        client.close()
    }

    @Test
    fun `SSH carries binary oneof messages through the generated bidirectional client`() = runTest {
        val server = RecordingServer()
        val client = BrowserClient(server::open)
        client.connect("http://example.com", "")
        val bytes = byteArrayOf(0, 1, -1, 27)
        val ready = client.service.StartTailscaleSSHSession().responses(
            flowOf(
                TailscaleSSHClientMessage(input = TailscaleSSHInput(bytes.toByteString())),
            ),
        ).first()
        assertNotNull(ready.ready)
        val call = server.calls.last()
        assertEquals("/daemon.StartedService/StartTailscaleSSHSession", call.method)
        val sent = TailscaleSSHClientMessage.ADAPTER.decode(call.sent.single())
        assertContentEquals(bytes, sent.input!!.data_.toByteArray())
        client.close()
    }

    private class RecordingServer {
        val calls = mutableListOf<RecordingExchange>()
        var versionGate: CompletableDeferred<Unit>? = null

        fun open(url: String, secret: String) =
            BrowserGrpcClient(url, secret) { _, path, metadata, request, timeout ->
                RecordingExchange(url, path, metadata, request, timeout, versionGate).also {
                    calls.add(it)
                }
            }
    }

    private class RecordingExchange(
        val url: String,
        val method: String,
        val metadata: Map<String, String>,
        val request: ByteArray?,
        val timeout: Duration?,
        private val versionGate: CompletableDeferred<Unit>?,
    ) : BrowserRpcExchange {
        override val status = 0
        override val statusMessage = ""
        override val responseMetadata = null
        val sent = mutableListOf<ByteArray>()
        var closed = false
            private set
        private var received = false
        private val closure = CompletableDeferred<Unit>()
        private val requestSent = CompletableDeferred<Unit>()

        override suspend fun send(bytes: ByteArray) {
            sent.add(bytes)
            requestSent.complete(Unit)
        }

        override suspend fun finish() = Unit

        override suspend fun receive(): ByteArray? {
            if (received) {
                if (method.endsWith("/GetVersion")) {
                    return null
                }
                closure.await()
                throw IOException("RPC closed")
            }
            received = true
            return when (method.substringAfterLast('/')) {
                "GetVersion" -> {
                    versionGate?.await()
                    Version("test-version").encode()
                }

                "SubscribeStatus" -> {
                    requestSent.await()
                    Status(uplinkTotal = 9007199254740993L).encode()
                }

                "StartTailscaleSSHSession" -> {
                    requestSent.await()
                    TailscaleSSHServerMessage(ready = TailscaleSSHReady()).encode()
                }

                else -> error("Unexpected RPC $method")
            }
        }

        override fun close() {
            closed = true
            closure.complete(Unit)
        }
    }
}
