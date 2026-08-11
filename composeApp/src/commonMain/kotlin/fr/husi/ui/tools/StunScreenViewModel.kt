package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.currentSocks5
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.readableMessage
import fr.husi.proto.v1.NATFiltering
import fr.husi.proto.v1.NATMapping
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

@Immutable
internal data class StunScreenUiState(
    val server: String = "stun.voipgate.com:3478",
    val proxy: String = "",
    val isDoing: Boolean = false,
    val report: StunReport? = null,
)

@Immutable
internal data class StunReport(
    val externalAddress: String? = null,
    val latencyMs: Int? = null,
    val mapping: NATMapping = NATMapping.NAT_MAPPING_UNSPECIFIED,
    val filtering: NATFiltering = NATFiltering.NAT_FILTERING_UNSPECIFIED,
    val mappingDisplay: String = "",
    val filteringDisplay: String = "",
    val natTypeUnsupported: Boolean = true,
)

@Immutable
internal sealed interface StunScreenUiEvent {
    data class Alert(val message: String) : StunScreenUiEvent
}

@Stable
internal class StunScreenViewModel(
    private val coreClient: CoreClient = GlobalContext.get().get(),
) : ViewModel() {

    val uiState: StateFlow<StunScreenUiState>
        field = MutableStateFlow(StunScreenUiState())
    val uiEvent: SharedFlow<StunScreenUiEvent>
        field = MutableSharedFlow()
    private var testJob: Job? = null

    init {
        initialize()
    }

    override fun onCleared() {
        testJob?.cancel()
        super.onCleared()
    }

    fun initialize() {
        uiState.update {
            it.copy(proxy = currentSocks5()?.string.orEmpty())
        }
    }

    fun doTest() {
        testJob?.cancel()
        testJob = viewModelScope.launch(Dispatchers.IO) {
            var server = ""
            var proxy = ""
            uiState.update { state ->
                state.copy(
                    isDoing = true,
                    report = StunReport(),
                ).also {
                    server = it.server
                    proxy = it.proxy
                }
            }
            coreClient.stunTest(server, proxy)
                .catch { e ->
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
                    uiEvent.emit(StunScreenUiEvent.Alert(e.readableMessage))
                    onIoDispatcher { Logs.e(e) }
                }
                .collect { response ->
                    uiState.update { state ->
                        state.copy(
                            isDoing = !response.done,
                            report = StunReport(
                                externalAddress = response.externalAddress.blankAsNull(),
                                latencyMs = response.latencyMs.takeIf { it > 0 },
                                mapping = response.mapping,
                                filtering = response.filtering,
                                mappingDisplay = response.mappingDisplay,
                                filteringDisplay = response.filteringDisplay,
                                natTypeUnsupported = !response.natTypeSupported,
                            ),
                        )
                    }
                }
        }
    }

    fun setServer(server: String) {
        uiState.update { it.copy(server = server) }
    }

    fun setProxy(proxy: String) {
        uiState.update { it.copy(proxy = proxy) }
    }

    fun cancel() {
        testJob?.cancel()
        testJob = null
        uiState.update { it.copy(isDoing = false) }
    }
}
