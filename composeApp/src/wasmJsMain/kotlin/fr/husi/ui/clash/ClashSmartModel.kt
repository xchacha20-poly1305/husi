package fr.husi.ui.clash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.husi.remote.ClashApi
import fr.husi.remote.ClashApiException
import fr.husi.remote.ClashProxy
import fr.husi.remote.ClashRequests
import fr.husi.remote.ClashSmartNodeRank
import fr.husi.resources.Res
import fr.husi.resources.clash_unsupported
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ClashSmartGroupInfo(
    val name: String,
    val selected: String = "",
    val fixed: String = "",
    val expectedStatus: String = "",
    val emptyFallback: String = "",
    val policyPriority: String = "",
    val useLightGBM: Boolean? = null,
    val collectData: Boolean? = null,
    val sampleRate: Double? = null,
    val preferASN: Boolean? = null,
    val tolerance: Int? = null,
    val ranks: List<ClashSmartNodeRank> = emptyList(),
    val error: String = "",
)

internal data class ClashSmartState(
    val groups: List<ClashSmartGroupInfo> = emptyList(),
    val message: String = "",
    val loading: Boolean = false,
    val error: String = "",
    val unsupported: Boolean = false,
    val pending: Map<String, Boolean> = emptyMap(),
    val unavailable: Map<String, Boolean> = emptyMap(),
    val actionErrors: Map<String, String> = emptyMap(),
)

internal class ClashSmartModel(
    private val api: ClashApi,
    private val onProxiesChanged: suspend () -> Unit = {},
) {
    companion object {
        const val ACTION_FLUSH_ALL = "flush-all"
        const val ACTION_FLUSH_CONFIG = "flush-config"
        const val ACTION_UPGRADE_LGBM = "upgrade-lgbm"

        fun refreshGroupKey(name: String) = "weights-$name"

        fun clearFixedKey(name: String) = "clear-fixed-$name"

        fun normalizeConfigName(raw: String) = raw.trim()

        fun isValidConfigName(raw: String): Boolean {
            val name = normalizeConfigName(raw)
            return name.isNotEmpty() && name != "." && name != ".."
        }
    }

    val state: ClashSmartState
        get() = ClashSmartState(
            groups = groups,
            message = message,
            loading = loading,
            error = error,
            unsupported = unsupported,
            pending = pending.toMap(),
            unavailable = unavailable.toMap(),
            actionErrors = actionErrors.toMap(),
        )

    var groups by mutableStateOf(emptyList<ClashSmartGroupInfo>())
        private set

    var message by mutableStateOf("")
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf("")
        private set

    var unsupported by mutableStateOf(false)
        private set

    val pending = mutableStateMapOf<String, Boolean>()
    val unavailable = mutableStateMapOf<String, Boolean>()
    val actionErrors = mutableStateMapOf<String, String>()

    suspend fun refresh() {
        if (loading) return

        loading = true
        error = ""

        try {
            val proxies = api.request(ClashRequests.proxies)
            currentCoroutineContext().ensureActive()
            val smartProxies = proxies.filterValues { it.isSmartGroup }

            var weightsError = ""
            var weightsUnsupported = false
            val snapshot = try {
                api.request(ClashRequests.smartWeights)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                weightsUnsupported = failure is ClashApiException && failure.unsupported
                weightsError = failure.message.orEmpty()
                null
            }
            currentCoroutineContext().ensureActive()

            groups = buildList {
                val seen = hashSetOf<String>()
                for ((name, proxy) in smartProxies) {
                    seen += name
                    add(
                        buildGroupInfo(
                            name = name,
                            proxy = proxy,
                            ranks = snapshot?.weights?.get(name).orEmpty(),
                            groupError = snapshot?.errors?.get(name).orEmpty(),
                        ),
                    )
                }
                if (snapshot != null) {
                    val extraNames = linkedSetOf<String>()
                    extraNames.addAll(snapshot.weights.keys)
                    extraNames.addAll(snapshot.errors.keys)
                    for (name in extraNames) {
                        if (name in seen) continue
                        add(
                            buildGroupInfo(
                                name = name,
                                proxy = null,
                                ranks = snapshot.weights[name].orEmpty(),
                                groupError = snapshot.errors[name].orEmpty(),
                            ),
                        )
                    }
                }
            }
            message = snapshot?.message.orEmpty()
            unsupported = weightsUnsupported
            error = weightsError
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            unsupported = failure is ClashApiException && failure.unsupported
            error = failure.message.orEmpty()
        } finally {
            loading = false
        }
    }

    suspend fun refreshGroupWeights(name: String) {
        val key = refreshGroupKey(name)
        if (pending[key] == true || unavailable[key] == true) return

        pending[key] = true
        actionErrors.remove(key)

        try {
            val ranks = api.request(ClashRequests.smartGroupWeights(name))
            currentCoroutineContext().ensureActive()
            val updated = groups.any { it.name == name }
            groups = if (updated) {
                groups.map { group ->
                    if (group.name == name) {
                        group.copy(ranks = ranks, error = "")
                    } else {
                        group
                    }
                }
            } else {
                groups + ClashSmartGroupInfo(name = name, ranks = ranks)
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (failure is ClashApiException && failure.unsupported) {
                unavailable[key] = true
            }
            actionErrors[key] = failure.message.orEmpty()
        } finally {
            pending.remove(key)
        }
    }

    suspend fun clearFixed(name: String, onError: (StringOrRes) -> Unit = {}) {
        runAction(clearFixedKey(name), onError) {
            api.request(ClashRequests.clearFixed(name))
            currentCoroutineContext().ensureActive()
            onProxiesChanged()
            refresh()
        }
    }

    suspend fun flushAllCache(
        onSuccess: () -> Unit = {},
        onError: (StringOrRes) -> Unit = {},
    ) {
        runAction(ACTION_FLUSH_ALL, onError) {
            api.request(ClashRequests.flushSmartCache)
            currentCoroutineContext().ensureActive()
            onProxiesChanged()
            refresh()
            onSuccess()
        }
    }

    suspend fun flushConfigCache(
        rawConfig: String,
        onSuccess: (String) -> Unit = {},
        onError: (StringOrRes) -> Unit = {},
    ) {
        val config = normalizeConfigName(rawConfig)
        if (!isValidConfigName(config)) return

        runAction(ACTION_FLUSH_CONFIG, onError) {
            api.request(ClashRequests.flushSmartConfigCache(config))
            currentCoroutineContext().ensureActive()
            onProxiesChanged()
            refresh()
            onSuccess(config)
        }
    }

    suspend fun upgradeLgbm(
        onSuccess: () -> Unit = {},
        onError: (StringOrRes) -> Unit = {},
    ) {
        runAction(ACTION_UPGRADE_LGBM, onError) {
            val result = api.request(ClashRequests.upgradeLgbm)
            currentCoroutineContext().ensureActive()
            check(result.status == "ok") { "LightGBM update failed: ${result.status}" }
            onSuccess()
        }
    }

    private suspend fun runAction(
        key: String,
        onError: (StringOrRes) -> Unit,
        block: suspend () -> Unit,
    ) {
        if (pending[key] == true || unavailable[key] == true) return

        pending[key] = true
        actionErrors.remove(key)

        try {
            block()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (failure is ClashApiException && failure.unsupported) {
                unavailable[key] = true
                onError(StringOrRes.Res(Res.string.clash_unsupported))
            } else {
                val failureMessage = failure.message?.takeIf { it.isNotBlank() } ?: failure.toString()
                actionErrors[key] = failureMessage
                onError(StringOrRes.Direct(failureMessage))
            }
        } finally {
            pending.remove(key)
        }
    }

    private fun buildGroupInfo(
        name: String,
        proxy: ClashProxy?,
        ranks: List<ClashSmartNodeRank>,
        groupError: String,
    ) = ClashSmartGroupInfo(
        name = name,
        selected = proxy?.selected.orEmpty(),
        fixed = proxy?.fixed.orEmpty(),
        expectedStatus = proxy?.expectedStatus.orEmpty(),
        emptyFallback = proxy?.emptyFallback.orEmpty(),
        policyPriority = proxy?.policyPriority.orEmpty(),
        useLightGBM = proxy?.useLightGBM,
        collectData = proxy?.collectData,
        sampleRate = proxy?.sampleRate,
        preferASN = proxy?.preferASN,
        tolerance = proxy?.tolerance,
        ranks = ranks,
        error = groupError,
    )
}
