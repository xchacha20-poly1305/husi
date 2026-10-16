package fr.husi.vpn

import fr.husi.core.CoreClient
import fr.husi.core.remote.CoreTarget
import fr.husi.core.remote.requireRunning
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val VPN_ENDPOINT_STATE_CONNECTING = "connecting"
const val VPN_ENDPOINT_STATE_AUTH_PENDING = "auth-pending"
const val VPN_ENDPOINT_STATE_CONNECTED = "connected"
const val VPN_ENDPOINT_STATE_ERROR = "error"

const val OPENCONNECT_STATE_CONNECTING = VPN_ENDPOINT_STATE_CONNECTING
const val OPENCONNECT_STATE_AUTH_PENDING = VPN_ENDPOINT_STATE_AUTH_PENDING
const val OPENCONNECT_STATE_CONNECTED = VPN_ENDPOINT_STATE_CONNECTED
const val OPENCONNECT_STATE_ERROR = VPN_ENDPOINT_STATE_ERROR

const val OPENVPN_STATE_CONNECTING = VPN_ENDPOINT_STATE_CONNECTING
const val OPENVPN_STATE_AUTH_PENDING = VPN_ENDPOINT_STATE_AUTH_PENDING
const val OPENVPN_STATE_CONNECTED = VPN_ENDPOINT_STATE_CONNECTED
const val OPENVPN_STATE_ERROR = VPN_ENDPOINT_STATE_ERROR

data class PendingVpnAuth<C>(
    val endpointTag: String,
    val challenge: C,
)

data class VpnAuthPendingNotice(
    val endpointTag: String,
    val challengeId: String,
)

class VpnAuthSession<E, C>(
    private val targets: StateFlow<CoreTarget?>,
    private val subscribe: (CoreClient) -> Flow<List<E>>,
    private val pendingOf: (E) -> PendingVpnAuth<C>?,
    private val challengeId: (C) -> String,
    private val logLabel: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var subscriptionJob: Job? = null

    val endpoints: StateFlow<List<E>>
        field = MutableStateFlow(emptyList())

    private val dismissedChallenges = MutableStateFlow<Set<String>>(emptySet())

    val pendingDialogAuth: StateFlow<PendingVpnAuth<C>?> =
        combine(endpoints, dismissedChallenges) { endpointList, dismissed ->
            endpointList.firstNotNullOfOrNull { endpoint ->
                val pending = pendingOf(endpoint) ?: return@firstNotNullOfOrNull null
                pending.takeUnless {
                    vpnAuthChallengeKey(it.endpointTag, challengeId(it.challenge)) in dismissed
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            targets.collect { target ->
                stop()
                if (target != null) start(target.client)
            }
        }
    }

    private fun start(client: CoreClient) {
        subscriptionJob = scope.launch {
            try {
                subscribe(client).collect { update ->
                    endpoints.value = update
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("subscribe $logLabel status", e)
            }
        }
    }

    private fun stop() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        endpoints.value = emptyList()
        dismissedChallenges.value = emptySet()
    }

    fun dismissDialog(endpointTag: String, challengeId: String) {
        dismissedChallenges.update { it + vpnAuthChallengeKey(endpointTag, challengeId) }
    }

    suspend fun perform(action: String, block: suspend (CoreTarget) -> Unit): String? =
        withContext(dispatcher) {
            try {
                block(targets.requireRunning())
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w(action, e)
                e.readableMessage
            }
        }
}

fun vpnAuthChallengeKey(endpointTag: String, challengeId: String): String = "$endpointTag\n$challengeId"

fun <T> firstVpnAuthPending(
    endpoints: Iterable<T>,
    state: (T) -> String,
    challengeId: (T) -> String?,
    tag: (T) -> String,
): VpnAuthPendingNotice? {
    for (endpoint in endpoints) {
        if (state(endpoint) != VPN_ENDPOINT_STATE_AUTH_PENDING) continue
        val id = challengeId(endpoint) ?: continue
        return VpnAuthPendingNotice(tag(endpoint), id)
    }
    return null
}
