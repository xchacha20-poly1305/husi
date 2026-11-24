@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package fr.husi.ui.clash

import fr.husi.net.HttpStatus
import fr.husi.remote.ClashApiException
import fr.husi.resources.Res
import fr.husi.resources.clash_unsupported
import fr.husi.test.FakeClashApi
import fr.husi.ui.StringOrRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class ClashSmartModelTest {
    @Test
    fun `refresh merges Smart groups from proxies and weights including errors and message`() = runTest {
        val api = FakeClashApi()
        api.handler = { operation, _ ->
            when (operation) {
                "proxies" -> Json.parseToJsonElement(
                    """
                    {
                      "SmartA": {
                        "name": "SmartA",
                        "type": "Smart",
                        "all": ["Node1", "Node2"],
                        "now": "Node1",
                        "fixed": "Node1",
                        "smart": true,
                        "expectedStatus": "204",
                        "emptyFallback": "DIRECT",
                        "policy-priority": "HK:1.50",
                        "useLightGBM": true,
                        "collectData": true,
                        "sampleRate": 0.5,
                        "preferASN": true,
                        "tolerance": 50
                      },
                      "Selector": {
                        "name": "Selector",
                        "type": "Selector",
                        "all": ["Node1"],
                        "smart": false
                      }
                    }
                    """.trimIndent(),
                )
                "smartWeights" -> Json.parseToJsonElement(
                    """
                    {
                      "weights": {
                        "SmartA": [{"Name": "Node1", "Rank": "MostUsed", "Weight": 88.5}],
                        "ExtraSmart": []
                      },
                      "errors": {
                        "BrokenSmart": "cache read error"
                      },
                      "message": "partial"
                    }
                    """.trimIndent(),
                )
                else -> error(operation)
            }
        }
        val model = ClashSmartModel(api)
        model.refresh()

        assertFalse(model.state.unsupported)
        assertEquals("", model.state.error)
        assertEquals("partial", model.state.message)
        assertEquals(listOf("SmartA", "ExtraSmart", "BrokenSmart"), model.state.groups.map { it.name })

        val smartA = model.state.groups[0]
        assertEquals("Node1", smartA.fixed)
        assertEquals("204", smartA.expectedStatus)
        assertEquals("DIRECT", smartA.emptyFallback)
        assertEquals("HK:1.50", smartA.policyPriority)
        assertEquals(true, smartA.useLightGBM)
        assertEquals(true, smartA.collectData)
        assertEquals(0.5, smartA.sampleRate)
        assertEquals(true, smartA.preferASN)
        assertEquals(50, smartA.tolerance)
        assertEquals("Node1", smartA.ranks.single().name)
        assertEquals(88.5, smartA.ranks.single().weight)

        val broken = model.state.groups[2]
        assertEquals("cache read error", broken.error)
    }

    @Test
    fun `refresh handles unsupported weights endpoint while preserving Smart groups`() = runTest {
        val api = FakeClashApi()
        api.handler = { operation, _ ->
            when (operation) {
                "proxies" -> Json.parseToJsonElement(
                    """{"SmartA":{"name":"SmartA","type":"Smart","all":["Node1"],"now":"Smart - Select","smart":true}}""",
                )
                "smartWeights" -> throw ClashApiException("http", HttpStatus.NOT_FOUND, "Not found")
                else -> error(operation)
            }
        }
        val model = ClashSmartModel(api)
        model.refresh()

        assertTrue(model.state.unsupported)
        assertEquals(listOf("SmartA"), model.state.groups.map { it.name })
    }

    @Test
    fun `refreshGroupWeights updates only the target group and clears its error`() = runTest {
        val api = FakeClashApi()
        api.handler = { operation, input ->
            when (operation) {
                "proxies" -> Json.parseToJsonElement(
                    """
                    {
                      "SmartA": {"name": "SmartA", "type": "Smart", "all": ["Node1"], "smart": true},
                      "SmartB": {"name": "SmartB", "type": "Smart", "all": ["Node2"], "smart": true}
                    }
                    """.trimIndent(),
                )
                "smartWeights" -> Json.parseToJsonElement(
                    """{"weights":{"SmartA":[],"SmartB":[]},"errors":{"SmartA":"stale"}}""",
                )
                "smartGroupWeights" -> {
                    assertEquals(JsonPrimitive("SmartA"), input["name"])
                    Json.parseToJsonElement("""[{"Name":"Node1","Rank":"OccasionalUsed","Weight":42}]""")
                }
                else -> error(operation)
            }
        }
        val model = ClashSmartModel(api)
        model.refresh()
        assertEquals("stale", model.state.groups.first { it.name == "SmartA" }.error)

        model.refreshGroupWeights("SmartA")
        val updatedA = model.state.groups.first { it.name == "SmartA" }
        val untouchedB = model.state.groups.first { it.name == "SmartB" }
        assertEquals("", updatedA.error)
        assertEquals("Node1", updatedA.ranks.single().name)
        assertEquals(42.0, updatedA.ranks.single().weight)
        assertTrue(untouchedB.ranks.isEmpty())
    }

    @Test
    fun `clearFixed refreshes proxies callback and reloads Smart groups`() = runTest {
        val api = FakeClashApi()
        var fixed = "Node1"
        api.handler = { operation, input ->
            when (operation) {
                "proxies" -> Json.parseToJsonElement(
                    """{"SmartA":{"name":"SmartA","type":"Smart","all":["Node1"],"now":"${fixed.ifEmpty { "Smart - Select" }}","fixed":"$fixed","smart":true}}""",
                )
                "smartWeights" -> Json.parseToJsonElement("""{"weights":{"SmartA":[]}}""")
                "clearFixed" -> {
                    assertEquals(JsonPrimitive("SmartA"), input["group"])
                    fixed = ""
                    JsonNull
                }
                else -> error(operation)
            }
        }
        var proxiesSynced = 0
        val model = ClashSmartModel(api) { proxiesSynced++ }
        model.refresh()
        assertEquals("Node1", model.state.groups.single().fixed)

        model.clearFixed("SmartA")
        assertEquals(1, proxiesSynced)
        assertEquals("", model.state.groups.single().fixed)
        assertEquals("Smart - Select", model.state.groups.single().selected)
    }

    @Test
    fun `flushAllCache and flushConfigCache validate config names, allow slashes, refresh proxies, and report success`() =
        runTest {
            assertFalse(ClashSmartModel.isValidConfigName(""))
            assertFalse(ClashSmartModel.isValidConfigName("   "))
            assertFalse(ClashSmartModel.isValidConfigName("."))
            assertFalse(ClashSmartModel.isValidConfigName(".."))
            assertTrue(ClashSmartModel.isValidConfigName("sub/profile.yaml"))
            assertTrue(ClashSmartModel.isValidConfigName(" profile / name "))

            val api = FakeClashApi()
            api.handler = { operation, _ ->
                when (operation) {
                    "proxies" -> Json.parseToJsonElement("{}")
                    "smartWeights" -> Json.parseToJsonElement("""{"weights":{}}""")
                    "flushSmartCache", "flushSmartConfigCache" -> JsonNull
                    else -> error(operation)
                }
            }
            var proxiesSynced = 0
            val model = ClashSmartModel(api) { proxiesSynced++ }

            var flushAllSucceeded = false
            model.flushAllCache(onSuccess = { flushAllSucceeded = true })
            assertTrue(flushAllSucceeded)
            assertEquals(1, proxiesSynced)

            model.flushConfigCache("..")
            assertEquals(0, api.requests.count { it.first == "flushSmartConfigCache" })

            var flushedConfig = ""
            model.flushConfigCache(" sub/config.yaml ", onSuccess = { flushedConfig = it })
            assertEquals("sub/config.yaml", flushedConfig)
            assertEquals(2, proxiesSynced)
            assertEquals(
                JsonPrimitive("sub/config.yaml"),
                api.requests.single { it.first == "flushSmartConfigCache" }.second["config"],
            )
        }

    @Test
    fun `upgradeLgbm disables only upgrade action on unsupported and rejects non-ok status`() = runTest {
        val api = FakeClashApi()
        api.handler = { operation, _ ->
            when (operation) {
                "upgradeLgbm" -> Json.parseToJsonElement("""{"status":"error"}""")
                else -> error(operation)
            }
        }
        val model = ClashSmartModel(api)
        var capturedError: StringOrRes? = null
        model.upgradeLgbm(onError = { capturedError = it })
        assertTrue(capturedError is StringOrRes.Direct)
        assertFalse(model.state.unavailable[ClashSmartModel.ACTION_UPGRADE_LGBM] == true)

        api.handler = { operation, _ ->
            if (operation == "upgradeLgbm") {
                throw ClashApiException("http", HttpStatus.NOT_FOUND, "Not found")
            }
            error(operation)
        }
        capturedError = null
        model.upgradeLgbm(onError = { capturedError = it })
        assertEquals(StringOrRes.Res(Res.string.clash_unsupported), capturedError)
        assertTrue(model.state.unavailable[ClashSmartModel.ACTION_UPGRADE_LGBM] == true)
        assertFalse(model.state.unavailable[ClashSmartModel.ACTION_FLUSH_ALL] == true)

        model.upgradeLgbm()
        assertEquals(2, api.requests.count { it.first == "upgradeLgbm" })
    }
}
