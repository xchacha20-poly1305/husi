package fr.husi.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.NETWORK_QUALITY_CONFIG_URL
import fr.husi.core.NatBehaviour
import fr.husi.core.NetworkQualityPhase
import fr.husi.core.StunPhase
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
                            Logs.w("STUN test: ${progress.error}")
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
                Logs.w("STUN test failed", failure)
            } finally {
                uiState.update { it.copy(isDoing = false) }
            }
        }
    }
}

internal class RemoteNetworkQualityPresenter(private val remote: RemoteState) :
    ViewModel(), NetworkQualityPresenter {
    private val testState = MutableStateFlow(NetworkQualityScreenUiState())
    override val uiState = combine(
        testState,
        DataStore.networkQualityConfigUrl.flow(),
        DataStore.networkQualitySerial.flow(),
        DataStore.networkQualityHttp3.flow(),
        DataStore.networkQualityMaxRuntime.flow(),
    ) { state, configUrl, serial, http3, maxRuntimeSeconds ->
        state.copy(
            configUrl = configUrl.ifBlank { NETWORK_QUALITY_CONFIG_URL },
            serial = serial,
            http3 = http3,
            maxRuntimeSeconds = maxRuntimeSeconds,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NetworkQualityScreenUiState())
    override val uiEvent: SharedFlow<NetworkQualityScreenUiEvent>
        field = MutableSharedFlow()
    private var job: Job? = null

    override fun setConfigUrl(value: String?) {
        if (value.isNullOrBlank()) {
            testState.update { it.copy(urlError = StringOrRes.Res(Res.string.can_not_be_empty)) }
            return
        }
        testState.update { it.copy(urlError = null) }
        viewModelScope.launch { DataStore.networkQualityConfigUrl.set(value) }
    }

    override fun setSerial(value: Boolean) {
        viewModelScope.launch { DataStore.networkQualitySerial.set(value) }
    }

    override fun setHttp3(value: Boolean) {
        viewModelScope.launch { DataStore.networkQualityHttp3.set(value) }
    }

    override fun setMaxRuntimeSeconds(value: String?) {
        val seconds = value?.toIntOrNull()
        if (seconds == null) {
            testState.update { it.copy(maxRuntimeError = StringOrRes.Res(Res.string.can_not_be_empty)) }
            return
        }
        testState.update { it.copy(maxRuntimeError = null) }
        viewModelScope.launch { DataStore.networkQualityMaxRuntime.set(seconds) }
    }

    override fun setOutboundTag(value: String) {
        testState.update { it.copy(outboundTag = value) }
    }

    override fun cancel() {
        job?.cancel()
        testState.update { it.copy(canTest = true) }
    }

    override fun doTest(value: Boolean) {
        cancel()
        job = viewModelScope.launch {
            val input = testState.value
            testState.update { it.copy(canTest = false, report = NetworkQualityReport()) }
            try {
                remote.client.service
                    .startNetworkQualityTest(
                        NetworkQualityTestRequest(
                            configURL =
                                DataStore.networkQualityConfigUrl.get().ifBlank {
                                    NETWORK_QUALITY_CONFIG_URL
                                },
                            outboundTag = input.outboundTag,
                            serial = DataStore.networkQualitySerial.get(),
                            http3 = DataStore.networkQualityHttp3.get(),
                            maxRuntimeSeconds = DataStore.networkQualityMaxRuntime.get(),
                        ),
                    )
                    .collect { progress ->
                        if (progress.error.isNotEmpty()) {
                            Logs.w("Network quality test: ${progress.error}")
                        }
                        testState.update {
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
                Logs.w("Network quality test failed", failure)
            } finally {
                testState.update { it.copy(canTest = true) }
            }
        }
    }
}
