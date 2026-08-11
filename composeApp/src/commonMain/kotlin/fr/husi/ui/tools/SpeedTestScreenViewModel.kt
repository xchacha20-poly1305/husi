package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.SPEED_TEST_UPLOAD_URL
import fr.husi.SPEED_TEST_URL
import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.currentSocks5
import fr.husi.ktx.readableMessage
import fr.husi.proto.v1.SpeedTestMode as ProtoSpeedTestMode
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
internal data class SpeedTestScreenUiState(
    val progress: Float? = null,
    val speed: Long = 0L,
    val canTest: Boolean = true,
    val mode: SpeedTestScreenViewModel.SpeedTestMode = SpeedTestScreenViewModel.SpeedTestMode.Download,
    val downloadURL: String = SPEED_TEST_URL,
    val uploadURL: String = SPEED_TEST_UPLOAD_URL,
    val urlError: StringOrRes? = null,
    val timeout: Int = 20000,
    val timeoutError: StringOrRes? = null,
    val uploadLength: Long = 10 * 1024 * 1024,
    val uploadLengthError: StringOrRes? = null,
)

@Immutable
internal sealed interface SpeedTestScreenUiEvent {
    class Snackbar(val message: StringOrRes) : SpeedTestScreenUiEvent
    class ErrorAlert(val message: StringOrRes) : SpeedTestScreenUiEvent
}

@Stable
internal class SpeedTestScreenViewModel(
    private val coreClient: CoreClient = GlobalContext.get().get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val userAgent: String = USER_AGENT,
) : ViewModel() {
    val uiState: StateFlow<SpeedTestScreenUiState>
        field = MutableStateFlow(SpeedTestScreenUiState())

    val uiEvent: SharedFlow<SpeedTestScreenUiEvent>
        field = MutableSharedFlow<SpeedTestScreenUiEvent>()

    init {
        initialize()
    }

    fun initialize() {
        uiState.update {
            it.copy(
                downloadURL = DataStore.speedTestUrl.blankAsNull() ?: SPEED_TEST_URL,
                uploadURL = DataStore.speedTestUploadURL.blankAsNull() ?: SPEED_TEST_UPLOAD_URL,
                timeout = DataStore.speedTestTimeout,
                uploadLength = DataStore.speedTestUploadLength,
            )
        }
    }

    private var job: Job? = null

    fun doSpeedTest() {
        cancel()
        job = viewModelScope.launch(ioDispatcher) {
            uiState.update {
                it.copy(canTest = false)
            }
            val state = uiState.value
            val proxy = currentSocks5()?.string.orEmpty()
            val (mode, url, uploadLength) = when (state.mode) {
                SpeedTestMode.Download -> Triple(
                    ProtoSpeedTestMode.SPEED_TEST_MODE_DOWNLOAD,
                    state.downloadURL,
                    0L,
                )

                SpeedTestMode.Upload -> Triple(
                    ProtoSpeedTestMode.SPEED_TEST_MODE_UPLOAD,
                    state.uploadURL,
                    state.uploadLength,
                )
            }
            try {
                coreClient.speedTest(
                    mode = mode,
                    url = url,
                    timeoutMs = state.timeout,
                    uploadLengthBytes = uploadLength,
                    socksProxyUrl = proxy,
                    userAgent = userAgent,
                ).catch { e ->
                    if (e is CancellationException) throw e
                    Logs.e(e)
                    uiEvent.emit(SpeedTestScreenUiEvent.ErrorAlert(StringOrRes.Direct(e.readableMessage)))
                }.collect { response ->
                    uiState.update { current ->
                        current.copy(
                            speed = response.bytesPerSec,
                            progress = response.progress.takeIf { it >= 0.0 }?.toFloat(),
                        )
                    }
                }
                uiEvent.emit(SpeedTestScreenUiEvent.Snackbar(StringOrRes.Res(Res.string.done)))
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Logs.e(e)
                uiEvent.emit(SpeedTestScreenUiEvent.ErrorAlert(StringOrRes.Direct(e.readableMessage)))
            } finally {
                uiState.update {
                    it.copy(canTest = true, progress = null)
                }
            }
        }
    }

    private fun cancel() {
        job?.cancel()
    }

    override fun onCleared() {
        val state = uiState.value
        DataStore.speedTestUrl = state.downloadURL
        DataStore.speedTestUploadURL = state.uploadURL
        DataStore.speedTestUploadLength = state.uploadLength
        DataStore.speedTestTimeout = state.timeout
        cancel()
        super.onCleared()
    }

    enum class SpeedTestMode {
        Download,
        Upload,
    }

    fun setMode(mode: SpeedTestMode) = viewModelScope.launch {
        uiState.emit(uiState.value.copy(mode = mode))
    }

    fun setServer(server: String?) = viewModelScope.launch {
        uiState.update { state ->
            if (server?.blankAsNull() == null) {
                state.copy(urlError = StringOrRes.Res(Res.string.can_not_be_empty))
            } else when (state.mode) {
                SpeedTestMode.Download -> {
                    state.copy(downloadURL = server)
                }

                SpeedTestMode.Upload -> {
                    state.copy(uploadURL = server)
                }
            }
        }
    }

    fun setTimeout(raw: String?) = viewModelScope.launch {
        uiState.update { state ->
            val timeout = raw?.blankAsNull()?.toIntOrNull()
            if (timeout == null) {
                state.copy(timeoutError = StringOrRes.Res(Res.string.can_not_be_empty))
            } else {
                state.copy(timeout = timeout)
            }
        }
    }

    fun setUploadSize(raw: String?) = viewModelScope.launch {
        uiState.update { state ->
            val size = raw?.blankAsNull()?.toLongOrNull()
            if (size == null) {
                state.copy(uploadLengthError = StringOrRes.Res(Res.string.can_not_be_empty))
            } else {
                state.copy(uploadLength = size)
            }
        }
    }
}
