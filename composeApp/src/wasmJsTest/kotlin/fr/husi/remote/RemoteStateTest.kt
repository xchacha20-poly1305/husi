package fr.husi.remote

import fr.husi.Key
import fr.husi.database.RemoteServer
import fr.husi.database.preference.long
import fr.husi.proto.daemon.Log
import fr.husi.proto.daemon.StartedAt
import fr.husi.proto.daemon.Version
import fr.husi.test.FakePreferencesDataStore
import fr.husi.test.FakeRemoteClient
import fr.husi.test.FakeRemoteServerStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteStateTest {
    private val server = RemoteServer(id = 1L, name = "Test", url = "https://example.com", secret = "secret")

    @Test
    fun `uptime uses the peer start time across reconnects`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.startedAtResponse = StartedAt(1700000000123L)
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        assertTrue(state.connected)
        assertEquals(1700000000123L, state.startedAt)

        advanceTimeBy(60000.milliseconds)
        state.disconnect()
        runCurrent()
        assertNull(state.startedAt)
        state.connect(server)
        runCurrent()
        assertTrue(state.connected)
        assertEquals(1700000000123L, state.startedAt)
    }

    @Test
    fun `heartbeat refreshes the peer start time without reconnecting`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.startedAtResponse = StartedAt(1700000000123L)
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()

        client.startedAtResponse = StartedAt(1700000060123L)
        advanceTimeBy(5000.milliseconds)
        runCurrent()
        assertEquals(1700000060123L, state.startedAt)
        assertTrue(state.connected)
        assertEquals(1, client.attempts.size)
    }

    @Test
    fun `missing or nonpositive peer start time leaves uptime unknown`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        assertNull(state.startedAt)

        for (value in listOf("0", "-1")) {
            client.startedAtResponse = StartedAt(value.toLong())
            advanceTimeBy(5000.milliseconds)
            runCurrent()
            assertNull(state.startedAt)
        }
        assertTrue(state.connected)
        assertEquals(1, client.attempts.size)
    }

    @Test
    fun `start time query failure keeps the connection usable and retries`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.startedAtFailure = true
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        assertTrue(state.connected)
        assertNull(state.startedAt)
        assertTrue(client.activeStreams > 0)

        client.startedAtFailure = false
        client.startedAtResponse = StartedAt(1700000000123L)
        advanceTimeBy(5000.milliseconds)
        runCurrent()
        assertEquals(1700000000123L, state.startedAt)
        assertEquals(1, client.attempts.size)
    }

    @Test
    fun `late start time response cannot restore a disconnected uptime`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        val gate = CompletableDeferred<Unit>()
        client.startedAtGate = gate
        client.startedAtResponse = StartedAt(1700000000123L)
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        assertTrue(state.connected)
        assertNull(state.startedAt)

        state.disconnect()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(state.selected)
        assertNull(state.startedAt)
        assertFalse(state.connected)
        assertEquals(0, client.activeStreams)
    }

    @Test
    fun `connection failures retry indefinitely with capped exponential backoff`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.connectFailure = true
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        val intervals = listOf(1000L, 2000, 4000, 8000, 16000) + List(12) { 30000L }
        var elapsed = 0L
        for ((index, interval) in intervals.withIndex()) {
            advanceTimeBy((interval - 1).milliseconds)
            runCurrent()
            assertEquals(index + 1, client.attempts.size)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            elapsed += interval
            assertEquals(elapsed, client.attempts.last().second)
            assertEquals(index + 2, client.attempts.size)
            assertEquals(server.copy(userOrder = 1L), state.selected)
            assertTrue(state.connecting)
            assertFalse(state.connected)
        }
    }

    @Test
    fun `heartbeat failure clears old streams and reconnects with reset backoff`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.connectFailure = true
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        advanceTimeBy(1000.milliseconds)
        runCurrent()
        client.connectFailure = false
        client.version = Version(version = "0.1.0")
        client.startedAtResponse = StartedAt(1700000000123L)
        advanceTimeBy(2000.milliseconds)
        runCurrent()
        assertTrue(state.connected)
        assertEquals(client.version, state.coreVersion)
        assertEquals("0.1.0", state.version)
        assertEquals(1700000000123L, state.startedAt)
        val activeStreams = client.activeStreams
        assertTrue(activeStreams > 0)
        state.logs.add(Log.Message())

        client.heartbeatFailure = true
        advanceTimeBy(5000.milliseconds)
        runCurrent()
        assertFalse(state.connected)
        assertTrue(state.connecting)
        assertEquals(server.copy(userOrder = 1L), state.selected)
        assertNull(state.startedAt)
        assertNull(state.coreVersion)
        assertEquals("", state.version)
        assertTrue(state.logs.isEmpty())
        assertEquals(0, client.activeStreams)

        client.heartbeatFailure = false
        client.version = Version(version = "0.2.0")
        client.startedAtResponse = StartedAt(1700000060123L)
        advanceTimeBy(1000.milliseconds)
        runCurrent()
        assertTrue(state.connected)
        assertEquals(client.version, state.coreVersion)
        assertEquals("0.2.0", state.version)
        assertEquals(1700000060123L, state.startedAt)
        assertEquals(9000L, client.attempts.last().second)
        assertEquals(activeStreams, client.activeStreams)
    }

    @Test
    fun `switching servers cancels old backoff and disconnect clears selection`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.connectFailure = true
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        advanceTimeBy(500.milliseconds)
        val replacement = server.copy(id = 2L, url = "https://replacement.example")
        client.connectFailure = false
        state.save(replacement)
        state.connect(replacement)
        runCurrent()
        advanceTimeBy(60000.milliseconds)
        runCurrent()
        assertEquals(listOf(server.url, replacement.url), client.attempts.map { it.first })
        assertEquals(replacement.copy(userOrder = 2L), state.selected)
        state.disconnect()
        runCurrent()
        assertNull(state.selected)
        assertFalse(state.connected)
        assertFalse(state.connecting)
        assertEquals(0, client.activeStreams)
    }

    @Test
    fun `deleting the selected server cancels pending retries`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.connectFailure = true
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        state.delete(server)
        advanceTimeBy(60000.milliseconds)
        runCurrent()
        assertNull(state.selected)
        assertTrue(state.servers.isEmpty())
        assertEquals(1, client.attempts.size)
    }

    @Test
    fun `failed deletion keeps the selected server connected`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        val store = FakeRemoteServerStore()
        val state = state(client, store)
        val saved = state.save(server)
        state.connect(saved)
        runCurrent()
        store.writeFailure = IllegalStateException("Storage unavailable")

        assertFails { state.delete(saved) }
        runCurrent()
        assertEquals(saved, state.selected)
        assertEquals(listOf(saved), state.servers.toList())
        assertTrue(state.connected)
    }

    @Test
    fun `late connection completion cannot restore a disconnected session`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        val gate = CompletableDeferred<Unit>()
        client.connectGate = gate
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        state.disconnect()
        gate.complete(Unit)
        runCurrent()
        assertNull(state.selected)
        assertFalse(state.connected)
        assertEquals(0, client.activeStreams)
    }

    @Test
    fun `broken subscriptions retry without restarting healthy streams`() = runTest {
        val client = RecordingClient { testScheduler.currentTime }
        client.logFailure = true
        val state = state(client)
        state.save(server)
        state.connect(server)
        runCurrent()
        advanceTimeBy(3000.milliseconds)
        runCurrent()
        assertEquals(listOf(0L, 1000L, 3000L), client.logAttempts)
        client.logFailure = false
        advanceTimeBy(4000.milliseconds)
        runCurrent()
        assertEquals(1, client.attempts.size)
        assertTrue(state.connected)
        assertEquals(7000L, client.logAttempts.last())
    }

    private fun TestScope.state(client: RemoteClient, store: FakeRemoteServerStore = FakeRemoteServerStore()) = RemoteState(
        backgroundScope,
        client,
        store,
        flowOf(1000),
        FakePreferencesDataStore().preferences.long(Key.ACTIVE_REMOTE_SERVER_ID),
    )

    private class RecordingClient(private val now: () -> Long) : FakeRemoteClient() {
        val attempts = mutableListOf<Pair<String, Long>>()
        val logAttempts = mutableListOf<Long>()
        var connectFailure = false
        var heartbeatFailure = false
        var logFailure = false
        var connectGate: CompletableDeferred<Unit>? = null
        var startedAtGate: CompletableDeferred<Unit>? = null
        var startedAtFailure = false
        var startedAtResponse = StartedAt()
        var activeStreams = 0
        var version = Version()

        override suspend fun connect(url: String, secret: String): Version {
            attempts.add(url to now())
            connectGate?.let { withContext(NonCancellable) { it.await() } }
            check(!connectFailure) { "Offline" }
            return version
        }

        override suspend fun call(method: String, request: ByteArray): ByteArray {
            check(method != "GetVersion" || !heartbeatFailure) { "Disconnected" }
            if (method == "GetStartedAt") {
                startedAtGate?.let { withContext(NonCancellable) { it.await() } }
                check(!startedAtFailure) { "GetStartedAt unavailable" }
                return startedAtResponse.encode()
            }
            return byteArrayOf()
        }

        override fun stream(method: String, request: ByteArray): Flow<ByteArray> = flow {
            if (method == "SubscribeLog") {
                logAttempts.add(now())
                check(!logFailure) { "Stream lost" }
            }
            activeStreams++
            try {
                awaitCancellation()
            } finally {
                activeStreams--
            }
        }

        override fun close() = Unit
        override suspend fun testConnection(url: String, secret: String) = "test"
        override fun normalizeUrl(value: String) = value
    }
}
