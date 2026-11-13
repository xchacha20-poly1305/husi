@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package fr.husi.remote

import fr.husi.test.FakeClashApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonPrimitive

class ClashSessionTest {
    @Test
    fun `connection snapshots use elapsed time and retain closed connections`() {
        val tracker = ClashConnectionTracker()
        var connection = tracker.apply(snapshot(100, 200), 1000).single()
        assertEquals(0L, connection.uploadSpeed)
        assertEquals("[::1]:1234", connection.src)
        connection = tracker.apply(snapshot(200, 400), 1500).single()
        assertEquals(200L, connection.uploadSpeed)
        assertEquals(400L, connection.downloadSpeed)
        connection = tracker.apply(snapshot(10, 20), 2000).single()
        assertEquals(0L, connection.uploadSpeed)
        connection = tracker.apply(snapshot(10, 20, includeConnection = false), 3000).single()
        assertTrue(connection.isClosed)
        assertEquals(0L, connection.downloadSpeed)
    }

    @Test
    fun `clash observes only its protocol and refresh interval replaces timed streams`() = runTest {
        val api = FakeClashApi()
        val active = mutableListOf<String>()
        val ended = mutableListOf<String>()
        var mode = "rule"
        api.handler = { operation, input ->
            when (operation) {
                "version" -> Json.parseToJsonElement("""{"version":"test"}""")
                "config" -> Json.parseToJsonElement("""{"mode":"$mode"}""")
                "setMode" -> {
                    mode = input.getValue("mode").jsonPrimitive.content
                    JsonNull
                }
                "proxies" ->
                    Json.parseToJsonElement(
                        """{"Group":{"name":"Group","type":"Selector","all":["Node"],"now":"Node"},"Node":{"name":"Node","type":"Direct","history":[{"time":"now","delay":42}]}}""",
                    )
                else -> JsonNull
            }
        }
        api.streams = { kind, interval ->
            flow {
                val key = "$kind:$interval"
                active += key
                try {
                    awaitCancellation()
                } finally {
                    active.remove(key)
                    ended += key
                }
            }
        }
        val session = ClashSession("url", "secret", api)
        session.connect("url", "secret")
        val intervals = MutableStateFlow(1000)
        val observation = backgroundScope.async { session.observe(intervals) }
        runCurrent()
        assertEquals("Node", session.dashboard.proxySets.single().selected)
        assertEquals(42, session.dashboard.proxySets.single().items.single().urlTestDelay)
        assertFalse(session.dashboard.supportsGoroutines)
        assertTrue("logs:1000" in active)
        intervals.value = 500
        runCurrent()
        assertTrue("connections:1000" in ended)
        assertTrue("connections:500" in active)
        assertEquals(0, ended.count { it.startsWith("logs") })
        intervals.value = 0
        runCurrent()
        assertEquals(listOf("logs:1000"), active)
        session.select("Group", "Node")
        session.closeConnection("one")
        session.closeConnections()
        session.setMode("direct")
        assertEquals("direct", session.dashboard.selectedClashMode)
        assertEquals(
            "{\"mode\":\"direct\"}",
            api.requests.single { it.first == "setMode" }.second.toString(),
        )
        session.close()
        observation.cancel()
        runCurrent()
        assertTrue(api.closed)
        assertTrue(active.isEmpty())
    }

    private fun snapshot(upload: Long, download: Long, includeConnection: Boolean = true) = ClashStreams.connections.decode(buildJsonObject {
        put("uploadTotal", upload)
        put("downloadTotal", download)
        put("connections", buildJsonArray {
            if (includeConnection) {
                add(buildJsonObject {
                    put("id", "one")
                    put("upload", upload)
                    put("download", download)
                    put("start", "2026-01-01T00:00:00Z")
                    putJsonObject("metadata") {
                        put("network", "tcp")
                        put("type", "HTTP")
                        put("sourceIP", "::1")
                        put("sourcePort", "1234")
                        put("destinationIP", "192.0.2.1")
                        put("destinationPort", "443")
                        put("host", "example.test")
                    }
                    put("chains", buildJsonArray {
                        add("Node")
                        add("Group")
                    })
                    put("rule", "Domain")
                    put("rulePayload", "example.test")
                })
            }
        })
    })
}
