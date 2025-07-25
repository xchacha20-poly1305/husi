package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

internal sealed interface GetCertUiState {
    object Idle : GetCertUiState
    object Doing : GetCertUiState
    class Done(val cert: String) : GetCertUiState
    class Failure(val exception: Exception) : GetCertUiState
}

internal class GetCertActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<GetCertUiState>(GetCertUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun getCert(server: String, serverName: String, protocol: String, proxy: String) {
        viewModelScope.launch(Dispatchers.IO) {
            getCert0(server, serverName, protocol.lowercase(), proxy)
        }
    }

    private suspend fun getCert0(server: String, serverName: String, protocol: String, proxy: String) {
        _uiState.update { GetCertUiState.Doing }
        try {
            val cert = Libcore.getCert(server, serverName, protocol, proxy)
            _uiState.update { GetCertUiState.Done(cert) }
        } catch (e: Exception) {
            _uiState.update { GetCertUiState.Failure(e) }
        }
    }
}