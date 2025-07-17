package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libcore.Libcore

internal sealed class GetCertUiState {
    object Idle : GetCertUiState()
    object Doing : GetCertUiState()
    class Done(val cert: String) : GetCertUiState()
    class Failure(val exception: Exception) : GetCertUiState()
}

internal class GetCertActivityViewModel : ViewModel() {
    private val _uiState: MutableLiveData<GetCertUiState> = MutableLiveData(GetCertUiState.Idle)
    val uiState: LiveData<GetCertUiState> = _uiState

    fun getCert(server: String, serverName: String, protocol: Int, proxy: String) {
        viewModelScope.launch(Dispatchers.IO) {
            getCert0(server, serverName, protocol, proxy)
        }
    }

    private suspend fun getCert0(server: String, serverName: String, protocol: Int, proxy: String) {
        _uiState.postValue(GetCertUiState.Doing)
        try {
            val cert = Libcore.getCert(server, serverName, protocol, proxy)
            _uiState.postValue(GetCertUiState.Done(cert))
        } catch (e: Exception) {
            _uiState.postValue(GetCertUiState.Failure(e))
        }
    }
}