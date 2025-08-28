package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SPEED_TEST_UPLOAD_URL
import io.nekohasekai.sagernet.SPEED_TEST_URL
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.CopyCallback
import libcore.HTTPResponse
import libcore.Libcore

internal data class SpeedTestActivityUiState(
    val speed: Long = 0L,
    val canTest: Boolean = true,
    val mode: SpeedTestActivityViewModel.SpeedTestMode = SpeedTestActivityViewModel.SpeedTestMode.Download,
    val downloadURL: String = DataStore.speedTestUrl.blankAsNull() ?: SPEED_TEST_URL,
    val uploadURL: String = DataStore.speedTestUploadURL.blankAsNull() ?: SPEED_TEST_UPLOAD_URL,
    val timeout: Int = DataStore.speedTestTimeout,
    val uploadLength: Long = DataStore.speedTestUploadLength,
)

internal sealed interface SpeedTestActivityUiEvent {
    class Snackbar(val message: StringOrRes) : SpeedTestActivityUiEvent
    class ErrorAlert(val message: StringOrRes) : SpeedTestActivityUiEvent
}

internal class SpeedTestActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SpeedTestActivityUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<SpeedTestActivityUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private var job: Job? = null
    private var currentResponse: HTTPResponse? = null

    fun doSpeedTest() {
        cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(canTest = false)
            }
            val state = _uiState.value
            when (state.mode) {
                SpeedTestMode.Download -> downloadTest(
                    state.downloadURL,
                    state.timeout,
                )

                SpeedTestMode.Upload -> uploadTest(
                    state.uploadURL,
                    state.timeout,
                    state.uploadLength,
                )
            }
            _uiEvent.emit(SpeedTestActivityUiEvent.Snackbar(StringOrRes.Res(R.string.done)))
            _uiState.update {
                it.copy(canTest = true)
            }
        }
    }

    private fun cancel() {
        currentResponse?.closeQuietly()
        job?.cancel()
    }

    override fun onCleared() {
        val state = _uiState.value
        DataStore.speedTestUrl = state.downloadURL
        DataStore.speedTestUploadURL = state.uploadURL
        DataStore.speedTestUploadLength = state.uploadLength
        DataStore.speedTestTimeout = state.timeout
        cancel()
        super.onCleared()
    }

    private suspend fun downloadTest(url: String, timeout: Int) {
        try {
            Libcore.newHttpClient()
                .apply {
                    if (DataStore.serviceState.started) {
                        useSocks5(
                            DataStore.mixedPort,
                            DataStore.inboundUsername,
                            DataStore.inboundPassword,
                        )
                    }
                }
                .newRequest()
                .apply {
                    setURL(url)
                    setUserAgent(USER_AGENT)
                    setTimeout(timeout)
                }
                .execute()
                .also {
                    currentResponse = it
                }
                .writeTo(Libcore.DevNull, SpeedTestCopyCallback { speed ->
                    viewModelScope.launch {
                        _uiState.update { state ->
                            state.copy(speed = speed)
                        }
                    }
                })
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(SpeedTestActivityUiEvent.ErrorAlert(StringOrRes.Direct(e.readableMessage)))
        } finally {
            currentResponse?.closeQuietly()
        }
    }

    private suspend fun uploadTest(url: String, timeout: Int, length: Long) {
        try {
            Libcore.newHttpClient()
                .apply {
                    if (DataStore.serviceState.started) {
                        useSocks5(
                            DataStore.mixedPort,
                            DataStore.inboundUsername,
                            DataStore.inboundPassword,
                        )
                    }
                }
                .newRequest()
                .apply {
                    setURL(url)
                    setUserAgent(USER_AGENT)
                    setTimeout(timeout)
                    setContentZero(length, SpeedTestCopyCallback { speed ->
                        viewModelScope.launch {
                            _uiState.update { state ->
                                state.copy(speed = speed)
                            }
                        }
                    })
                }
                .execute()
                .also {
                    currentResponse = it
                }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(SpeedTestActivityUiEvent.ErrorAlert(StringOrRes.Direct(e.readableMessage)))
        } finally {
            currentResponse?.closeQuietly()
        }
    }

    private fun HTTPResponse.closeQuietly() = runCatching {
        close()
    }

    enum class SpeedTestMode {
        Download,
        Upload,
    }

    fun setMode(mode: SpeedTestMode) = viewModelScope.launch {
        _uiState.emit(_uiState.value.copy(mode = mode))
    }

    fun setServer(server: String) = viewModelScope.launch {
        _uiState.update { state ->
            when (state.mode) {
                SpeedTestMode.Download -> {
                    state.copy(downloadURL = server)
                }

                SpeedTestMode.Upload -> {
                    state.copy(uploadURL = server)
                }
            }
        }
    }

    fun setTimeout(timeout: Int) = viewModelScope.launch {
        _uiState.update { state ->
            state.copy(timeout = timeout)
        }
    }

    fun setUploadSize(size: Long) = viewModelScope.launch {
        _uiState.update { state ->
            state.copy(uploadLength = size)
        }
    }

    private class SpeedTestCopyCallback(val updateSpeed: (Long) -> Unit) : CopyCallback {

        val start = System.nanoTime()

        private var saved: Long = 0L

        override fun setLength(length: Long) {}

        override fun update(n: Long) {
            saved += n
            val duration = (System.nanoTime() - start) / 1_000_000_000.0
            val speed = (saved.toDouble() / duration).toLong()
            updateSpeed(speed)
        }

    }
}