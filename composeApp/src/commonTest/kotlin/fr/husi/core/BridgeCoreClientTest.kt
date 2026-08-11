package fr.husi.core

import fr.husi.libcore.StreamHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeCoreClientTest {

    @Test
    fun `probe uses injected bridge factory`() = runTest {
        val factory = RecordingBridgeFactory()
        val client = newClient(factory)

        client.probe()

        assertEquals(1, factory.created.size)
        client.close()
    }

    @Test
    fun `concurrent subscribe streams share one bridge`() = runTest {
        val factory = RecordingBridgeFactory()
        val client = newClient(factory)

        backgroundScope.launch { client.subscribeServiceStatus().collect() }
        backgroundScope.launch { client.subscribeGroups().collect() }
        backgroundScope.launch { client.subscribeLog().collect() }
        backgroundScope.launch { client.subscribeClashMode().collect() }

        val first = factory.awaitFirst()
        awaitCondition("all four streams opened") { first.streamCalls.size == 4 }
        assertEquals(1, factory.created.size)
        client.close()
    }

    @Test
    fun `Unavailable stream close resets shared bridge for retry and unary`() = runTest {
        val factory = RecordingBridgeFactory()
        val client = newClient(factory)

        backgroundScope.launch { client.subscribeServiceStatus().collect() }
        val first = factory.awaitFirst()
        awaitCondition("first stream opened") { first.streamCalls.isNotEmpty() }

        first.completeAll("Unavailable: connection lost")
        awaitCondition("shared bridge reset") { first.closed }
        awaitCondition("retry created a second bridge") { factory.created.size == 2 }
        advanceUntilIdle()

        client.probe()
        assertEquals(2, factory.created.size)
        assertFalse(factory.created.last().closed)
        client.close()
    }

    @Test
    fun `non-connection stream error does not reset shared bridge`() = runTest {
        val factory = RecordingBridgeFactory()
        val client = newClient(factory)

        backgroundScope.launch { client.subscribeServiceStatus().collect() }
        val first = factory.awaitFirst()
        awaitCondition("first stream opened") { first.streamCalls.isNotEmpty() }

        first.completeAll("NotFound: x")
        awaitCondition("stream retried on the same bridge") { first.streamCalls.size >= 2 }
        advanceUntilIdle()

        assertFalse(first.closed)
        assertEquals(1, factory.created.size)
        client.probe()
        assertEquals(1, factory.created.size)
        client.close()
    }

    @Test
    fun `close causes stream retry to create a new bridge`() = runTest {
        val factory = RecordingBridgeFactory()
        val client = newClient(factory)

        backgroundScope.launch { client.subscribeServiceStatus().collect() }
        val first = factory.awaitFirst()
        awaitCondition("first stream opened") { first.streamCalls.isNotEmpty() }

        client.close()
        awaitCondition("close reset the shared bridge") { first.closed }
        awaitCondition("retry created a second bridge") { factory.created.size == 2 }
        client.close()
    }

    @Test
    fun `one-shot stream Unavailable resets shared bridge`() = runTest {
        val factory = RecordingBridgeFactory()
        val client = newClient(factory)

        backgroundScope.launch {
            client.stunTest("stun.example", "").catch { }.collect()
        }
        val first = factory.awaitFirst()
        awaitCondition("one-shot stream opened") { first.streamCalls.isNotEmpty() }

        first.completeAll("Unavailable: gone")
        awaitCondition("one-shot Unavailable reset the shared bridge") { first.closed }

        client.probe()
        assertEquals(2, factory.created.size)
        client.close()
    }

    private fun newClient(factory: RecordingBridgeFactory) = BridgeCoreClient(
        createBridge = { factory(it) },
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

private class RecordingBridgeFactory {
    val created = mutableListOf<FakeCoreBridge>()
    private val first = CompletableDeferred<FakeCoreBridge>()

    @Synchronized
    operator fun invoke(basePath: String?): CoreBridge {
        val bridge = FakeCoreBridge()
        created += bridge
        first.complete(bridge)
        return bridge
    }

    suspend fun awaitFirst(): FakeCoreBridge = first.await()
}

private class FakeCoreBridge : CoreBridge {
    @Volatile
    var closed: Boolean = false
        private set
    val streamCalls = mutableListOf<FakeCoreStreamCall>()

    override fun callWithTimeout(method: String, request: ByteArray, timeoutMs: Int): ByteArray {
        checkOpen()
        return ByteArray(0)
    }

    override fun stream(
        method: String,
        request: ByteArray,
        handler: StreamHandler,
    ): CoreStreamCall {
        checkOpen()
        return FakeCoreStreamCall(handler).also {
            synchronized(streamCalls) { streamCalls += it }
        }
    }

    override fun probe() {
        checkOpen()
    }

    override fun close() {
        closed = true
        completeAll("Unavailable: closed")
    }

    fun completeAll(errMessage: String?) {
        synchronized(streamCalls) { streamCalls.toList() }.forEach { it.complete(errMessage) }
    }

    private fun checkOpen() {
        if (closed) throw Exception("Unavailable: client closed")
    }
}

private class FakeCoreStreamCall(
    private val handler: StreamHandler,
) : CoreStreamCall {
    private var completed = false

    @Synchronized
    fun complete(errMessage: String?) {
        if (completed) return
        completed = true
        handler.onClosed(errMessage)
    }

    override fun close() {
        // Match production: StreamCall.Close cancels; onClosed is empty.
        complete(null)
    }
}
