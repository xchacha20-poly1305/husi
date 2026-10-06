package fr.husi.core

import fr.husi.proto.daemon.SubscribeConnectionsRequest
import fr.husi.proto.daemon.SubscribeStatusRequest
import io.github.xchacha20_poly1305.kurpc.CallOptions
import io.github.xchacha20_poly1305.kurpc.Metadata
import io.github.xchacha20_poly1305.kurpc.Status
import io.github.xchacha20_poly1305.kurpc.StatusException
import io.github.xchacha20_poly1305.kurpc.TransportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KurpcCoreClientTest {

    @Test
    fun `probe passes only for a SERVING health status`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        client.probe()
        val channel = factory.awaitFirst()
        assertEquals("/grpc.health.v1.Health/Check", channel.unaryCalls.single().method)
        assertFalse(channel.unaryCalls.single().options.waitForReady)

        channel.healthStatus = 2
        val error = assertFailsWith<CoreRpcException> { client.probe() }
        assertEquals(Status.Code.UNAVAILABLE, error.code)
        client.close()
    }

    @Test
    fun `call failures become CoreRpcException with the gRPC code`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        factory.next.unaryFailure = StatusException(Status.Code.NOT_FOUND, "no such group", ByteArray(0), Metadata.Empty)
        val status = assertFailsWith<CoreRpcException> { client.selectOutbound("g", "o") }
        assertEquals(Status.Code.NOT_FOUND, status.code)
        assertEquals("no such group", status.message)

        factory.awaitFirst().unaryFailure = TransportException("connection refused")
        val transport = assertFailsWith<CoreRpcException> { client.clearLogs() }
        assertEquals(Status.Code.UNAVAILABLE, transport.code)
        client.close()
    }

    @Test
    fun `concurrent subscriptions share one channel`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        backgroundScope.launch(Dispatchers.Default) { client.subscribeServiceStatus().collect() }
        backgroundScope.launch(Dispatchers.Default) { client.subscribeGroups().collect() }
        backgroundScope.launch(Dispatchers.Default) { client.subscribeLog().collect() }
        backgroundScope.launch(Dispatchers.Default) { client.subscribeClashMode().collect() }

        val first = factory.awaitFirst()
        awaitCondition("all four streams opened") { first.streamCount() == 4 }
        assertEquals(1, factory.created.size)
        client.close()
    }

    @Test
    fun `a failed subscription retries on the same channel`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        backgroundScope.launch(Dispatchers.Default) { client.subscribeServiceStatus().collect() }
        val first = factory.awaitFirst()
        awaitCondition("first stream opened") { first.streamCount() == 1 }

        // kurpc reconnects by itself; the channel is not thrown away.
        first.stream(0).fail(TransportException("connection lost"))
        awaitCondition("stream reopened") { first.streamCount() == 2 }
        assertEquals(1, factory.created.size)
        assertFalse(first.closed)
        client.close()
    }

    @Test
    fun `close makes subscriptions reopen on a new channel`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        backgroundScope.launch(Dispatchers.Default) { client.subscribeServiceStatus().collect() }
        val first = factory.awaitFirst()
        awaitCondition("first stream opened") { first.streamCount() == 1 }

        client.close()
        awaitCondition("close closed the channel") { first.closed }
        awaitCondition("retry opened a second channel") { factory.created.size == 2 }
        client.close()
    }

    @Test
    fun `subscribe status and connections send interval as nanoseconds`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        backgroundScope.launch(Dispatchers.Default) { client.subscribeStatus(1.seconds).collect() }
        backgroundScope.launch(Dispatchers.Default) { client.subscribeConnections(1.seconds).collect() }

        val first = factory.awaitFirst()
        awaitCondition("status and connections streams opened") { first.streamCount() == 2 }
        val calls = first.streams()
        val statusCall = calls.single { it.method == "/daemon.StartedService/SubscribeStatus" }
        val connectionsCall = calls.single { it.method == "/daemon.StartedService/SubscribeConnections" }
        assertEquals(1.seconds.inWholeNanoseconds, SubscribeStatusRequest.parseFrom(statusCall.request).interval)
        assertEquals(
            1.seconds.inWholeNanoseconds,
            SubscribeConnectionsRequest.parseFrom(connectionsCall.request).interval,
        )
        client.close()
    }

    @Test
    fun `one-shot stream fails with the mapped error and does not retry`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)
        val failure = CompletableDeferred<Throwable>()

        backgroundScope.launch(Dispatchers.Default) {
            failure.complete(runCatching { client.stunTest("stun.example", "").collect() }.exceptionOrNull()!!)
        }
        val first = factory.awaitFirst()
        awaitCondition("one-shot stream opened") { first.streamCount() == 1 }
        first.stream(0).fail(StatusException(Status.Code.UNAVAILABLE, "gone", ByteArray(0), Metadata.Empty))

        val error = failure.await()
        assertEquals(Status.Code.UNAVAILABLE, (error as CoreRpcException).code)
        assertEquals(1, first.streamCount())
        client.close()
    }

    @Test
    fun `an empty message is an all-default message`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)
        val groups = CompletableDeferred<Int>()

        backgroundScope.launch(Dispatchers.Default) {
            client.subscribeGroups().collect { groups.complete(it.groupCount) }
        }
        val first = factory.awaitFirst()
        awaitCondition("groups stream opened") { first.streamCount() == 1 }
        first.stream(0).emit(ByteArray(0))

        assertEquals(0, groups.await())
        client.close()
    }

    @Test
    fun `one-shot stream completes with the server`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)
        val results = CompletableDeferred<Int>()

        backgroundScope.launch(Dispatchers.Default) {
            results.complete(client.standaloneStunTest("stun.example").toList().size)
        }
        val first = factory.awaitFirst()
        awaitCondition("one-shot stream opened") { first.streamCount() == 1 }
        first.stream(0).emit(ByteArray(0))
        first.stream(0).finish()

        assertEquals(1, results.await())
        client.close()
    }

    private fun newClient(factory: RecordingChannelFactory) = KurpcCoreClient(
        openChannel = { factory() },
        retryDelay = 1.milliseconds,
        maxRetryDelay = 10.milliseconds,
        stableReset = 1.seconds,
    )
}

private suspend fun awaitCondition(description: String, condition: () -> Boolean) {
    withContext(Dispatchers.Default) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (!condition()) {
            if (System.nanoTime() > deadline) {
                error("timed out waiting for $description")
            }
            Thread.sleep(5)
        }
    }
}

private class RecordingChannelFactory {
    val created = mutableListOf<FakeCoreChannel>()
    private val first = CompletableDeferred<FakeCoreChannel>()

    /** The channel the next open returns, so a test can configure it before the first call. */
    var next = FakeCoreChannel()

    @Synchronized
    operator fun invoke(): CoreChannel {
        val channel = next
        next = FakeCoreChannel()
        created += channel
        first.complete(channel)
        return channel
    }

    suspend fun awaitFirst(): FakeCoreChannel = first.await()
}

private class UnaryCall(val method: String, val request: ByteArray, val options: CallOptions)

private class FakeStream(val method: String, val request: ByteArray) {
    private val messages = Channel<ByteArray>(Channel.UNLIMITED)
    val flow: Flow<ByteArray> = messages.receiveAsFlow()

    fun emit(message: ByteArray) {
        messages.trySend(message)
    }

    fun finish() {
        messages.close()
    }

    fun fail(error: Throwable) {
        messages.close(error)
    }
}

private class FakeCoreChannel : CoreChannel {
    @Volatile
    var closed: Boolean = false
        private set

    /** `grpc.health.v1.HealthCheckResponse.status`; 1 is SERVING. */
    @Volatile
    var healthStatus: Int = 1

    @Volatile
    var unaryFailure: Throwable? = null

    val unaryCalls = mutableListOf<UnaryCall>()
    private val streams = mutableListOf<FakeStream>()

    fun streams(): List<FakeStream> = synchronized(streams) { streams.toList() }

    fun streamCount(): Int = synchronized(streams) { streams.size }

    fun stream(index: Int): FakeStream = synchronized(streams) { streams[index] }

    override suspend fun unary(method: String, request: ByteArray, options: CallOptions): ByteArray {
        synchronized(unaryCalls) { unaryCalls += UnaryCall(method, request, options) }
        unaryFailure?.let { throw it }
        if (method == "/grpc.health.v1.Health/Check") {
            return byteArrayOf(0x08, healthStatus.toByte())
        }
        return ByteArray(0)
    }

    override fun serverStreaming(method: String, request: ByteArray): Flow<ByteArray> {
        val stream = FakeStream(method, request)
        synchronized(streams) { streams += stream }
        return stream.flow
    }

    override fun close() {
        closed = true
        streams().forEach { it.fail(io.github.xchacha20_poly1305.kurpc.ChannelClosedException()) }
    }
}
