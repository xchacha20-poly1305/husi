package fr.husi.tailscale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.ktx.readableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val TAILSCALE_PING_HISTORY_SIZE = 30

data class TailscalePingState(
    val running: Boolean = false,
    val hasResult: Boolean = false,
    val latencyMilliseconds: Double = 0.0,
    val direct: Boolean = false,
    val derpRegionCode: String = "",
    val endpoint: String = "",
    val peerRelay: String = "",
    val error: String = "",
    val latencyHistory: List<Float> = emptyList(),
)

data class TailscalePeerUiState(
    val endpoint: TailscaleEndpointState? = null,
    val peer: TailscalePeerState? = null,
    val ping: TailscalePingState = TailscalePingState(),
)

class TailscalePeerViewModel(
    private val endpointTag: String,
    private val peerId: String,
    repository: TailscaleStatusRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val uiState: StateFlow<TailscalePeerUiState>
        field = MutableStateFlow(stateFrom(repository.snapshot.value.endpoints))

    val closed: SharedFlow<Unit>
        field = MutableSharedFlow()

    val uiEvent: SharedFlow<TailscaleUiEvent>
        field = MutableSharedFlow()

    private val presence = TailscalePresence.peer(endpointTag, peerId)
    private var pingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                uiState.update { current ->
                    val next = stateFrom(snapshot.endpoints)
                    current.copy(endpoint = next.endpoint, peer = next.peer)
                }
                if (presence.shouldClose(snapshot)) {
                    closed.emit(Unit)
                }
            }
        }
    }

    fun startPing(ip: String) {
        pingJob?.cancel()
        uiState.update { it.copy(ping = TailscalePingState(running = true)) }
        pingJob = viewModelScope.launch(ioDispatcher) {
            try {
                presence.requireTarget().client.tailscalePing(endpointTag, ip).collect { response ->
                    if (!uiState.value.ping.running) return@collect
                    if (response.error.isNotEmpty()) {
                        uiState.update { state ->
                            state.copy(ping = state.ping.copy(error = response.error))
                        }
                        return@collect
                    }
                    if (!response.latencyMs.isFinite()) return@collect
                    uiState.update { state ->
                        val history = (state.ping.latencyHistory + response.latencyMs.toFloat())
                            .takeLast(TAILSCALE_PING_HISTORY_SIZE)
                        state.copy(
                            ping = state.ping.copy(
                                hasResult = true,
                                latencyMilliseconds = response.latencyMs,
                                direct = response.isDirect,
                                derpRegionCode = response.derpRegionCode,
                                endpoint = response.endpoint,
                                peerRelay = response.peerRelay,
                                error = "",
                                latencyHistory = history,
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.update { state ->
                    state.copy(ping = state.ping.copy(running = false, error = e.readableMessage))
                }
                return@launch
            }
            uiState.update { state -> state.copy(ping = state.ping.copy(running = false)) }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        uiState.update { state -> state.copy(ping = state.ping.copy(running = false)) }
    }

    fun logout() {
        viewModelScope.launch(ioDispatcher) {
            try {
                presence.requireTarget().client.tailscaleLogout(endpointTag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiEvent.emit(TailscaleUiEvent.Message(e.readableMessage))
            }
        }
    }

    override fun onCleared() {
        pingJob?.cancel()
        super.onCleared()
    }

    private fun stateFrom(endpoints: List<TailscaleEndpointState>): TailscalePeerUiState {
        val endpoint = endpoints.firstOrNull { it.endpointTag == endpointTag }
        return TailscalePeerUiState(
            endpoint = endpoint,
            peer = endpoint?.findPeer(peerId),
        )
    }
}
