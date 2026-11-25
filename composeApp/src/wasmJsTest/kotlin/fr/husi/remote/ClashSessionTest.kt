@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package fr.husi.remote

import fr.husi.net.HttpStatus
import fr.husi.test.FakeClashApi
import fr.husi.ui.dashboard.SmartConnectionInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration.Companion.milliseconds

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
        val subscriptions = MutableStateFlow(
            setOf(
                RemoteSubscription.STATS,
                RemoteSubscription.PROXY_SET,
                RemoteSubscription.CONNECTIONS,
                RemoteSubscription.LOGS,
            ),
        )
        val observation = backgroundScope.async { session.observe(intervals, subscriptions) }
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
        assertEquals(0, api.requests.count { it.first == "smartWeights" })
    }

    @Test
    fun `Smart groups expose fixed selection, weights, clearFixed, and survive unsupported weights endpoint`() =
        runTest {
            val api = FakeClashApi()
            var fixed = "NodeA"
            api.handler = { operation, _ ->
                when (operation) {
                    "version" -> Json.parseToJsonElement("""{"version":"test"}""")
                    "config" -> Json.parseToJsonElement("""{"mode":"rule"}""")
                    "proxies" -> Json.parseToJsonElement(
                        """
                        {
                          "SmartGroup": {
                            "name": "SmartGroup",
                            "type": "Smart",
                            "all": ["NodeA", "NodeB"],
                            "now": "${if (fixed.isEmpty()) "Smart - Select" else fixed}",
                            "fixed": "$fixed",
                            "smart": true
                          },
                          "NodeA": {"name": "NodeA", "type": "Shadowsocks", "history": [{"time": "now", "delay": 24}]},
                          "NodeB": {"name": "NodeB", "type": "Direct", "history": []}
                        }
                        """.trimIndent(),
                    )
                    "smartWeights" -> Json.parseToJsonElement(
                        """
                        {
                          "weights": {
                            "SmartGroup": [
                              {"Name": "NodeA", "Rank": "MostUsed", "Weight": 95.5},
                              {"Name": "NodeB", "Rank": "RarelyUsed", "Weight": 10}
                            ]
                          }
                        }
                        """.trimIndent(),
                    )
                    "clearFixed" -> {
                        fixed = ""
                        JsonNull
                    }
                    else -> JsonNull
                }
            }
            val session = ClashSession("url", "secret", api)
            session.connect("url", "secret")
            val observation = backgroundScope.async {
                session.observe(MutableStateFlow(0), MutableStateFlow(setOf(RemoteSubscription.PROXY_SET)))
            }
            runCurrent()

            val group = session.dashboard.proxySets.single()
            assertEquals("SmartGroup", group.tag)
            assertTrue(group.selectable)
            assertEquals("NodeA", group.fixed)
            assertEquals("MostUsed", group.items[0].smartRank)
            assertEquals(95.5, group.items[0].smartWeight)
            assertEquals("RarelyUsed", group.items[1].smartRank)
            assertEquals(10.0, group.items[1].smartWeight)

            session.clearFixed("SmartGroup")
            assertEquals(
                """{"group":"SmartGroup"}""",
                api.requests.single { it.first == "clearFixed" }.second.toString(),
            )
            assertEquals("", session.dashboard.proxySets.single().fixed)
            assertEquals("Smart - Select", session.dashboard.proxySets.single().selected)

            session.close()
            observation.cancel()
            runCurrent()

            val unsupportedApi = FakeClashApi()
            unsupportedApi.handler = { operation, _ ->
                when (operation) {
                    "version" -> Json.parseToJsonElement("""{"version":"test"}""")
                    "config" -> Json.parseToJsonElement("""{"mode":"rule"}""")
                    "proxies" -> Json.parseToJsonElement(
                        """{"SmartGroup":{"name":"SmartGroup","type":"Smart","all":["NodeA"],"now":"Smart - Select","smart":true},"NodeA":{"name":"NodeA","type":"Direct"}}""",
                    )
                    "smartWeights" -> throw ClashApiException("http", HttpStatus.NOT_FOUND, "Not found")
                    else -> JsonNull
                }
            }
            val fallbackSession = ClashSession("url", "secret", unsupportedApi)
            fallbackSession.connect("url", "secret")
            val fallbackObservation = backgroundScope.async {
                fallbackSession.observe(MutableStateFlow(0), MutableStateFlow(setOf(RemoteSubscription.PROXY_SET)))
            }
            runCurrent()
            assertEquals("SmartGroup", fallbackSession.dashboard.proxySets.single().tag)
            assertEquals(1, unsupportedApi.requests.count { it.first == "smartWeights" })

            fallbackSession.refreshProxies()
            assertEquals(1, unsupportedApi.requests.count { it.first == "smartWeights" })
            fallbackSession.close()
            fallbackObservation.cancel()
            runCurrent()
        }

    @Test
    fun `ClashConnectionTracker maps Smart metadata, preserves it on close, and ClashSession blocks Smart connections`() =
        runTest {
            val tracker = ClashConnectionTracker()
            val mixedSnapshot = ClashStreams.connections.decode(buildJsonObject {
                put("uploadTotal", 300)
                put("downloadTotal", 600)
                put("connections", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "smart-1")
                        put("upload", 100)
                        put("download", 200)
                        put("start", "2026-01-01T00:00:00Z")
                        put("smart", true)
                        putJsonObject("metadata") {
                            put("network", "tcp")
                            put("type", "Mixed")
                            put("sourceIP", "127.0.0.1")
                            put("sourcePort", "1234")
                            put("destinationIP", "192.0.2.1")
                            put("destinationPort", "443")
                            put("host", "smart.test")
                            put("smartBlock", "normal")
                            put("smartTarget", "Domain [smart.test]")
                            put("wildcardTarget", "Domain [*.smart.test]")
                            put("exitGeoIP", "US")
                            put("exitIPASN", "AS13335")
                        }
                        put("chains", buildJsonArray {
                            add("NodeA")
                            add("SmartGroup")
                        })
                        put("rule", "Domain")
                        put("rulePayload", "smart.test")
                    })
                    add(buildJsonObject {
                        put("id", "plain-1")
                        put("upload", 200)
                        put("download", 400)
                        put("start", "2026-01-01T00:00:00Z")
                        put("smart", false)
                        putJsonObject("metadata") {
                            put("network", "tcp")
                            put("type", "HTTP")
                            put("sourceIP", "::1")
                            put("sourcePort", "1235")
                            put("destinationIP", "192.0.2.2")
                            put("destinationPort", "443")
                            put("host", "plain.test")
                        }
                        put("chains", buildJsonArray { add("DIRECT") })
                        put("rule", "Match")
                        put("rulePayload", "")
                    })
                })
            })

            val records = tracker.apply(mixedSnapshot, 1000)
            val smartRecord = records.single { it.uuid == "smart-1" }
            val plainRecord = records.single { it.uuid == "plain-1" }
            assertEquals(
                SmartConnectionInfo(
                    blockState = "normal",
                    target = "Domain [smart.test]",
                    wildcardTarget = "Domain [*.smart.test]",
                    exitGeoIP = "US",
                    exitIPASN = "AS13335",
                ),
                smartRecord.smart,
            )
            assertTrue(smartRecord.smart?.canBlock == true)
            assertNull(plainRecord.smart)

            val closedSnapshot = snapshot(300, 600, includeConnection = false)
            val closedRecords = tracker.apply(closedSnapshot, 2000)
            val closedSmart = closedRecords.single { it.uuid == "smart-1" }
            assertTrue(closedSmart.isClosed)
            assertEquals("Domain [smart.test]", closedSmart.smart?.target)

            val api = FakeClashApi()
            api.handler = { operation, _ ->
                when (operation) {
                    "version" -> Json.parseToJsonElement("""{"version":"test"}""")
                    else -> JsonNull
                }
            }
            val session = ClashSession("url", "secret", api)
            session.connect("url", "secret")
            session.blockSmartConnection("smart-1")
            assertEquals(
                """{"id":"smart-1"}""",
                api.requests.single { it.first == "blockSmartConnection" }.second.toString(),
            )
            session.close()
        }

    @Test
    fun `page subscriptions gate Clash streams and polling independently`() = runTest {
        val api = FakeClashApi()
        val activeStreams = mutableMapOf<String, Int>()
        val startedCounts = mutableMapOf<String, Int>()
        api.handler = { operation, _ ->
            when (operation) {
                "version" -> Json.parseToJsonElement("""{"version":"test"}""")
                "config" -> Json.parseToJsonElement("""{"mode":"rule"}""")
                "proxies" -> Json.parseToJsonElement("{}")
                else -> JsonNull
            }
        }
        api.streams = { kind, _ ->
            flow {
                startedCounts[kind] = (startedCounts[kind] ?: 0) + 1
                activeStreams[kind] = (activeStreams[kind] ?: 0) + 1
                if (kind == "connections") {
                    emit(
                        buildJsonObject {
                            put("uploadTotal", 100)
                            put("downloadTotal", 200)
                            put("connections", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", "one")
                                    put("upload", 100)
                                    put("download", 200)
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
                                    put("chains", buildJsonArray { add("DIRECT") })
                                    put("rule", "Match")
                                    put("rulePayload", "")
                                })
                            })
                        },
                    )
                }
                try {
                    awaitCancellation()
                } finally {
                    val remaining = (activeStreams[kind] ?: 0) - 1
                    if (remaining <= 0) {
                        activeStreams.remove(kind)
                    } else {
                        activeStreams[kind] = remaining
                    }
                }
            }
        }
        val session = ClashSession("url", "secret", api)
        session.connect("url", "secret")
        val subscriptions = MutableStateFlow(emptySet<RemoteSubscription>())
        val observation = backgroundScope.async { session.observe(MutableStateFlow(1000), subscriptions) }
        runCurrent()

        assertTrue(activeStreams.isEmpty())
        assertEquals(0, api.requests.count { it.first == "proxies" })
        assertEquals(0, api.requests.count { it.first == "config" })

        subscriptions.value = setOf(RemoteSubscription.STATS)
        runCurrent()
        assertEquals(setOf("traffic", "memory", "connections"), activeStreams.keys)
        assertTrue(api.requests.count { it.first == "config" } >= 1)
        assertEquals(0, api.requests.count { it.first == "proxies" })
        assertEquals(1, session.dashboard.connections.size)

        subscriptions.value = setOf(RemoteSubscription.STATS, RemoteSubscription.CONNECTIONS)
        runCurrent()
        subscriptions.value = setOf(RemoteSubscription.CONNECTIONS)
        runCurrent()
        assertEquals(setOf("connections"), activeStreams.keys)
        assertEquals(1, startedCounts["connections"])

        subscriptions.value = setOf(RemoteSubscription.PROXY_SET)
        runCurrent()
        assertTrue(activeStreams.isEmpty())
        assertTrue(session.dashboard.connections.isEmpty())
        assertTrue(api.requests.count { it.first == "proxies" } >= 1)

        subscriptions.value = setOf(RemoteSubscription.LOGS)
        runCurrent()
        assertEquals(setOf("logs"), activeStreams.keys)
        val proxiesBefore = api.requests.count { it.first == "proxies" }
        val configBefore = api.requests.count { it.first == "config" }
        advanceTimeBy(5000.milliseconds)
        runCurrent()
        assertEquals(proxiesBefore, api.requests.count { it.first == "proxies" })
        assertEquals(configBefore, api.requests.count { it.first == "config" })

        subscriptions.value = emptySet()
        runCurrent()
        assertTrue(activeStreams.isEmpty())

        session.close()
        observation.cancel()
        runCurrent()
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
