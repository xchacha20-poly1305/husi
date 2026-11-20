package fr.husi.core

import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcClientStreamingCall
import com.squareup.wire.GrpcException
import com.squareup.wire.GrpcMethod
import com.squareup.wire.GrpcServerStreamingCall
import com.squareup.wire.GrpcStatus
import com.squareup.wire.GrpcStreamingCall
import com.squareup.wire.MessageSource
import fr.husi.proto.daemon.SubscribeConnectionsRequest
import fr.husi.proto.daemon.SubscribeStatusRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Timeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WireCoreClientTest {

    @Test
    fun `probe passes only for a SERVING health status`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        client.probe()
        val channel = factory.awaitFirst()
        assertEquals("/grpc.health.v1.Health/Check", channel.unaryCalls.single().method)

        channel.healthStatus = ServingStatus.NOT_SERVING
        val error = assertFailsWith<CoreRpcException> { client.probe() }
        assertEquals(GrpcStatus.UNAVAILABLE, error.code)
        client.close()
    }

    @Test
    fun `call failures become CoreRpcException with the gRPC code`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)

        factory.next.unaryFailure = GrpcException(GrpcStatus.NOT_FOUND, "no such group")
        val status = assertFailsWith<CoreRpcException> { client.selectOutbound("g", "o") }
        assertEquals(GrpcStatus.NOT_FOUND, status.code)
        assertEquals("no such group", status.message)

        factory.awaitFirst().unaryFailure = IOException("connection refused")
        val transport = assertFailsWith<CoreRpcException> { client.clearLogs() }
        assertEquals(GrpcStatus.UNAVAILABLE, transport.code)
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

        first.stream(0).fail(IOException("connection lost"))
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
        assertEquals(
            1.seconds.inWholeNanoseconds,
            SubscribeStatusRequest.ADAPTER.decode(statusCall.request).interval,
        )
        assertEquals(
            1.seconds.inWholeNanoseconds,
            SubscribeConnectionsRequest.ADAPTER.decode(connectionsCall.request).interval,
        )
        client.close()
    }

    @Test
    fun `one-shot stream fails with the mapped error and does not retry`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)
        val failure = CompletableDeferred<Throwable>()

        backgroundScope.launch(Dispatchers.Default) {
            failure.complete(
                runCatching {
                    client.stunTest("stun.example", "").collect()
                }.exceptionOrNull()!!,
            )
        }
        val first = factory.awaitFirst()
        awaitCondition("one-shot stream opened") { first.streamCount() == 1 }
        first.stream(0).fail(GrpcException(GrpcStatus.UNAVAILABLE, "gone"))

        val error = failure.await()
        assertEquals(GrpcStatus.UNAVAILABLE, (error as CoreRpcException).code)
        assertEquals(1, first.streamCount())
        client.close()
    }

    @Test
    fun `an empty message is an all-default message`() = runTest {
        val factory = RecordingChannelFactory()
        val client = newClient(factory)
        val groups = CompletableDeferred<Int>()

        backgroundScope.launch(Dispatchers.Default) {
            client.subscribeGroups().collect {
                groups.complete(it.group.size)
            }
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

    private fun newClient(factory: RecordingChannelFactory) = WireCoreClient(
        openChannel = { factory() },
        retryDelay = 1.milliseconds,
        maxRetryDelay = 10.milliseconds,
        stableReset = 1.seconds,
    )
}

private suspend fun awaitCondition(description: String, condition: () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeoutOrNull(2.seconds) {
            while (!condition()) {
                delay(5.milliseconds)
            }
        } ?: error("timed out waiting for $description")
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
        return CoreChannel(channel, channel::close)
    }

    suspend fun awaitFirst(): FakeCoreChannel = first.await()
}

private class UnaryCall(val method: String)

private class FakeStream(val method: String, val request: ByteArray) {
    val messages = Channel<ByteArray>(Channel.UNLIMITED)

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

private class FakeCoreChannel : GrpcClient() {
    @Volatile
    var closed = false
        private set

    @Volatile
    var healthStatus = ServingStatus.SERVING

    @Volatile
    var unaryFailure: Throwable? = null

    val unaryCalls = mutableListOf<UnaryCall>()
    private val streams = mutableListOf<FakeStream>()

    fun streams(): List<FakeStream> = synchronized(streams) { streams.toList() }

    fun streamCount(): Int = synchronized(streams) { streams.size }

    fun stream(index: Int): FakeStream = synchronized(streams) { streams[index] }

    override fun <S : Any, R : Any> newCall(method: GrpcMethod<S, R>): GrpcCall<S, R> = GrpcCall {
        synchronized(unaryCalls) {
            unaryCalls += UnaryCall(method.path)
        }
        unaryFailure?.let { throw it }
        val response = if (method.path == "/grpc.health.v1.Health/Check") {
            HealthCheckResponse(healthStatus).encode()
        } else ByteArray(0)
        method.responseAdapter.decode(response)
    }

    override fun <S : Any, R : Any> newServerStreamingCall(
        method: GrpcMethod<S, R>,
    ): GrpcServerStreamingCall<S, R> =
        object : GrpcServerStreamingCall<S, R> {
            override val method = method
            override val timeout = Timeout()
            override var requestMetadata = emptyMap<String, String>()
            override val responseMetadata = null
            private var stream: FakeStream? = null

            override suspend fun executeIn(scope: CoroutineScope, request: S): ReceiveChannel<R> {
                val opened = FakeStream(method.path, method.requestAdapter.encode(request))
                stream = opened
                synchronized(streams) { streams += opened }
                val responses = Channel<R>(1)
                scope.launch {
                    try {
                        for (bytes in opened.messages) responses.send(method.responseAdapter.decode(bytes))
                        responses.close()
                    } catch (e: Exception) {
                        responses.close(e)
                    }
                }
                return responses
            }

            override fun cancel() {
                stream?.messages?.cancel()
            }

            override fun isCanceled(): Boolean = false
            override fun isExecuted(): Boolean = stream != null
            override fun clone() = newServerStreamingCall(method)
            override fun executeBlocking(request: S): MessageSource<R> = error("Unexpected blocking call")
        }

    override fun <S : Any, R : Any> newStreamingCall(method: GrpcMethod<S, R>): GrpcStreamingCall<S, R> =
        error("Unexpected bidirectional call ${method.path}")

    override fun <S : Any, R : Any> newClientStreamingCall(
        method: GrpcMethod<S, R>,
    ): GrpcClientStreamingCall<S, R> =
        error("Unexpected client streaming call ${method.path}")

    fun close() {
        closed = true
        streams().forEach {
            it.fail(IOException("Core channel closed"))
        }
    }
}
