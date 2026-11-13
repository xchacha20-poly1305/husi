package fr.husi.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

internal val clashJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal class ClashRequest<T>(
    internal val operation: String,
    internal val input: Map<String, String> = emptyMap(),
    internal val decode: (JsonElement) -> T,
)

internal class ClashStream<T>(
    val kind: String,
    internal val decode: (JsonElement) -> T,
)

internal object ClashRequests {
    val version = request<ClashVersion>("version")

    val proxies = request<Map<String, ClashProxy>>("proxies")

    val config = request<ClashConfig>("config")

    val rules = request<List<ClashRule>>("rules")

    val proxyProviders = request<List<ClashProxyProvider>>("proxyProviders")

    val ruleProviders = request<List<ClashRuleProvider>>("ruleProviders")

    val closeConnections = command("closeConnections")

    fun select(group: String, name: String) = command("select", "group" to group, "name" to name)

    fun delay(name: String) = request<ClashDelay>("delay", "name" to name)

    fun closeConnection(id: String) = command("closeConnection", "id" to id)

    fun setMode(mode: String) = command("setMode", "mode" to mode)

    fun toggleRule(uuid: String) = command("toggleRule", "uuid" to uuid)

    fun updateProxyProvider(name: String) = command("updateProxyProvider", "name" to name)

    fun healthCheck(name: String) = command("healthCheck", "name" to name)

    fun providerDelay(provider: String, name: String): ClashRequest<ClashDelay> {
        return request<ClashDelay>("providerDelay", "provider" to provider, "name" to name)
    }

    fun updateRuleProvider(name: String) = command("updateRuleProvider", "name" to name)

    fun dns(name: String, type: String) = request<ClashDnsResponse>("dns", "name" to name, "type" to type)

    private inline fun <reified T> request(operation: String, vararg input: Pair<String, String>): ClashRequest<T> {
        return ClashRequest(operation, input.toMap()) {
            clashJson.decodeFromJsonElement<T>(it)
        }
    }

    private fun command(operation: String, vararg input: Pair<String, String>): ClashRequest<Unit> {
        return ClashRequest(operation, input.toMap()) {}
    }
}

internal object ClashStreams {
    val logs = stream<ClashLog>("logs")

    val traffic = stream<ClashTraffic>("traffic")

    val memory = stream<ClashMemory>("memory")

    val connections = stream<ClashConnections>("connections")

    private inline fun <reified T> stream(kind: String) = ClashStream(kind) {
        clashJson.decodeFromJsonElement<T>(it)
    }
}
