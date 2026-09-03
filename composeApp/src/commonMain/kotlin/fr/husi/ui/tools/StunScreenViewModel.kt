package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.CoreClient
import fr.husi.core.NatBehaviour
import fr.husi.core.StunPhase
import fr.husi.core.failure
import fr.husi.core.remote.RemoteControlManager
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.readableMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

private const val DEFAULT_STUN_SERVER = "stun.voipgate.com:3478"

@Immutable
internal data class StunScreenUiState(
    val server: String = DEFAULT_STUN_SERVER,
    /** Empty means the service's default outbound; ignored when it is not running. */
    val outboundTag: String = "",
    val isDoing: Boolean = false,
    val report: StunReport? = null,
)

@Immutable
internal data class StunReport(
    val phase: StunPhase = StunPhase.Binding,
    val externalAddress: String? = null,
    val latencyMs: Int? = null,
    val mapping: NatBehaviour = NatBehaviour.Unknown,
    val filtering: NatBehaviour = NatBehaviour.Unknown,
    val natTypeUnsupported: Boolean = true,
)

@Immutable
internal sealed interface StunScreenUiEvent {
    data class Alert(val message: String) : StunScreenUiEvent
}

@Stable
internal class StunScreenViewModel(
    coreClient: CoreClient? = null,
    private val remoteControl: RemoteControlManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val coreClientOverride = coreClient

    private val coreClient: CoreClient
        get() = coreClientOverride
            ?: remoteControl?.activeClient?.value
            ?: GlobalContext.get().get()

    val uiState: StateFlow<StunScreenUiState>
        field = MutableStateFlow(StunScreenUiState())
    val uiEvent: SharedFlow<StunScreenUiEvent>
        field = MutableSharedFlow()
    private var testJob: Job? = null

    override fun onCleared() {
        testJob?.cancel()
        super.onCleared()
    }

    /**
     * [serviceRunning] is the *target's* service, not this device's: a remote
     * endpoint has its own. The caller collects it, so this stays testable
     * without a live RemoteControlManager.
     */
    fun doTest(serviceRunning: Boolean) {
        testJob?.cancel()
        testJob = viewModelScope.launch(ioDispatcher) {
            var server = ""
            var outboundTag = ""
            uiState.update { state ->
                state.copy(
                    isDoing = true,
                    report = StunReport(),
                ).also {
                    server = it.server
                    outboundTag = it.outboundTag
                }
            }
            val progresses = if (serviceRunning) {
                coreClient.stunTest(server, outboundTag)
            } else {
                coreClient.standaloneStunTest(server)
            }
            progresses
                .catch { e ->
                    fail(e.readableMessage)
                    Logs.e(e)
                }
                .collect { progress ->
                    val failure = progress.failure
                    if (failure != null) {
                        fail(failure)
                        return@collect
                    }
                    uiState.update { state ->
                        state.copy(
                            isDoing = !progress.isFinal,
                            report = StunReport(
                                phase = StunPhase.ofWire(progress.phase),
                                externalAddress = progress.externalAddr.blankAsNull(),
                                latencyMs = progress.latencyMs.takeIf { it > 0 },
                                mapping = NatBehaviour.ofMapping(progress.natMapping),
                                filtering = NatBehaviour.ofFiltering(progress.natFiltering),
                                natTypeUnsupported = !progress.natTypeSupported,
                            ),
                        )
                    }
                }
        }
    }

    private suspend fun fail(message: String) {
        uiState.update { state ->
            val report = state.report
            state.copy(
                isDoing = false,
                report = report?.copy(
                    externalAddress = report.externalAddress ?: "-",
                    latencyMs = report.latencyMs ?: -1,
                ),
            )
        }
        uiEvent.emit(StunScreenUiEvent.Alert(message))
    }

    fun setServer(server: String) {
        uiState.update { it.copy(server = server) }
    }

    fun setOutboundTag(outboundTag: String) {
        uiState.update { it.copy(outboundTag = outboundTag) }
    }

    fun cancel() {
        testJob?.cancel()
        testJob = null
        uiState.update { it.copy(isDoing = false) }
    }
}
