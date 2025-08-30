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
    val progress: Int? = null,
    val speed: Long = 0L,
    val canTest: Boolean = true,
    val mode: SpeedTestActivityViewModel.SpeedTestMode = SpeedTestActivityViewModel.SpeedTestMode.Download,
    val downloadURL: String = DataStore.speedTestUrl.blankAsNull() ?: SPEED_TEST_URL,
    val uploadURL: String = DataStore.speedTestUploadURL.blankAsNull() ?: SPEED_TEST_UPLOAD_URL,
    val urlError: StringOrRes? = null,
    val timeout: Int = DataStore.speedTestTimeout,
    val timeoutError: StringOrRes? = null,
    val uploadLength: Long = DataStore.speedTestUploadLength,
    val uploadLengthError: StringOrRes? = null,
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
                it.copy(canTest = true, progress = null)
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
                .writeTo(Libcore.DevNull, SpeedTestCopyCallback { speed, progress ->
                    _uiState.update { state ->
                        state.copy(speed = speed, progress = progress)
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
                    setContentZero(length, SpeedTestCopyCallback { speed, progress ->
                        _uiState.update { state ->
                            state.copy(speed = speed, progress = progress)
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

    fun setServer(server: String?) = viewModelScope.launch {
        _uiState.update { state ->
            if (server?.blankAsNull() == null) {
                state.copy(urlError = StringOrRes.Res(R.string.can_not_be_empty))
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
        _uiState.update { state ->
            val timeout = raw?.blankAsNull()?.toIntOrNull()
            if (timeout == null) {
                state.copy(timeoutError = StringOrRes.Res(R.string.can_not_be_empty))
            } else {
                state.copy(timeout = timeout)
            }
        }
    }

    fun setUploadSize(raw: String?) = viewModelScope.launch {
        _uiState.update { state ->
            val size = raw?.blankAsNull()?.toLongOrNull()
            if (size == null) {
                state.copy(uploadLengthError = StringOrRes.Res(R.string.can_not_be_empty))
            } else {
                state.copy(uploadLength = size)
            }
        }
    }

    private class SpeedTestCopyCallback(val onFrameUpdate: (speed: Long, progress: Int?) -> Unit) :
        CopyCallback {

        private val start = System.nanoTime()
        private var total: Long? = null
        private var saved: Long = 0L

        override fun setLength(length: Long) {
            total = length
        }

        override fun update(n: Long) {
            saved += n
            val savedDouble = saved.toDouble()
            val duration = (System.nanoTime() - start) / 1_000_000_000.0
            val speed = (savedDouble / duration).toLong()
            val progress = total?.let {
                (savedDouble / it * 100).toInt()
            }
            onFrameUpdate(speed, progress)
        }

    }
}