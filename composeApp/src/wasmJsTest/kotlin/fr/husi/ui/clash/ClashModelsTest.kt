@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package fr.husi.ui.clash

import fr.husi.remote.ClashRequests
import fr.husi.remote.ClashApiException
import fr.husi.test.FakeClashApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClashModelsTest {
    @Test
    fun `rules default to enabled and UUID toggles are serialized then refreshed`() = runTest {
        val api = FakeClashApi()
        val gate = CompletableDeferred<Unit>()
        var disabled = false
        api.handler = { operation, _ ->
            when (operation) {
                "rules" -> rules(disabled)
                "toggleRule" -> {
                    gate.await()
                    disabled = !disabled
                    JsonNull
                }

                else -> error(operation)
            }
        }
        val model = ClashRulesModel(api)
        model.refresh()
        assertFalse(model.rules.single().disabled)
        val rule = model.rules.single()
        val first = async {
            model.toggle(rule)
        }
        runCurrent()
        assertTrue(model.pending[rule.uuid] == true)
        assertFalse(model.rules.single().disabled)
        model.toggle(rule)
        assertEquals(1, api.requests.count { it.first == "toggleRule" })
        gate.complete(Unit)
        first.await()
        assertTrue(model.rules.single().disabled)
        assertEquals(listOf("rules", "toggleRule", "rules"), api.requests.map { it.first })
    }

    @Test
    fun `ambiguous toggle failure is not retried and blocks further writes until a successful refresh`() =
        runTest {
            val api = FakeClashApi()
            var reads = 0
            api.handler = { operation, _ ->
                if (operation == "toggleRule") {
                    throw ClashApiException("timeout", null, "Timeout")
                }
                if (++reads > 1) {
                    error("Offline")
                }
                rules(false)
            }
            val model = ClashRulesModel(api)
            model.refresh()
            val rule = model.rules.single()
            model.toggle(rule)
            assertTrue(model.uncertain[rule.uuid] == true)
            model.toggle(rule)
            assertEquals(1, api.requests.count { it.first == "toggleRule" })
            api.handler = { _, _ -> rules(true) }
            model.refresh()
            assertTrue(model.uncertain.isEmpty())
            assertTrue(model.rules.single().disabled)
        }

    @Test
    fun `unsupported UUID operations disable only their appropriate scope`() = runTest {
        for (status in listOf(404, 405, 501)) {
            val api = FakeClashApi().apply {
                handler = { operation, _ ->
                    if (operation == "toggleRule") {
                        throw ClashApiException("http", status, "Unsupported")
                    }
                    rules(false)
                }
            }
            val model = ClashRulesModel(api)
            model.refresh()
            val rule = model.rules.single()
            model.toggle(rule)
            assertEquals(status != 404, model.toggleUnsupported)
            assertEquals(status == 404, model.unavailable[rule.uuid] == true)
            model.refresh()
            model.toggle(rule)
            assertEquals(1, api.requests.count { it.first == "toggleRule" })
        }
    }

    @Test
    fun `rules without UUID support remain read only`() = runTest {
        val api = FakeClashApi().apply {
            handler = { _, _ ->
                Json.parseToJsonElement(
                    """[{"type":"MATCH","payload":"all","proxy":"DIRECT"}]""",
                )
            }
        }
        val model = ClashRulesModel(api)
        model.refresh()
        model.toggle(model.rules.single())
        assertEquals(listOf("rules"), api.requests.map { it.first })
    }

    @Test
    fun `optional resource failures are local and authentication failures remain retryable`() =
        runTest {
            val api = FakeClashApi().apply {
                handler = { _, _ -> throw ClashApiException("http", 404, "Not found") }
            }
            val model = ClashResourceModel(api) { api.request(ClashRequests.proxyProviders) }
            model.refresh()
            assertTrue(model.unsupported)
            api.handler = { _, _ -> throw ClashApiException("http", 401, "Unauthorized") }
            model.refresh()
            assertFalse(model.unsupported)
            api.handler = { _, _ -> Json.parseToJsonElement("[]") }
            model.refresh()
            assertEquals(emptyList(), model.data)
            assertEquals("", model.error)
        }

    @Test
    fun `query failures clear previous results and a later success restores them`() = runTest {
        val api = FakeClashApi()
        var fail = false
        var response = "first answer"
        val model = ClashResourceModel(api, clearDataOnError = true) {
            if (fail) error("Query failed")
            response
        }

        model.refresh()
        assertEquals("first answer", model.data)
        fail = true
        model.refresh()
        assertNull(model.data)
        assertEquals("Query failed", model.error)
        assertFalse(model.loading)

        fail = false
        response = "next answer"
        model.refresh()
        assertEquals("next answer", model.data)
        assertEquals("", model.error)
    }

    @Test
    fun `resource refresh failures retain data by default`() = runTest {
        var fail = false
        val model = ClashResourceModel(FakeClashApi()) {
            if (fail) error("Offline")
            "provider data"
        }
        model.refresh()
        fail = true
        model.refresh()
        assertEquals("provider data", model.data)
        assertEquals("Offline", model.error)
    }

    @Test
    fun `DNS model normalizes query parameters and clears stale answers on failure`() = runTest {
        val api = FakeClashApi()
        api.handler = { _, _ -> Json.parseToJsonElement("""{"Status":0,"Question":[],"Answer":[]}""") }
        val model = ClashDnsModel(api)

        model.query(" example.test ", " aaaa ")
        assertEquals("dns", api.requests.single().first)
        assertEquals(JsonPrimitive("example.test"), api.requests.single().second["name"])
        assertEquals(JsonPrimitive("AAAA"), api.requests.single().second["type"])
        assertEquals(0, model.result.state.data?.status)

        api.handler = { _, _ -> error("Offline") }
        model.query("other.test", "A")
        assertNull(model.result.state.data)
        assertEquals("Offline", model.result.state.error)
    }

    @Test
    fun `provider actions refresh their own resource and expose delay results`() = runTest {
        val api = FakeClashApi()
        api.handler = { operation, _ ->
            when (operation) {
                "proxyProviders", "ruleProviders" -> Json.parseToJsonElement("[]")
                "providerDelay" -> Json.parseToJsonElement("""{"delay":42}""")
                else -> JsonNull
            }
        }
        val model = ClashProvidersModel(api)
        model.updateProxyProvider("proxy source")
        model.healthCheck("proxy source")
        model.testDelay("proxy source", "node")
        model.updateRuleProvider("rule source")

        assertEquals(
            listOf(
                "updateProxyProvider", "proxyProviders",
                "healthCheck", "proxyProviders",
                "providerDelay", "proxyProviders",
                "updateRuleProvider", "ruleProviders",
            ),
            api.requests.map { it.first },
        )
        assertEquals(JsonPrimitive("proxy source"), api.requests[4].second["provider"])
        assertEquals(JsonPrimitive("node"), api.requests[4].second["name"])
        assertEquals(42, model.proxies.state.delayResults["delay-proxy source-node"])
        assertEquals(emptyList(), model.rules.state.data)
    }

    private fun rules(disabled: Boolean) = buildJsonArray {
        add(buildJsonObject {
            put("type", "Domain")
            put("payload", "example.test")
            put("proxy", "DIRECT")
            put("uuid", "rule-1")
            put("toggleable", true)
            if (disabled) {
                put("disabled", true)
            }
        })
    }
}
