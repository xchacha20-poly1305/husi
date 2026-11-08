package fr.husi.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.NatBehaviour
import fr.husi.core.NetworkQualityPhase
import fr.husi.core.StunPhase
import fr.husi.remote.RemoteState
import fr.husi.remote.flag
import fr.husi.remote.number
import fr.husi.remote.request
import fr.husi.remote.text
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
                remote.client
                    .stream(
                        "StartSTUNTest",
                        request("server" to input.server, "outboundTag" to input.outboundTag),
                    )
                    .collect { progress ->
                        if (progress.text("error").isNotEmpty()) {
                            uiEvent.emit(StunScreenUiEvent.Alert(progress.text("error")))
                        }
                        uiState.update {
                            it.copy(
                                isDoing = !progress.flag("isFinal"),
                                report =
                                    StunReport(
                                        phase = StunPhase.ofWire(progress.number("phase").toInt()),
                                        externalAddress =
                                            progress.text("externalAddr").ifEmpty { null },
                                        latencyMs =
                                            progress.number("latencyMs").toInt().takeIf { it > 0 },
                                        mapping =
                                            NatBehaviour.ofMapping(
                                                progress.number("natMapping").toInt(),
                                            ),
                                        filtering =
                                            NatBehaviour.ofFiltering(
                                                progress.number("natFiltering").toInt(),
                                            ),
                                        natTypeUnsupported = !progress.flag("natTypeSupported"),
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
                remote.client
                    .stream(
                        "StartNetworkQualityTest",
                        buildJsonObject {
                            put("configURL", input.configUrl)
                            put("outboundTag", input.outboundTag)
                            put("serial", input.serial)
                            put("http3", input.http3)
                            put("maxRuntimeSeconds", input.maxRuntimeSeconds)
                        },
                    )
                    .collect { progress ->
                        if (progress.text("error").isNotEmpty()) {
                            uiEvent.emit(
                                NetworkQualityScreenUiEvent.ErrorAlert(
                                    StringOrRes.Direct(progress.text("error")),
                                ),
                            )
                        }
                        uiState.update {
                            it.copy(
                                report =
                                    NetworkQualityReport(
                                        phase =
                                            NetworkQualityPhase.ofWire(
                                                progress.number("phase").toInt(),
                                            ),
                                        downloadCapacity = progress.number("downloadCapacity"),
                                        uploadCapacity = progress.number("uploadCapacity"),
                                        downloadRpm = progress.number("downloadRPM").toInt(),
                                        uploadRpm = progress.number("uploadRPM").toInt(),
                                        idleLatencyMs = progress.number("idleLatencyMs").toInt(),
                                        elapsedMs = progress.number("elapsedMs"),
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
