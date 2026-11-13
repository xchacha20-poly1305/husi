package fr.husi.ui.clash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.husi.remote.ClashApi
import fr.husi.remote.ClashApiException
import fr.husi.remote.ClashRequest
import fr.husi.remote.ClashRequests
import fr.husi.remote.ClashRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ClashRulesState(
    val rules: List<ClashRule> = emptyList(),
    val loading: Boolean = false,
    val error: String = "",
    val unsupported: Boolean = false,
    val toggleUnsupported: Boolean = false,
    val pending: Map<String, Boolean> = emptyMap(),
    val unavailable: Map<String, Boolean> = emptyMap(),
    val uncertain: Map<String, Boolean> = emptyMap(),
)

internal data class ClashResourceState<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String = "",
    val unsupported: Boolean = false,
    val pending: Map<String, Boolean> = emptyMap(),
    val unavailable: Map<String, Boolean> = emptyMap(),
    val delayResults: Map<String, Int> = emptyMap(),
)

internal class ClashRulesModel(private val api: ClashApi) {
    val state: ClashRulesState
        get() = ClashRulesState(
            rules, loading, error, unsupported, toggleUnsupported,
            pending.toMap(), unavailable.toMap(), uncertain.toMap(),
        )

    var rules by mutableStateOf(emptyList<ClashRule>())
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf("")
        private set

    var unsupported by mutableStateOf(false)
        private set

    var toggleUnsupported by mutableStateOf(false)
        private set

    val pending = mutableStateMapOf<String, Boolean>()
    val unavailable = mutableStateMapOf<String, Boolean>()
    val uncertain = mutableStateMapOf<String, Boolean>()

    suspend fun refresh() {
        if (loading) return

        loading = true
        error = ""

        try {
            readRules()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure

            unsupported = failure is ClashApiException && failure.unsupported
            error = failure.message.orEmpty()
        } finally {
            loading = false
        }
    }

    private suspend fun readRules() {
        val result = api.request(ClashRequests.rules)
        currentCoroutineContext().ensureActive()

        rules = result
        uncertain.clear()
        unsupported = false
    }

    suspend fun toggle(rule: ClashRule) {
        val uuid = rule.uuid

        if (
            unsupported
            || !rule.toggleable
            || uuid.isEmpty()
            || pending[uuid] == true
            || unavailable[uuid] == true
            || uncertain[uuid] == true
            || toggleUnsupported
        ) return

        pending[uuid] = true
        error = ""

        try {
            try {
                // This endpoint flips state. Never retry it or optimistically set a target value.
                api.request(ClashRequests.toggleRule(uuid))
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    uncertain[uuid] = true
                    throw failure
                }
                error = failure.message.orEmpty()

                when ((failure as? ClashApiException)?.status) {
                    404 -> {
                        unavailable[uuid] = true
                        return
                    }

                    405,
                    501,
                        -> {
                        toggleUnsupported = true
                        return
                    }
                }
            }
            uncertain[uuid] = true

            try {
                readRules()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = listOf(error, failure.message.orEmpty())
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            }
        } finally {
            pending.remove(uuid)
        }
    }
}

/** A page owns its read state; optional endpoints never fail the dashboard session. */
internal class ClashResourceModel<T>(
    private val api: ClashApi,
    private val clearDataOnError: Boolean = false,
    private val read: suspend () -> T,
) {
    val state: ClashResourceState<T>
        get() = ClashResourceState(
            data, loading, error, unsupported,
            pending.toMap(), unavailable.toMap(), delayResults.toMap(),
        )

    var data by mutableStateOf<T?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf("")
        private set

    var unsupported by mutableStateOf(false)
        private set

    val pending = mutableStateMapOf<String, Boolean>()
    val unavailable = mutableStateMapOf<String, Boolean>()
    val delayResults = mutableStateMapOf<String, Int>()

    suspend fun refresh() {
        if (loading) return

        loading = true
        error = ""

        try {
            val result = read()
            currentCoroutineContext().ensureActive()

            data = result
            unsupported = false
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure

            if (clearDataOnError) data = null
            unsupported = failure is ClashApiException && failure.unsupported
            error = failure.message.orEmpty()
        } finally {
            loading = false
        }
    }

    suspend fun <R> act(
        key: String,
        request: ClashRequest<R>,
        receive: (R) -> Unit = {},
    ) {
        if (pending[key] == true || unavailable[key] == true) return

        pending[key] = true
        error = ""

        try {
            val result = api.request(request)
            currentCoroutineContext().ensureActive()

            receive(result)
            refresh()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (failure is ClashApiException && failure.unsupported) {
                unavailable[key] = true
            }
            error = failure.message.orEmpty()
        } finally {
            pending.remove(key)
        }
    }
}

internal class ClashProvidersModel(api: ClashApi) {
    val proxies = ClashResourceModel(api) { api.request(ClashRequests.proxyProviders) }
    val rules = ClashResourceModel(api) { api.request(ClashRequests.ruleProviders) }

    suspend fun updateProxyProvider(name: String) {
        proxies.act("update-$name", ClashRequests.updateProxyProvider(name))
    }

    suspend fun healthCheck(name: String) {
        proxies.act("health-$name", ClashRequests.healthCheck(name))
    }

    suspend fun testDelay(name: String, tag: String) {
        val key = "delay-$name-$tag"
        proxies.act(key, ClashRequests.providerDelay(name, tag)) {
            proxies.delayResults[key] = it.delay
        }
    }

    suspend fun updateRuleProvider(name: String) {
        rules.act(name, ClashRequests.updateRuleProvider(name))
    }
}

internal class ClashDnsModel(api: ClashApi) {
    private var name = ""
    private var type = "A"

    val result = ClashResourceModel(api, clearDataOnError = true) {
        api.request(ClashRequests.dns(name, type))
    }

    suspend fun query(name: String, type: String) {
        if (result.loading) return
        this.name = name.trim()
        this.type = type.trim().uppercase()
        result.refresh()
    }
}
