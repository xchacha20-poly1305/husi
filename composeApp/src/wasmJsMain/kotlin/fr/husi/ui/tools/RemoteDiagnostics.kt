package fr.husi.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.NatBehaviour
import fr.husi.core.NetworkQualityPhase
import fr.husi.core.StunPhase
import fr.husi.ktx.emptyAsNull
import fr.husi.proto.daemon.NetworkQualityTestRequest
import fr.husi.proto.daemon.STUNTestRequest
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.can_not_be_empty
import fr.husi.resources.done
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class RemoteStunPresenter(private val remote: RemoteState) : ViewModel(), StunPresenter {
    override val uiState: StateFlow<StunScreenUiState>
        field = MutableStateFlow(StunScreenUiState())
    override val uiEvent: SharedFlow<StunScreenUiEvent>
        field = MutableSharedFlow()
    private var job: Job? = null

    override fun setServer(value: String) {
        uiState.update { it.copy(server = value) }
    }

    override fun setOutboundTag(value: String) {
        uiState.update { it.copy(outboundTag = value) }
    }

    override fun cancel() {
        job?.cancel()
        uiState.update { it.copy(isDoing = false) }
    }

    override fun doTest(value: Boolean) {
        cancel()
        val input = uiState.value
        job = viewModelScope.launch {
            uiState.update { it.copy(isDoing = true, report = StunReport()) }
            try {
                remote.client.service
                    .startSTUNTest(STUNTestRequest(server = input.server, outboundTag = input.outboundTag))
                    .collect { progress ->
                        if (progress.error.isNotEmpty()) {
                            uiEvent.emit(StunScreenUiEvent.Alert(progress.error))
                        }
                        uiState.update {
                            it.copy(
                                isDoing = !progress.isFinal,
                                report =
                                    StunReport(
                                        phase = StunPhase.ofWire(progress.phase),
                                        externalAddress = progress.externalAddr.emptyAsNull(),
                                        latencyMs = progress.latencyMs.takeIf { it > 0 },
                                        mapping = NatBehaviour.ofMapping(progress.natMapping),
                                        filtering = NatBehaviour.ofFiltering(progress.natFiltering),
                                        natTypeUnsupported = !progress.natTypeSupported,
                                    ),
                            )
                        }
                    }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                uiEvent.emit(StunScreenUiEvent.Alert(failure.message.orEmpty()))
            } finally {
                uiState.update { it.copy(isDoing = false) }
            }
        }
    }
}

internal class RemoteNetworkQualityPresenter(private val remote: RemoteState) :
    ViewModel(), NetworkQualityPresenter {
    override val uiState: StateFlow<NetworkQualityScreenUiState>
        field = MutableStateFlow(NetworkQualityScreenUiState())
    override val uiEvent: SharedFlow<NetworkQualityScreenUiEvent>
        field = MutableSharedFlow()
    private var job: Job? = null

    override fun setConfigUrl(value: String?) {
        uiState.update {
            if (value.isNullOrBlank())
                it.copy(urlError = StringOrRes.Res(Res.string.can_not_be_empty))
            else it.copy(configUrl = value, urlError = null)
        }
    }

    override fun setSerial(value: Boolean) {
        uiState.update { it.copy(serial = value) }
    }

    override fun setHttp3(value: Boolean) {
        uiState.update { it.copy(http3 = value) }
    }

    override fun setMaxRuntimeSeconds(value: String?) {
        val seconds = value?.toIntOrNull()
        uiState.update {
            if (seconds == null)
                it.copy(maxRuntimeError = StringOrRes.Res(Res.string.can_not_be_empty))
            else it.copy(maxRuntimeSeconds = seconds, maxRuntimeError = null)
        }
    }

    override fun setOutboundTag(value: String) {
        uiState.update { it.copy(outboundTag = value) }
    }

    override fun cancel() {
        job?.cancel()
        uiState.update { it.copy(canTest = true) }
    }

    override fun doTest(value: Boolean) {
        cancel()
        val input = uiState.value
        job = viewModelScope.launch {
            uiState.update { it.copy(canTest = false, report = NetworkQualityReport()) }
            try {
                remote.client.service
                    .startNetworkQualityTest(
                        NetworkQualityTestRequest(
                            configURL = input.configUrl,
                            outboundTag = input.outboundTag,
                            serial = input.serial,
                            http3 = input.http3,
                            maxRuntimeSeconds = input.maxRuntimeSeconds,
                        ),
                    )
                    .collect { progress ->
                        if (progress.error.isNotEmpty()) {
                            uiEvent.emit(
                                NetworkQualityScreenUiEvent.ErrorAlert(
                                    StringOrRes.Direct(progress.error),
                                ),
                            )
                        }
                        uiState.update {
                            it.copy(
                                report =
                                    NetworkQualityReport(
                                        phase = NetworkQualityPhase.ofWire(progress.phase),
                                        downloadCapacity = progress.downloadCapacity,
                                        uploadCapacity = progress.uploadCapacity,
                                        downloadRpm = progress.downloadRPM,
                                        uploadRpm = progress.uploadRPM,
                                        idleLatencyMs = progress.idleLatencyMs,
                                        elapsedMs = progress.elapsedMs,
                                    ),
                            )
                        }
                    }
                uiEvent.emit(NetworkQualityScreenUiEvent.Snackbar(StringOrRes.Res(Res.string.done)))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                uiEvent.emit(
                    NetworkQualityScreenUiEvent.ErrorAlert(
                        StringOrRes.Direct(failure.message.orEmpty()),
                    ),
                )
            } finally {
                uiState.update { it.copy(canTest = true) }
            }
        }
    }
}
