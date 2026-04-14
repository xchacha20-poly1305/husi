package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.currentSocks5
import fr.husi.ktx.onIoDispatcher
import fr.husi.libcore.STUNTestHandler
import fr.husi.libcore.STUNTestReport
import fr.husi.libcore.StunTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val natMapping: Int = 0,
    val natFiltering: Int = 0,
    val natTypeUnsupported: Boolean = true,
)

@Immutable
internal sealed interface StunScreenUiEvent {
    data class Alert(val message: String) : StunScreenUiEvent
}

@Stable
internal class StunScreenViewModel : ViewModel() {

    val uiState: StateFlow<StunScreenUiState>
        field = MutableStateFlow(StunScreenUiState())
    val uiEvent: SharedFlow<StunScreenUiEvent>
        field = MutableSharedFlow()
    private val tester = StunTester()

    init {
        initialize()
    }

    override fun onCleared() {
        tester.cancel()
        super.onCleared()
    }

    fun initialize() {
        uiState.update {
            it.copy(proxy = currentSocks5()?.string.orEmpty())
        }
    }

    fun doTest() = viewModelScope.launch(Dispatchers.IO) {
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
        tester.start(server, proxy, handler)
    }

    private val handler = object : STUNTestHandler {
        override fun onError(message: String) {
            tester.cancel()
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
            viewModelScope.launch {
                uiEvent.emit(StunScreenUiEvent.Alert(message))
                onIoDispatcher {
                    Logs.e(message)
                }
            }
        }

        override fun onReport(report: STUNTestReport, done: Boolean) {
            if (done) {
                tester.cancel()
            }
            uiState.update { state ->
                state.copy(
                    isDoing = !done,
                    report = StunReport(
                        externalAddress = report.externalAddr.blankAsNull(),
                        latencyMs = report.latencyMs.takeIf { it > 0 },
                        natMapping = report.natMapping,
                        natFiltering = report.natFiltering,
                        natTypeUnsupported = !report.natTypeSupported,
                    ),
                )
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
        tester.cancel()
        uiState.update { it.copy(isDoing = false) }
    }
}
