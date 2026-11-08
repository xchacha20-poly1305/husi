package fr.husi.remote

import fr.husi.Key
import fr.husi.database.RemoteServer
import fr.husi.database.preference.long
import fr.husi.test.FakePreferencesDataStore
import fr.husi.test.FakeRemoteServerDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class RemoteRefreshIntervalTest {
    @Test
    fun `changing refresh interval replaces traffic streams without restarting logs or connection`() = runTest {
        val intervals = MutableStateFlow(1000)
        val client = RecordingClient()
        val servers = FakeRemoteServerDao()
        val state = RemoteState(
            backgroundScope, client, servers, intervals,
            FakePreferencesDataStore().preferences.long(Key.ACTIVE_REMOTE_SERVER_ID),
        )
        val server = state.save(RemoteServer(name = "Test", url = "http://localhost"))
        state.connect(server)
        runCurrent()
        val timedMethods = setOf("SubscribeStatus", "SubscribeConnections")
        val initialStreams = client.subscriptions.filter { it.method in timedMethods }
        assertEquals(2, initialStreams.size)
        assertTrue(initialStreams.all { it.active && it.request.text("interval") == "1000000000" })
        val logs = client.subscriptions.single { it.method == "SubscribeLog" }

        intervals.value = 500
        runCurrent()
        assertTrue(initialStreams.none { it.active })
        val refreshedStreams = client.subscriptions.filter { it.method in timedMethods && it.active }
        assertEquals(2, refreshedStreams.size)
        assertTrue(refreshedStreams.all { it.request.text("interval") == "500000000" })
        assertTrue(logs.active)
        assertEquals(1, client.connectCount)

        intervals.value = 0
        runCurrent()
        assertTrue(client.subscriptions.none { it.method in timedMethods && it.active })
        assertTrue(logs.active)
        assertTrue(state.connected)

        intervals.value = 3000
        runCurrent()
        assertEquals(2, client.subscriptions.count {
            it.method in timedMethods && it.active && it.request.text("interval") == "3000000000"
        })
        state.disconnect()
        runCurrent()
        assertFalse(state.connected)
        assertTrue(client.subscriptions.none { it.active })
    }

    private class Subscription(val method: String, val request: JsonObject, var active: Boolean = true)

    private class RecordingClient : RemoteClient {
        val subscriptions = mutableListOf<Subscription>()
        var connectCount = 0
        override suspend fun connect(url: String, secret: String): JsonObject {
            connectCount++
            return JsonObject(emptyMap())
        }
        override suspend fun call(method: String, request: JsonObject) = JsonObject(emptyMap())
        override fun stream(method: String, request: JsonObject): Flow<JsonObject> = flow {
            val subscription = Subscription(method, request)
            subscriptions += subscription
            try {
                awaitCancellation()
            } finally {
                subscription.active = false
            }
        }
        override fun bidi(method: String, requests: Flow<JsonObject>): Flow<JsonObject> = emptyFlow()
        override fun close() = Unit
        override suspend fun testConnection(url: String, secret: String) = "test"
        override fun normalizeUrl(value: String) = value
    }
}
