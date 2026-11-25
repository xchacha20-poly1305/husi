@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.remote

import fr.husi.Key
import fr.husi.database.RemoteServer
import fr.husi.database.preference.long
import fr.husi.proto.daemon.ClashModeStatus
import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.Version
import fr.husi.test.FakePreferencesDataStore
import fr.husi.test.FakeRemoteClient
import fr.husi.test.FakeRemoteServerStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SingBoxSessionSubscriptionTest {
    private val server = RemoteServer(id = 1L, name = "Test", url = "https://example.com", secret = "secret")
    private val globalStreams = setOf(
        "SubscribeTailscaleStatus",
        "SubscribeOpenConnectStatus",
        "SubscribeOpenVPNStatus",
        "SubscribeServiceStatus",
    )

    @Test
    fun `connect keeps only global notification streams active until a page subscribes`() = runTest {
        val client = RecordingClient()
        val state = newState(client)

        state.save(server)
        state.connect(server)
        runCurrent()

        assertTrue(state.connected)
        assertEquals(globalStreams, client.activeStreamNames())
        assertEquals(0, client.calls["GetClashModeStatus"] ?: 0)
    }

    @Test
    fun `each page subscription opens and closes only its own streams`() = runTest {
        val client = RecordingClient()
        val state = newState(client)

        state.save(server)
        state.connect(server)
        runCurrent()

        val releaseStats = state.retainSubscription(RemoteSubscription.STATS)
        runCurrent()
        assertEquals(
            globalStreams + setOf("SubscribeStatus", "SubscribeClashMode"),
            client.activeStreamNames(),
        )
        assertEquals(1, client.calls["GetClashModeStatus"])
        client.statusEvents.emit(
            Status(
                connectionsIn = 7,
                uplink = 100L,
                downlink = 200L,
                uplinkTotal = 1000L,
                downlinkTotal = 2000L,
            ),
        )
        runCurrent()
        assertEquals(7, state.dashboard.activeConnectionCount)
        assertFalse("SubscribeConnections" in client.activeStreamNames())

        val releaseProxySet = state.retainSubscription(RemoteSubscription.PROXY_SET)
        runCurrent()
        assertEquals(
            globalStreams + setOf("SubscribeStatus", "SubscribeClashMode", "SubscribeGroups"),
            client.activeStreamNames(),
        )

        releaseStats()
        runCurrent()
        assertEquals(globalStreams + setOf("SubscribeGroups"), client.activeStreamNames())
        assertEquals(1, client.starts["SubscribeGroups"])
        for (method in globalStreams) {
            assertEquals(1, client.starts[method])
        }

        val releaseConnections = state.retainSubscription(RemoteSubscription.CONNECTIONS)
        runCurrent()
        assertEquals(
            globalStreams + setOf("SubscribeGroups", "SubscribeConnections"),
            client.activeStreamNames(),
        )
        releaseProxySet()
        runCurrent()
        assertEquals(globalStreams + setOf("SubscribeConnections"), client.activeStreamNames())

        client.connectionEvents.emit(
            ConnectionEvents(
                reset = true,
                events = listOf(
                    ConnectionEvent(
                        type = ConnectionEventType.CONNECTION_EVENT_NEW,
                        id = "c1",
                        connection = Connection(
                            id = "c1",
                            inbound = "mixed",
                            network = "tcp",
                            createdAt = 1000L,
                        ),
                    ),
                ),
            ),
        )
        runCurrent()
        assertEquals(1, state.dashboard.connections.size)
        assertEquals(1, state.dashboard.activeConnectionCount)

        releaseConnections()
        runCurrent()
        assertEquals(globalStreams, client.activeStreamNames())
        assertTrue(state.dashboard.connections.isEmpty())

        val releaseLogs = state.retainSubscription(RemoteSubscription.LOGS)
        runCurrent()
        assertEquals(globalStreams + setOf("SubscribeLog"), client.activeStreamNames())

        releaseLogs()
        runCurrent()
        assertEquals(globalStreams, client.activeStreamNames())
    }

    @Test
    fun `overlapping retainSubscription calls keep streams open until the last holder releases`() = runTest {
        val client = RecordingClient()
        val state = newState(client)

        state.save(server)
        state.connect(server)
        runCurrent()

        val releaseFirst = state.retainSubscription(RemoteSubscription.LOGS)
        val releaseSecond = state.retainSubscription(RemoteSubscription.LOGS)
        runCurrent()
        assertEquals(globalStreams + setOf("SubscribeLog"), client.activeStreamNames())

        releaseFirst()
        releaseFirst()
        runCurrent()
        assertEquals(globalStreams + setOf("SubscribeLog"), client.activeStreamNames())

        releaseSecond()
        runCurrent()
        assertEquals(globalStreams, client.activeStreamNames())
    }

    @Test
    fun `retained page subscriptions resume automatically after heartbeat reconnect`() = runTest {
        val client = RecordingClient()
        val state = newState(client)
        state.retainSubscription(RemoteSubscription.STATS)

        state.save(server)
        state.connect(server)
        runCurrent()
        assertEquals(
            globalStreams + setOf("SubscribeStatus", "SubscribeClashMode"),
            client.activeStreamNames(),
        )

        client.heartbeatFailure = true
        advanceTimeBy(5000.milliseconds)
        runCurrent()
        assertFalse(state.connected)
        assertTrue(client.activeStreamNames().isEmpty())

        client.heartbeatFailure = false
        advanceTimeBy(1000.milliseconds)
        runCurrent()
        assertTrue(state.connected)
        assertEquals(
            globalStreams + setOf("SubscribeStatus", "SubscribeClashMode"),
            client.activeStreamNames(),
        )
        assertEquals(null, client.starts["SubscribeGroups"])
        assertEquals(null, client.starts["SubscribeConnections"])
        assertEquals(null, client.starts["SubscribeLog"])
    }

    private fun TestScope.newState(client: RemoteClient) = RemoteState(
        backgroundScope,
        client,
        FakeRemoteServerStore(),
        flowOf(1000),
        FakePreferencesDataStore().preferences.long(Key.ACTIVE_REMOTE_SERVER_ID),
    )

    private class RecordingClient : FakeRemoteClient() {
        val starts = mutableMapOf<String, Int>()
        val calls = mutableMapOf<String, Int>()
        val statusEvents = MutableSharedFlow<Status>()
        val connectionEvents = MutableSharedFlow<ConnectionEvents>()
        var heartbeatFailure = false
        private val activeCounts = mutableMapOf<String, Int>()

        fun activeStreamNames(): Set<String> = activeCounts.keys.toSet()

        override suspend fun connect(url: String, secret: String) = Version(version = "1.0.0")

        override suspend fun call(method: String, request: ByteArray): ByteArray {
            calls[method] = (calls[method] ?: 0) + 1
            check(method != "GetVersion" || !heartbeatFailure) { "Disconnected" }
            return when (method) {
                "GetClashModeStatus" -> ClashModeStatus(
                    modeList = listOf("rule", "global"),
                    currentMode = "rule",
                ).encode()

                else -> byteArrayOf()
            }
        }

        override fun stream(method: String, request: ByteArray): Flow<ByteArray> = flow {
            starts[method] = (starts[method] ?: 0) + 1
            activeCounts[method] = (activeCounts[method] ?: 0) + 1
            try {
                when (method) {
                    "SubscribeStatus" -> statusEvents.collect { emit(it.encode()) }
                    "SubscribeConnections" -> connectionEvents.collect { emit(it.encode()) }
                    else -> awaitCancellation()
                }
            } finally {
                val remaining = (activeCounts[method] ?: 0) - 1
                if (remaining <= 0) {
                    activeCounts.remove(method)
                } else {
                    activeCounts[method] = remaining
                }
            }
        }
    }
}
