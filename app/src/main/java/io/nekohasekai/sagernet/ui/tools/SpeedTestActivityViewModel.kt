package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.USER_AGENT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import libcore.CopyCallback
import libcore.HTTPResponse
import libcore.Libcore

internal sealed class SpeedTestActivityUiState {
    object Idle : SpeedTestActivityUiState()
    class Doing(val result: Long = 0L) : SpeedTestActivityUiState()
    class Done(val exception: Exception? = null) : SpeedTestActivityUiState()
}

internal class SpeedTestActivityViewModel : ViewModel() {
    private val _uiState: MutableLiveData<SpeedTestActivityUiState> =
        MutableLiveData(SpeedTestActivityUiState.Idle)
    val uiState: LiveData<SpeedTestActivityUiState> = _uiState

    private var job: Job? = null
    private var currentResponse: HTTPResponse? = null

    fun doTest(url: String, timeout: Int) {
        cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            doTest0(url, timeout)
        }
    }

    private fun cancel() {
        currentResponse?.closeQuietly()
        job?.cancel()
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private suspend fun doTest0(url: String, timeout: Int) {
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
                .writeTo(Libcore.DevNull, object : CopyCallback {
                    val start = System.nanoTime()

                    private var saved: Long = 0L

                    override fun setLength(length: Long) {}

                    override fun update(n: Long) {
                        saved += n

                        val duration = (System.nanoTime() - start) / 1_000_000_000.0
                        val speed = (saved.toDouble() / duration).toLong()

                        _uiState.postValue(SpeedTestActivityUiState.Doing(speed))
                    }

                })

            _uiState.postValue(SpeedTestActivityUiState.Done(null))
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            _uiState.postValue(SpeedTestActivityUiState.Done(e))
        } finally {
            currentResponse?.closeQuietly()
        }
    }

    private fun HTTPResponse.closeQuietly() = runCatching {
        close()
    }

}