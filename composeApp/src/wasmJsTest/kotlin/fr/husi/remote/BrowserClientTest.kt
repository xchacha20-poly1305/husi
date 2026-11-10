@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.remote

import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.SubscribeStatusRequest
import fr.husi.proto.daemon.TailscaleSSHClientMessage
import fr.husi.proto.daemon.TailscaleSSHInput
import fr.husi.proto.daemon.TailscaleSSHReady
import fr.husi.proto.daemon.TailscaleSSHServerMessage
import fr.husi.proto.daemon.Version
import io.github.xchacha20_poly1305.kurpc.CallOptions
import io.github.xchacha20_poly1305.kurpc.Channel
import io.github.xchacha20_poly1305.kurpc.ChannelClosedException
import io.github.xchacha20_poly1305.kurpc.Metadata
import io.github.xchacha20_poly1305.kurpc.UnaryResponse
import io.github.xchacha20_poly1305.kurpc.WebChannelConfig
import io.github.xchacha20_poly1305.kurpc.WebStreamTransport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString

class BrowserClientTest {
    @Test
    fun `session uses bearer metadata websocket subscriptions and bounded unary deadlines`() = runTest {
        val channel = RecordingChannel()
        lateinit var config: WebChannelConfig
        val client = BrowserClient { config = it; channel }
        assertEquals("test-version", client.connect("https://example.com/api/", "secret").version)
        assertEquals("https://example.com/api", config.baseUrl)
        assertEquals("Bearer secret", config.metadata.text("authorization"))
        assertEquals(WebStreamTransport.WebSocket, config.serverStreamingTransport)
        assertEquals("/daemon.StartedService/GetVersion", channel.calls.single().method)
        assertEquals(10.seconds, channel.calls.single().options.timeout)

        client.service.getVersion()
        assertEquals(30.seconds, channel.calls.last().options.timeout)
        client.service.getVersion(options = CallOptions(timeout = 2.seconds))
        assertEquals(2.seconds, channel.calls.last().options.timeout)

        val status = client.service.subscribeStatus(SubscribeStatusRequest(500000000)).first()
        assertEquals(9007199254740993L, status.uplinkTotal)
        val call = channel.calls.last()
        assertEquals(500000000L, SubscribeStatusRequest.ADAPTER.decode(call.request).interval)
        assertNull(call.options.timeout)
        client.close()
        assertTrue(channel.closed)
    }

    @Test
    fun `connection probe closes its own channel and preserves the active session`() = runTest {
        val active = RecordingChannel()
        val probe = RecordingChannel()
        val channels = ArrayDeque(listOf(active, probe))
        val client = BrowserClient { channels.removeFirst() }
        client.connect("http://active.example", "")
        assertEquals("test-version", client.testConnection("http://probe.example", ""))
        assertTrue(probe.closed)
        assertFalse(active.closed)
        client.service.getVersion()
        assertEquals(2, active.calls.size)
        client.close()
    }

    @Test
    fun `replacing a session closes live and not yet collected streams`() = runTest {
        val old = RecordingChannel()
        val replacement = RecordingChannel()
        val channels = ArrayDeque(listOf(old, replacement))
        val client = BrowserClient { channels.removeFirst() }
        client.connect("http://old.example", "")
        val oldService = client.service
        val delayedStream = oldService.subscribeStatus(SubscribeStatusRequest())
        val live = async { runCatching { oldService.subscribeStatus(SubscribeStatusRequest()).collect {} } }
        runCurrent()
        assertEquals(1, old.activeStreams)
        client.connect("http://new.example", "")
        runCurrent()
        assertIs<ChannelClosedException>(live.await().exceptionOrNull())
        assertEquals(0, old.activeStreams)
        assertFailsWith<ChannelClosedException> { delayedStream.first() }
        assertFalse(replacement.closed)
        client.close()
    }

    @Test
    fun `late failure from a replaced connection cannot close its replacement`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val old = RecordingChannel().also { it.versionGate = gate }
        val replacement = RecordingChannel()
        val channels = ArrayDeque(listOf(old, replacement))
        val client = BrowserClient { channels.removeFirst() }
        val connecting = async { runCatching { client.connect("http://old.example", "") } }
        runCurrent()
        client.connect("http://new.example", "")
        gate.completeExceptionally(IllegalStateException("old connection failed"))
        assertTrue(connecting.await().isFailure)
        assertEquals("test-version", client.service.getVersion().version)
        assertFalse(replacement.closed)
        client.close()
    }

    @Test
    fun `SSH carries binary oneof messages through the generated bidirectional client`() = runTest {
        val channel = RecordingChannel()
        val client = BrowserClient { channel }
        client.connect("http://example.com", "")
        val bytes = byteArrayOf(0, 1, -1, 27)
        val ready = client.service.startTailscaleSSHSession(flowOf(
            TailscaleSSHClientMessage(input = TailscaleSSHInput(bytes.toByteString())),
        )).first()
        assertNotNull(ready.ready)
        assertContentEquals(bytes, channel.sshInputs.single().input!!.data_.toByteArray())
        client.close()
    }

    @Test
    fun `API URL rejects credentials queries fragments and unsupported schemes`() {
        val client = BrowserClient { error("URL validation must not open a channel") }
        for (url in listOf("ftp://example.com", "https://user:pass@example.com", "https://example.com/?q=1", "https://example.com/#x")) {
            assertTrue(runCatching { client.normalizeUrl(url) }.isFailure, url)
        }
        assertEquals("https://example.com/prefix", client.normalizeUrl("https://example.com/prefix/"))
    }

    private data class Call(val method: String, val request: ByteArray, val options: CallOptions) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Call

            if (method != other.method) return false
            if (!request.contentEquals(other.request)) return false
            if (options != other.options) return false

            return true
        }

        override fun hashCode(): Int {
            var result = method.hashCode()
            result = 31 * result + request.contentHashCode()
            result = 31 * result + options.hashCode()
            return result
        }
    }

    private class RecordingChannel : Channel {
        val calls = mutableListOf<Call>()
        val sshInputs = mutableListOf<TailscaleSSHClientMessage>()
        var versionGate: CompletableDeferred<Unit>? = null
        var closed = false
        var activeStreams = 0
        private val closure = CompletableDeferred<Unit>()

        override fun close() {
            closed = true
            closure.complete(Unit)
        }

        private fun checkOpen() {
            if (closed) throw ChannelClosedException()
        }

        override suspend fun unary(method: String, request: ByteArray, options: CallOptions): ByteArray {
            checkOpen()
            calls.add(Call(method, request, options))
            versionGate?.await()
            checkOpen()
            return Version("test-version").encode()
        }

        override suspend fun unaryCall(method: String, request: ByteArray, options: CallOptions) =
            UnaryResponse(unary(method, request, options), Metadata.Empty, Metadata.Empty)

        override fun serverStreaming(method: String, request: ByteArray, options: CallOptions): Flow<ByteArray> = flow {
            checkOpen()
            calls.add(Call(method, request, options))
            activeStreams++
            try {
                emit(Status(uplinkTotal = 9007199254740993L).encode())
                closure.await()
                throw ChannelClosedException()
            } finally {
                activeStreams--
            }
        }

        override fun bidiStreaming(method: String, requests: Flow<ByteArray>, options: CallOptions): Flow<ByteArray> = flow {
            checkOpen()
            assertEquals("/daemon.StartedService/StartTailscaleSSHSession", method)
            requests.collect {
                sshInputs.add(TailscaleSSHClientMessage.ADAPTER.decode(it))
                emit(TailscaleSSHServerMessage(ready = TailscaleSSHReady()).encode())
            }
        }

        override suspend fun clientStreaming(method: String, requests: Flow<ByteArray>, options: CallOptions): ByteArray =
            error("Unexpected client stream")
    }
}
