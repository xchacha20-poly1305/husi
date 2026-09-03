package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.NETWORK_QUALITY_CONFIG_URL
import fr.husi.core.CoreClient
import fr.husi.core.NetworkQualityPhase
import fr.husi.core.failure
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.readableMessage
import fr.husi.resources.Res
import fr.husi.resources.can_not_be_empty
import fr.husi.resources.done
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.CancellationException
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

@Immutable
internal data class NetworkQualityScreenUiState(
    val configUrl: String = NETWORK_QUALITY_CONFIG_URL,
    val urlError: StringOrRes? = null,
    val serial: Boolean = false,
    val maxRuntimeSeconds: Int = DEFAULT_MAX_RUNTIME_SECONDS,
    val maxRuntimeError: StringOrRes? = null,
    val http3: Boolean = false,
    val outboundTag: String = "",
    val canTest: Boolean = true,
    val report: NetworkQualityReport? = null,
)

@Immutable
internal data class NetworkQualityReport(
    val phase: NetworkQualityPhase = NetworkQualityPhase.Idle,
    val downloadCapacity: Long = 0L,
    val uploadCapacity: Long = 0L,
    val downloadRpm: Int = 0,
    val uploadRpm: Int = 0,
    val idleLatencyMs: Int = 0,
    val elapsedMs: Long = 0L,
)

@Immutable
internal sealed interface NetworkQualityScreenUiEvent {
    class Snackbar(val message: StringOrRes) : NetworkQualityScreenUiEvent
    class ErrorAlert(val message: StringOrRes) : NetworkQualityScreenUiEvent
}

private const val DEFAULT_MAX_RUNTIME_SECONDS = 30

@Stable
internal class NetworkQualityScreenViewModel(
    coreClient: CoreClient? = null,
    private val remoteControl: RemoteControlManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val coreClientOverride = coreClient

    // Resolved per test rather than held, so switching the remote target
    // between runs sends the next one to the newly chosen endpoint.
    private val coreClient: CoreClient
        get() = coreClientOverride
            ?: remoteControl?.activeClient?.value
            ?: GlobalContext.get().get()

    val uiState: StateFlow<NetworkQualityScreenUiState>
        field = MutableStateFlow(NetworkQualityScreenUiState())

    val uiEvent: SharedFlow<NetworkQualityScreenUiEvent>
        field = MutableSharedFlow<NetworkQualityScreenUiEvent>()

    init {
        initialize()
    }

    fun initialize() {
        uiState.update {
            it.copy(
                configUrl = DataStore.networkQualityConfigUrl.getBlocking().blankAsNull()
                    ?: NETWORK_QUALITY_CONFIG_URL,
                serial = DataStore.networkQualitySerial.getBlocking(),
                maxRuntimeSeconds = DataStore.networkQualityMaxRuntime.getBlocking(),
                http3 = DataStore.networkQualityHttp3.getBlocking(),
            )
        }
    }

    private var job: Job? = null

    fun doTest(serviceRunning: Boolean) {
        cancel()
        job = viewModelScope.launch(ioDispatcher) {
            uiState.update {
                it.copy(canTest = false, report = NetworkQualityReport())
            }
            val state = uiState.value
            try {
                val progresses = if (serviceRunning) {
                    coreClient.networkQualityTest(
                        configUrl = state.configUrl,
                        outboundTag = state.outboundTag,
                        serial = state.serial,
                        maxRuntimeSeconds = state.maxRuntimeSeconds,
                        http3 = state.http3,
                    )
                } else {
                    coreClient.standaloneNetworkQualityTest(
                        configUrl = state.configUrl,
                        serial = state.serial,
                        maxRuntimeSeconds = state.maxRuntimeSeconds,
                        http3 = state.http3,
                    )
                }
                var failed = false
                progresses.catch { e ->
                    if (e is CancellationException) throw e
                    Logs.e(e)
                    failed = true
                    uiEvent.emit(
                        NetworkQualityScreenUiEvent.ErrorAlert(StringOrRes.Direct(e.readableMessage)),
                    )
                }.collect { progress ->
                    val failure = progress.failure
                    if (failure != null) {
                        failed = true
                        uiEvent.emit(
                            NetworkQualityScreenUiEvent.ErrorAlert(StringOrRes.Direct(failure)),
                        )
                        return@collect
                    }
                    uiState.update { current ->
                        current.copy(
                            report = NetworkQualityReport(
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
                if (!failed) {
                    uiEvent.emit(NetworkQualityScreenUiEvent.Snackbar(StringOrRes.Res(Res.string.done)))
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Logs.e(e)
                uiEvent.emit(
                    NetworkQualityScreenUiEvent.ErrorAlert(StringOrRes.Direct(e.readableMessage)),
                )
            } finally {
                uiState.update { it.copy(canTest = true) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        uiState.update { it.copy(canTest = true) }
    }

    override fun onCleared() {
        val state = uiState.value
        DataStore.networkQualityConfigUrl.setBlocking(state.configUrl)
        DataStore.networkQualitySerial.setBlocking(state.serial)
        DataStore.networkQualityMaxRuntime.setBlocking(state.maxRuntimeSeconds)
        DataStore.networkQualityHttp3.setBlocking(state.http3)
        cancel()
        super.onCleared()
    }

    fun setConfigUrl(url: String?) = viewModelScope.launch {
        uiState.update { state ->
            if (url?.blankAsNull() == null) {
                state.copy(urlError = StringOrRes.Res(Res.string.can_not_be_empty))
            } else {
                state.copy(configUrl = url, urlError = null)
            }
        }
    }

    fun setSerial(serial: Boolean) = viewModelScope.launch {
        uiState.update { it.copy(serial = serial) }
    }

    fun setHttp3(http3: Boolean) = viewModelScope.launch {
        uiState.update { it.copy(http3 = http3) }
    }

    fun setMaxRuntimeSeconds(raw: String?) = viewModelScope.launch {
        uiState.update { state ->
            val seconds = raw?.blankAsNull()?.toIntOrNull()
            if (seconds == null) {
                state.copy(maxRuntimeError = StringOrRes.Res(Res.string.can_not_be_empty))
            } else {
                state.copy(maxRuntimeSeconds = seconds, maxRuntimeError = null)
            }
        }
    }

    fun setOutboundTag(outboundTag: String) {
        uiState.update { it.copy(outboundTag = outboundTag) }
    }
}
